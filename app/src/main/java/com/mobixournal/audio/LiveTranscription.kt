package com.mobixournal.audio

import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Turns a recording stream into 16 kHz mono as it arrives, one write at a time.
 *
 * The file path can afford to hold a recording in memory ([PcmResampler16k]) because it runs once,
 * after the fact; this one runs **while the microphone is open**, so it keeps nothing but a small
 * output buffer that it hands to [sink] as soon as it fills. Same mapping as the file path — one
 * output frame per `sourceRate / 16000` input frames — so a span's timestamps stay the recording's.
 */
internal class LiveResampler16k(
    private val sourceRate: Int,
    private val channels: Int,
    private val sink: (ByteArray, Int) -> Unit,
) {
    private val out = ByteArray(OUT_BYTES)
    private var fill = 0
    /**
     * Phase accumulator, in units of the target rate: one output frame is taken every
     * `sourceRate / targetRate` input frames. Seeded with one target period so **the first input
     * frame is the first output frame**, the same mapping the file path's [PcmResampler16k] uses.
     */
    private var acc = PcmResampler16k.TARGET_RATE

    init {
        require(sourceRate > 0) { "a sample rate is needed to resample from" }
        require(channels > 0) { "a channel count is needed to read frames" }
    }

    /** Consume [count] bytes of interleaved 16-bit PCM; [sink] sees the result, and owns nothing. */
    fun write(pcm: ByteArray, offset: Int, count: Int) {
        val frameBytes = channels * BYTES_PER_SAMPLE
        val end = offset + count - count % frameBytes
        var at = offset
        while (at < end) {
            acc += PcmResampler16k.TARGET_RATE
            if (acc >= sourceRate) {
                acc -= sourceRate
                emit(mix(pcm, at, frameBytes))
            }
            at += frameBytes
        }
    }

    /** Hand the last partial buffer over (the capture has stopped). */
    fun flush() {
        if (fill <= 0) return
        sink(out, fill)
        fill = 0
    }

    private fun emit(sample: Int) {
        if (fill + BYTES_PER_SAMPLE > out.size) {
            sink(out, fill)
            fill = 0
        }
        out[fill++] = (sample and 0xFF).toByte()
        out[fill++] = ((sample shr 8) and 0xFF).toByte()
    }

    private fun mix(pcm: ByteArray, offset: Int, frameBytes: Int): Int {
        if (channels == 1) return sampleAt(pcm, offset)
        var sum = 0
        var channel = 0
        while (channel < channels) {
            sum += sampleAt(pcm, offset + channel * BYTES_PER_SAMPLE)
            channel++
        }
        return sum / channels
    }

    private fun sampleAt(pcm: ByteArray, at: Int): Int =
        (pcm[at].toInt() and 0xFF) or (pcm[at + 1].toInt() shl 8)

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val OUT_BYTES = 4 * 1024
    }
}

/**
 * Cuts the live 16 kHz stream into **spans**: the audio a live transcription job can chew on without
 * waiting for the recording to end.
 *
 * A span closes at a silence — the same silence the file path's VAD uses to end a phrase — once it is
 * at least [minSpanMs] long, or at [maxSpanMs] if someone talks for half a minute without pausing. It
 * never closes in the middle of speech unless the maximum forces it, so a word is not cut in two;
 * whatever is cut is handed over as an [AudioChunk] whose bytes and milliseconds still line up with
 * the recording, and the spans together cover the stream with no gap and no overlap.
 *
 * Pure and dependency-free (the RMS is the file path's own [AudioVadSegmenter] rule), so the cutting
 * is unit-tested on the JVM while the recogniser it feeds is not.
 */
