package com.mobixournal.design

/**
 * The shells' **version numbers**, in one place — the fact each About page reads about the other.
 *
 * The tablet's own number is its `BuildConfig.VERSION_NAME`, which the build owns; the window's is the one
 * `compose.desktop`'s `packageVersion` carries, and it has to be readable from `mobix-core` as well, because
 * the tablet's About page names the desktop build it speaks to (`docs/mobixtool-versions` — see
 * `docs/about.md`). Two literals would drift the first time one shell shipped without the other.
 */
object BuildVersions {

    /** The packaged desktop window's version — the one `compose.desktop`'s `packageVersion` carries. */
    const val DESKTOP = "1.1.0"
}
