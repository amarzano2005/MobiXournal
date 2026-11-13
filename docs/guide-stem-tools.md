# Placing a plot, a table, a circuit or a formula, and editing it afterwards

> Read when you are about to put mathematics on the page and want to know what stays editable, what is
> drawn once, and what this build does not offer.

_A guide page for Android and Windows: the living objects, in the order people reach for them. The internal map is [architecture.md](architecture.md)._

This page is true for Android **2.2.0** and desktop **1.2.0**.

## One model for all of them

Plot, Table and Circuit are **placement** tools, not drawing tools: the press reserves a box instead of
laying ink. Drag two corners to size the box, or tap for the family's default, and the page never ends up
with half an element hanging off it.

What lands is an object, not a picture of one. It is selected as soon as it is placed, and the fields that
created it are the fields that edit it later. Placing is one edit and one undo step; cancelling leaves the
page exactly as it was, because nothing was ever written.

## Functions

The plot tool asks for the formula first. Its dialog carries the function, the domain's two bounds,
critical points, the derivative, a summary, and a shaded integral with its own bounds; the default is
`sin(x)` over −10 to 10, at a little under half the page's width.

The plot stays a live expression. Select it and its named parameters get sliders under the frame —
dragging one re-evaluates the curve, the derivative and the summary while you drag, so the formula is
something you can feel rather than retype.

On Android the dialog has a second tab, **Parametric {x, y}**: it asks for `x(t)` and `y(t)` and places
the pair as **one** curve — sampled over the `t min`/`t max` fields instead of the domain — rather than
as the two waves its two formulas would look like as `y = f(x)`. An `x(t)` with no `y(t)`, or one that
does not parse, keeps the insert button off.

The window's placement card has no such tab, so a graph placed there is `y = f(x)`. A parametric curve
that arrives in a document still opens, draws and exports on both shells, and Android reopens it on the
same tab with its two expressions. Nothing creates a **polar** curve yet on either shell: those are
evaluated, drawn, exported and round-tripped when a document carries them.

## Tables

A placed table is a grid with a header row, and its columns and rows divide the box exactly. Its cells and
header are the element's own fields — the same ones the placement card asked for, reachable again from the
inspector, so a typo is an edit rather than a redraw.

## Circuits and gates

The circuit parts come in three palettes: passive parts, active parts, and logic gates. Picking a part is
the whole question — the next press places it, with the value, rotation and label side left to the
inspector. Parts arrive with the pins the factory gives them, and a wire connects at a pin, so two
components meet where their pins are rather than where you happened to end the drag.

## Chemistry, and what this build does not carry

There is no chemistry palette in this build: those tools were taken out of the rail and survive only as
stored preference names, so an old setting does not resurrect them. What remains from that area is a 60°
lattice snap on the drafting guides, which is genuinely useful for hexagonal structures and is not a bond
tool.

## Formulas

The formula tool takes LaTeX and shows a live typeset preview before you commit, so a formula is checked
before it costs a placement. The dialog also offers a Typst source switch: the source is kept through
save, export and re-import, but this build has no Typst typesetter, so such a block does not appear as
typeset mathematics. [The status page](guide-limitations.md) records exactly where that stands.

## Guides and snapping

Drafting guides snap what you draw: 15° steps on the protractor, the page's ruling, a 60°/120° chemistry
lattice, and grid snapping for the shape tools. A snap moves the endpoint you are drawing to, never the
whole element, and the guides have no grab handle — a finger on the page keeps drawing.

## How it works

- [element-authoring.md](element-authoring.md) — placement, the card, the inspector, vertical space.
- [math-engine.md](math-engine.md) — the expression engine, curve types and the sliders.
- [drawing-tools.md](drawing-tools.md) — figures, guides, snapping and the layers.
- [mobix-elements.md](mobix-elements.md) — the element model and the fields an object carries.