internal class LiveSpanCutter(
    private val minSpanMs: Int = MIN_SPAN_MS,
    private val maxSpanMs: Int = TranscriptionWindows.WINDOW_MS,
    private val silenceMs: Int = SILENCE_MS,
    private val rmsThreshold: Double = RMS_THRESHOLD,
    private val frameMs: Int = FRAME_MS,
) {
    private val frameBytes = frameMs * BYTES_PER_MS
    private var buffer = ByteArray(INITIAL_BYTES)
    private var size = 0
    /** Frame-aligned bytes of [buffer] already turned into a speech/silence decision. */
    private var scanned = 0
    /** Absolute ms of `buffer[0]`. */
    private var spanStartMs = 0
    private var silenceAcc = 0
    private var speechSeen = false
    /** Absolute ms of the end of the last silence run, for an early cut at [maxSpanMs]. */
    private var lastSilenceEndMs = -1

    /** Append [count] bytes of 16 kHz mono and hand every span it closed to [emit]. */
    fun feed(pcm: ByteArray, count: Int, emit: (AudioChunk) -> Unit) {
        if (count <= 0) return
        append(pcm, count)
        while (size - scanned >= frameBytes) {
            val speech = frameRms(scanned) >= rmsThreshold
            scanned += frameBytes
            if (speech) {
                speechSeen = true
                silenceAcc = 0
                lastSilenceEndMs = -1
            } else {
                silenceAcc += frameMs
                lastSilenceEndMs = spanStartMs + scanned / BYTES_PER_MS
            }
            val spanMs = scanned / BYTES_PER_MS
            when {
                !speechSeen && spanMs >= LEADING_SILENCE_MS -> {
                    // Nothing said yet: slide the span start along instead of carrying the silence.
                    cut(scanned, emit, cutSpan = false)
                }
                speechSeen && silenceAcc >= silenceMs && spanMs >= minSpanMs -> {
                    cut(scanned, emit, cutSpan = true)
                }
                spanMs >= maxSpanMs -> {
                    // Somebody talked for half a minute: close at the last silence if there was one,
                    // otherwise on the maximum itself — never past it, since the model's window is
                    // what the next stage pads to anyway.
                    val wanted = lastSilenceEndMs
                    val cutBytes = if (wanted in (spanStartMs + minSpanMs)..(spanStartMs + spanMs)) {
                        (wanted - spanStartMs) * BYTES_PER_MS
                    } else {
                        maxSpanMs * BYTES_PER_MS
                    }
                    cut(cutBytes, emit, cutSpan = true)
                }
            }
        }
    }

    /** End the recording: hand over what is buffered when it holds speech, drop a silent tail. */
    fun flush(emit: (AudioChunk) -> Unit) {
        if (speechSeen && size >= MIN_TAIL_BYTES) cut(size, emit, cutSpan = true)
        else size = 0
        scanned = 0
        speechSeen = false
        silenceAcc = 0
        lastSilenceEndMs = -1
        spanStartMs += size / BYTES_PER_MS
    }

    private fun cut(bytes: Int, emit: (AudioChunk) -> Unit, cutSpan: Boolean) {
        val cut = bytes.coerceIn(0, size)
        if (cutSpan && cut > 0) {
            val pcm = buffer.copyOfRange(0, cut)
            emit(AudioChunk(spanStartMs, spanStartMs + cut / BYTES_PER_MS, pcm))
        }
        spanStartMs += cut / BYTES_PER_MS
        if (cut >= size) {
            size = 0
            scanned = 0
            speechSeen = false
            silenceAcc = 0
            lastSilenceEndMs = -1
            return
        }
        // Keep the tail (silence after a hard cut, at most a couple of seconds) and rescan it: its
        // decisions belonged to the span that just closed.
        buffer.copyInto(buffer, 0, cut, size)
        size -= cut
        scanned = 0
        speechSeen = false
        silenceAcc = 0
        lastSilenceEndMs = -1
    }

    private fun append(pcm: ByteArray, count: Int) {
        if (size + count > buffer.size) {
            var wanted = buffer.size
            while (wanted < size + count) wanted *= 2
            buffer = buffer.copyOf(wanted)
        }
        pcm.copyInto(buffer, size, 0, count)
        size += count
    }

    private fun frameRms(offset: Int): Double {
        var sum = 0.0
        var at = offset
        val end = offset + frameBytes
        while (at < end) {
            val sample = ((buffer[at].toInt() and 0xFF) or (buffer[at + 1].toInt() shl 8)).toShort().toDouble()
            sum += sample * sample
            at += 2
        }
        return kotlin.math.sqrt(sum / (frameBytes / 2))
    }

    private companion object {
        const val BYTES_PER_MS = 32
        const val FRAME_MS = 30
        const val MIN_SPAN_MS = 6_000
        const val SILENCE_MS = 500
        const val RMS_THRESHOLD = 450.0
        /** Silence at the start of a recording is dropped rather than kept as a span of its own. */
        const val LEADING_SILENCE_MS = 1_000
        const val MIN_TAIL_BYTES = 300 * BYTES_PER_MS
        const val INITIAL_BYTES = 64 * 1024
    }
}

/**
 * Transcribes a recording **while it is being made**, so the wait at Stop is the tail rather than the
 * whole lecture.
 *
 * The recorder hands its PCM to [feed] on the capture thread; this resamples it to 16 kHz
 * ([LiveResampler16k]), cuts it into spans at silences ([LiveSpanCutter]) and queues them for one
 * worker thread, which runs the same pipeline the file path runs — the VAD's phrases, grouped into
 * [TranscriptionWindows] — keeping the segments and writing them to the sidecar's transcript file
 * after every span. Nothing is ever redone: the file path resumes from the last segment it finds on
 * disk (`AudioTranscriptionService`), so a session that keeps up leaves only its final seconds.
 *
 * Deliberately bounded and disposable. The queue holds a few spans and then the whole live job stops;
 * inference that cannot keep up with the microphone must never steal the machine from the app that is
 * being written in, and whatever it did not reach is exactly what the post-stop path already does.
 *
 * Background operation needs nothing from this class: it runs in the app's process, which the
 * recording foreground service keeps alive and awake, so a screen that is off is no different.
 */
