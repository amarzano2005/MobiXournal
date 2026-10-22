package com.mobixournal.render

import android.view.MotionEvent

/**
 * The Android half of `give-both-shells-one-pointer-router`: the **platform vocabulary** of one
 * pointer, read off a `MotionEvent`.
 *
 * The Android surface stays an adapter — this file is where a `MotionEvent`'s tool type becomes the
 * shared [PointerKind], and nothing here decides anything: the decisions it feeds live in
 * `mobix-core` ([InputClassifier] for what a contact begins, [PressureCurve] for how hard it draws,
 * [PageEraser]/[StrokeEraser] for what a partial erase cuts, [InkPointerRouter] for what a stream of
 * samples means).
 *
 * Reading by **index** is deliberate: palm rejection depends on never reading pointer 0 when the pen
 * is pointer 1 (`docs/stylus.md` → *Palm rejection*).
 */
internal fun MotionEvent.inkPointerKind(pointerIndex: Int): PointerKind =
    when (getToolType(pointerIndex)) {
        MotionEvent.TOOL_TYPE_STYLUS -> PointerKind.STYLUS
        MotionEvent.TOOL_TYPE_ERASER -> PointerKind.ERASER_TIP
        MotionEvent.TOOL_TYPE_FINGER -> PointerKind.FINGER
        else -> PointerKind.UNKNOWN
    }
