# The repository layout: the three modules and their packages

> Read when you need to know **where code lives**: the three Gradle modules, and the
> package map of `app/src/main`, `mobix-core/` and `desktop/`.
>
> _One spoke of the architecture docs; the index is [architecture.md](architecture.md). What the
> project is built on, its prior art and its branding live in [codebase.md](codebase.md)._


## The three modules

| Module | What it is | Its own page |
|---|---|---|
| `app/` | the Android shell: the activity family, the canvas surface, the settings screens | [chrome.md](chrome.md) |
| `mobix-core/` | the platform-free model, the codecs, the paper and ink geometry, the export compilers | [mobix-format.md](mobix-format.md) |
| `desktop/` | the Compose Multiplatform window, a client of the core | [desktop-workstation.md](desktop-workstation.md) |

The Gradle version is the wrapper's own: `gradle/wrapper/gradle-wrapper.properties` names the
distribution, and no page restates it as a separate number — an earlier version of this page did, and the
number had gone stale.

## The packages

Each package is one subject, and the spoke named beside it is where that subject is described. This list is
the package **names** only: a page that promised one line per file was describing a tree that changed under
it, so it no longer tries.

### `app/src/main` — the Android shell

- `app/src/main/java/com/mobixournal`
- `app/src/main/java/com/mobixournal/audio`
- `app/src/main/java/com/mobixournal/io`
- `app/src/main/java/com/mobixournal/panes`
- `app/src/main/java/com/mobixournal/render`
- `app/src/main/java/com/mobixournal/render/markdown`
- `app/src/main/java/com/mobixournal/tabs`
- `app/src/main/java/com/mobixournal/ui`
- `app/src/main/java/com/mobixournal/ui/entry`
- `app/src/main/java/com/mobixournal/ui/theme`
- `app/src/main/java/com/mobixournal/update`

### `mobix-core/src/main` — the platform-free core

- `mobix-core/src/main/java/com/mobixournal/audio`
- `mobix-core/src/main/java/com/mobixournal/collections`
- `mobix-core/src/main/java/com/mobixournal/concurrent`
- `mobix-core/src/main/java/com/mobixournal/design`
- `mobix-core/src/main/java/com/mobixournal/format`
- `mobix-core/src/main/java/com/mobixournal/format/json`
- `mobix-core/src/main/java/com/mobixournal/format/mobix`
- `mobix-core/src/main/java/com/mobixournal/format/model`
- `mobix-core/src/main/java/com/mobixournal/format/xml`
- `mobix-core/src/main/java/com/mobixournal/io`
- `mobix-core/src/main/java/com/mobixournal/portability`
- `mobix-core/src/main/java/com/mobixournal/render`
- `mobix-core/src/main/java/com/mobixournal/update`

### `desktop/src/main` — the window

- `desktop/src/main/java/com/mobixournal/desktop`
- `desktop/src/main/java/com/mobixournal/desktop/ink`
- `desktop/src/main/java/com/mobixournal/desktop/io`
- `desktop/src/main/java/com/mobixournal/desktop/settings`
- `desktop/src/main/java/com/mobixournal/desktop/sheet`
- `desktop/src/main/java/com/mobixournal/desktop/shell`
- `desktop/src/main/java/com/mobixournal/desktop/tools`

## Where the subjects meet

The **`format/` layer is the heart** and is deliberately free of Android dependencies: the model, the
XML/JSON layers and the `.xopp` codecs live in `mobix-core/`, so the round trip is unit-tested on the
JVM and reused by the desktop shell. `render/` and `ui/` are the Android-facing shell around it.