internal class LiveTranscription(
    private val fileName: String,
    private val dir: File,
    private val language: String,
    private val modelDir: File,
    private val isTiny: Boolean,
) {
    private val lock = Any()
    private val segments = mutableListOf<TranscriptionSegment>()
    private val spans = LinkedBlockingQueue<AudioChunk>(MAX_QUEUED_SPANS)
    private val cutter = LiveSpanCutter()
    private var coveredMs = 0
    private var worker: Thread? = null
    private var recognizer: WhisperOfflineTranscriber? = null
    private var resampler: LiveResampler16k? = null
    @Volatile private var stopped = false
    /** Set when a span could not be queued: the job is behind and the file path takes the rest. */
    @Volatile private var gaveUp = false

    /** The transcript file the live job writes and the post-stop job appends to. */
    val transcriptFile: File get() = File(dir, "audio/$fileName.transcript.json")

    /** Start the worker if the model is installed; false leaves this object inert. */
    fun start(): Boolean {
        if (!modelInstalled()) return false
        val thread = Thread({ work() }, "live-transcription")
        thread.isDaemon = true
        worker = thread
        thread.start()
        return true
    }

    /** Feed one capture chunk — called on the recorder's thread, so it must stay cheap. */
    fun feed(pcm: ByteArray, count: Int, sampleRate: Int) {
        if (stopped || gaveUp) return
        val resample = resampler
            ?: LiveResampler16k(sampleRate, channels = 1) { bytes, n ->
                cutter.feed(bytes, n) { span -> enqueue(span) }
            }.also { resampler = it }
        resample.write(pcm, 0, count)
    }

    /**
     * Stop feeding and take what the worker finished. Whatever it had not reached is deliberately
     * dropped — the post-stop transcription resumes from [covered] and does that part then, once,
     * rather than the live job sprinting after the microphone.
     */
    fun stop(): AudioTranscription? {
        if (stopped) return transcript()
        stopped = true
        spans.clear()
        worker?.interrupt()
        worker?.join(WORKER_JOIN_MS)
        // A worker that did not finish its window is still inside the recogniser: closing a native
        // recogniser under a running decode is worse than letting the last one go, so that case is
        // left to the worker's own exit (work() closes it in its finally).
        if (worker?.isAlive != true) closeRecognizer()
        writeTranscript()
        return transcript()
    }

    /** Milliseconds of the recording that are transcribed for good — where the file path resumes. */
    fun covered(): Int = synchronized(lock) { coveredMs }

    private fun work() {
        val open = openRecognizer() ?: return
        try {
            while (!stopped) {
                val span = try {
                    spans.poll(POLL_MS, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    break
                } ?: continue
                process(open, span)
            }
        } finally {
            closeRecognizer()
        }
    }

    /** Free the native recogniser once, whether the worker exited or [stop] beat it to it. */
    private fun closeRecognizer() {
        val open = synchronized(lock) { recognizer.also { recognizer = null } } ?: return
        open.close()
    }

    private fun openRecognizer(): WhisperOfflineTranscriber? {
        val transcriber = WhisperOfflineTranscriber(modelDir, isTiny)
        if (!transcriber.init(language = language)) {
            transcriber.close()
            gaveUp = true
            return null
        }
        recognizer = transcriber
        return transcriber
    }

    private fun process(open: WhisperOfflineTranscriber, span: AudioChunk) {
        val chunks = AudioVadSegmenter.segment(span.pcm).map {
            AudioChunk(it.startMs + span.startMs, it.endMs + span.startMs, it.pcm)
        }
        val produced = mutableListOf<TranscriptionSegment>()
        for (window in TranscriptionWindows.of(chunks)) {
            if (stopped) break
            val decoded = open.transcribeDecode(window.pcm(), SAMPLE_RATE) ?: continue
            if (decoded.text.isBlank()) continue
            // Same filing as the post-stop path: real word times when the model reports them.
            val segments = TranscriptionWindows.segmentsFromTokens(window, decoded.tokens, decoded.timestampsSec)
                ?: TranscriptionWindows.distributeText(window, decoded.text)
            produced += segments
        }
        if (stopped) return
        synchronized(lock) {
            segments += produced
            coveredMs = span.endMs
        }
        writeTranscript()
    }

    private fun enqueue(span: AudioChunk) {
        if (stopped || gaveUp) return
        if (!spans.offer(span)) gaveUp = true
    }

    private fun modelInstalled(): Boolean {
        val prefix = if (isTiny) "tiny" else "base"
        return File(modelDir, "$prefix-encoder.int8.onnx").isFile &&
            File(modelDir, "$prefix-decoder.int8.onnx").isFile &&
            File(modelDir, "$prefix-tokens.txt").isFile
    }

    private fun transcript(): AudioTranscription? {
        val done = synchronized(lock) { segments.toList() }
        if (done.isEmpty()) return null
        return AudioTranscription(audioFilename = fileName, segments = done, language = language)
    }

    private fun writeTranscript() {
        val transcript = transcript() ?: return
        transcriptFile.parentFile?.mkdirs()
        runCatching { transcriptFile.writeText(TranscriptionCodec.encode(transcript)) }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_QUEUED_SPANS = 4
        const val POLL_MS = 200L
        const val WORKER_JOIN_MS = 1_500L
    }
}
