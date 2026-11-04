package com.mobixournal.design

/**
 * The project's **outward address**, in one place, for both shells.
 *
 * The tablet and the window each print their own version in About and now each offers the same door out:
 * the repository where the source, the releases and the issues live. Two literals would be two chances to
 * spell the address differently, so the string lives here — in the module both shells already link — and
 * the tablet's `AboutLinks` and the window's About row read it.
 *
 * A link is not a version: naming the *other* shell's build number in About answered a question nobody
 * asks at that moment, and a reader who wants the other build wants its releases, which is here.
 */
object ProjectLinks {

    /** The repository this app is developed in — source, releases and issue tracker. */
    const val GITHUB = "https://github.com/amarzano2005/MobiXournal"
}
