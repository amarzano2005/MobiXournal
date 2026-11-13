# How the pen behaves, and the gestures that are not on the toolbar

> Read when the ink is not landing the way you expect: the wrong width, a palm that drew, a button that
> did nothing, a circle that snapped on its own.

_A guide page for Android and Windows: the pen, the gestures around it, and the undo they share. The internal map is [architecture.md](architecture.md)._

This page is true for Android **2.2.0** and desktop **1.2.0**.

## Width comes from pressure, not from a slider

The pen's width is the pressure the tablet reports, run through the same filter desktop Xournal++ uses
and scaled by two settings — a minimum pressure and a multiplier. There is no width knob that overrides
the pen; what you can tune is how much of the pressure range becomes width.

A figure is the exception that proves it: laying an arrow down gives it the pen slot's width, and
resizing it afterwards stretches the geometry while every vertex keeps its width. An arrow made twice as
long is twice as long and exactly as thin, and its head is sized by the pen rather than by the shaft.

## The eraser end and the barrel button

Pen hardware outranks the toolbar. A flipped-over eraser tip erases whatever tool is selected. The barrel
button runs one action you choose — erase, select, hand, the **Ring**, or nothing — and holding it in the
middle of a stroke turns the rest of that stroke into an erase, exactly as Xournal++ does.

The detection is deliberately generous, because pens disagree about how to report a button: a pen that
sends a key, a pen the tablet presents as a mouse, and even a pen whose firmware invents its own key code
all reach the same action. A button that does nothing is a bug worth reporting with the pen's name.

## Palm rejection, and where it stops

A pen outranks a hand that landed first: when the pen arrives, the finger's gesture is abandoned rather
than finished, and once the pen owns a gesture a later finger is dropped. Only the owning pointer is
sampled, so a palm resting beside a stroke cannot pull it.

What it does not do is stop a finger from starting a stroke before the pen lands — that ink exists until
the pen takes over and abandons it. The rule is about who wins a gesture, not about hiding contacts.

## The Ring

The Ring is a menu that opens where the pen is, so a mode change does not cost a trip to the toolbar. It
opens from the barrel button when that is what you set the button to, or from its own shortcut key.

## Why your circle snapped

Rest the tip without moving for about a third of a second and the shape recogniser paints the geometry it
believes you drew, in place of the stroke, before you lift — the snap happens where you can see it. Move
more than a few points (the tolerance is in page space, so it reads the same at any zoom) and the timer
re-arms: keep drawing and your own ink comes back the moment the next sample arrives.

Lifting while the preview is showing commits the figure. A closed triangle or rectangle is kept as an
editable shape rather than ink, so its corners can be moved later. Hold-to-snap and the on-lift recogniser
share one switch, and the same pen-only tool filter.

## Moving content, and making room

Drag a selection's handles to resize it; the geometry stretches and the stroke width stays as drawn. To
push a page's content down without moving it by hand, insert vertical space at the point you want the gap
— one edit, one undo step.

## Undo counts gestures, not samples

One gesture is one undo step, however many samples and however many strokes of that gesture it wrote. A
gesture that changed nothing records nothing, so a tap leaves no empty step to step back through. Dragging
a plot's slider is the same deal: the plot re-evaluates live and one edit lands when you let go.

## How it works

- [stylus.md](stylus.md) — the pointer as input, the barrel, and the pen diagnostics.
- [gestures.md](gestures.md) — the priority order, palm rejection, and the gesture transaction.
- [drawing-tools.md](drawing-tools.md) — shapes, hold-to-snap, the eraser and the layers.
- [selection.md](selection.md) — selecting, moving and resizing what a gesture produced.
