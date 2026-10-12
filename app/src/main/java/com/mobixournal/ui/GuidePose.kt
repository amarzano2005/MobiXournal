package com.mobixournal.ui

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.render.DrawingGuide
import com.mobixournal.render.GuideKind

/**
 * A remembered **drawing-guide pose**: the live guide plus the page index it was posed on, so an
 * instrument comes back where the user left it after a restart instead of being dropped fresh at the
 * middle of the viewport.
 *
 * Only the three **instruments** have a pose worth keeping — see [isRememberable]: the ruling snap and
 * the chemistry lattice are invisible rules whose anchor is re-derived from the page each time, and
 * their projection (which is all that matters about them) does not depend on where the anchor sits.
 * [GuidePoseCodec] writes nothing for those, so a stored pose is always an instrument's.
 */
data class GuidePose(val page: Int, val guide: DrawingGuide)

/** The [GuideKind] a live [DrawingGuide] is — the key a remembered pose is matched against. */
val DrawingGuide.guideKind: GuideKind
    get() = when (this) {
        is DrawingGuide.Setsquare -> GuideKind.SETSQUARE
        is DrawingGuide.Compass -> GuideKind.COMPASS
        is DrawingGuide.Protractor -> GuideKind.PROTRACTOR
        is DrawingGuide.Ruling -> GuideKind.RULING
        is DrawingGuide.Chemistry -> GuideKind.CHEMISTRY
    }

/**
 * True for the guide kinds a finger **poses**: the setsquare, the compass and the protractor. Losing
 * their pose is losing a real thing (the angle the triangle was aimed at, the radius the compass was
 * opened to), which is exactly why these are the ones remembered.
 */
val DrawingGuide.isRememberable: Boolean
    get() = this is DrawingGuide.Setsquare ||
        this is DrawingGuide.Compass ||
        this is DrawingGuide.Protractor

/** [guide]'s own x anchor, for the codec. */
private val DrawingGuide.anchorX: Double get() = x

/** The rotation the codec stores for [guide], or 0 for a kind that has none. */
private val DrawingGuide.poseAngle: Double
    get() = when (this) {
        is DrawingGuide.Setsquare -> angle
        is DrawingGuide.Protractor -> angle
        else -> 0.0
    }

/** The long-leg length the codec stores for [guide], or 0 for a kind that has none. */
private val DrawingGuide.poseSize: Double
    get() = if (this is DrawingGuide.Setsquare) size else 0.0

/** The radius the codec stores for [guide], or 0 for a kind that has none. */
private val DrawingGuide.poseRadius: Double
    get() = when (this) {
        is DrawingGuide.Compass -> radius
        is DrawingGuide.Protractor -> radius
        else -> 0.0
    }

/**
 * JSON codec for [GuidePose] — **one** encoding for both places the pose is kept (the preference store
 * and the settings backup), so the two can't drift.
 *
 * The shape is flat (`kind`, `page`, `x`, `y`, `angle`, `size`, `radius`) rather than one object per
 * guide type: a pose is a handful of numbers, and a single reader keeps the per-kind meaning of
 * `size` / `radius` in one place. Anything unreadable (a missing or unknown kind, a non-numeric
 * anchor) reads back as **no pose**, which places the instrument fresh rather than refusing to start.
 */
internal object GuidePoseCodec {

    /** [pose] as a JSON object literal, or `null`. */
    fun toJson(pose: GuidePose?): String {
        val guide = pose?.guide ?: return "null"
        if (!guide.isRememberable) return "null"
        return buildString {
            append("{\"kind\": \"").append(guide.guideKind.name).append("\"")
            append(", \"page\": ").append(pose.page)
            append(", \"x\": ").append(num(guide.anchorX))
            append(", \"y\": ").append(num(guide.y))
            append(", \"angle\": ").append(num(guide.poseAngle))
            append(", \"size\": ").append(num(guide.poseSize))
            append(", \"radius\": ").append(num(guide.poseRadius))
            append("}")
        }
    }

    /** The pose [text] describes, or null when it does not describe a rememberable one. */
    fun fromJson(text: String?): GuidePose? {
        if (text.isNullOrBlank()) return null
        val obj = JsonParser.parse(text) as? JsonNode.Obj ?: return null
        return fromNode(obj)
    }

    /** The pose a parsed backup's `guidePose` object describes, or null. */
    fun fromNode(obj: JsonNode.Obj?): GuidePose? {
        val node = obj ?: return null
        val kindName = node.getStrOrNull("kind") ?: return null
        val kind = GuideKind.entries.firstOrNull { it.name.equals(kindName, ignoreCase = true) }
            ?: return null
        val x = node.getDouble("x", Double.NaN)
        val y = node.getDouble("y", Double.NaN)
        if (x.isNaN() || y.isNaN()) return null
        val angle = node.getDouble("angle", 0.0)
        val min = DrawingGuide.MIN_SIZE_PT
        val guide = when (kind) {
            GuideKind.SETSQUARE -> DrawingGuide.Setsquare(
                x = x,
                y = y,
                size = node.getDouble("size", DrawingGuide.DEFAULT_SIZE_PT).coerceAtLeast(min),
                angle = angle,
            )
            GuideKind.COMPASS -> DrawingGuide.Compass(
                x = x,
                y = y,
                radius = node.getDouble("radius", DrawingGuide.DEFAULT_RADIUS_PT).coerceAtLeast(min),
            )
            GuideKind.PROTRACTOR -> DrawingGuide.Protractor(
                x = x,
                y = y,
                radius = node.getDouble("radius", DrawingGuide.DEFAULT_RADIUS_PT).coerceAtLeast(min),
                angle = angle,
            )
            // NONE and the invisible rules have no pose to remember, however they were written.
            else -> return null
        }
        return GuidePose(node.getInt("page", 0).coerceAtLeast(0), guide)
    }

    /** A number as a JSON literal that the parser reads back exactly (its `e` forms included). */
    private fun num(v: Double): String =
        if (v.isFinite()) v.toString() else "0.0"
}
