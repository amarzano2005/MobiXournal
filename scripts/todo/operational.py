"""Conservative checks for executable instructions in active tasks.

Historical archive entries are deliberately outside this module's call site.
These checks flag known contradictions, not arbitrary feature prose.
"""

import re


_OBSOLETE_PACKAGE = re.compile(r"\bcom\.mobixournal\.v2\b")
_HOST_ABSENCE = re.compile(
    r"\bthis (?:host|machine) has no (?:adb|avd)\b"
    r"|\bno adb (?:and|or) (?:no )?avd on this (?:host|machine)\b",
    re.IGNORECASE,
)
_HISTORICAL_PREFIX = re.compile(
    r"\b(?:historical observation|historical note|previous host|previous machine)\b"
    r"|\bon \d{4}-\d{2}-\d{2}\b",
    re.IGNORECASE,
)
_DEVICE_LOCATION = re.compile(
    r"\b(?:on|using)\s+(?:(?:a|an|the|baseline|real|physical)\s+)*(?:emulator|device)\b",
    re.IGNORECASE,
)
_DEVICE_PASS = re.compile(
    r"^\s*(?:run|verify|exercise|check)\s+(?:(?:a|an|the)\s+)?"
    r"(?:emulator|device)\s+(?:pass|verification|session|smoke)\b",
    re.IGNORECASE,
)
_RUN = re.compile(
    r"\b(?:run|verify|test|exercise|install|review|type|compute|measure|check)\b",
    re.IGNORECASE,
)
_INSTALL_APK = re.compile(r"\binstall the (?:debug )?apk\b", re.IGNORECASE)
_DEVICE_EVIDENCE = re.compile(r"\b(?:screenshots?|logcat|emulator|avd)\b", re.IGNORECASE)


def problems(task):
    """Return actionable errors for an active task; flag defaults match the CLI."""
    title = task.get("title", "")
    description = task.get("description", "")
    text = title + "\n" + description
    out = []
    # Examine each sentence independently: a dated historical note must not exempt
    # a later present-tense claim about whatever machine picks up the task.
    obsolete_package = False
    host_absence = False
    for sentence in re.split(r"(?<=[.!?])\s+|\n", text):
        for match in _OBSOLETE_PACKAGE.finditer(sentence):
            if not _HISTORICAL_PREFIX.search(sentence[:match.start()]):
                obsolete_package = True
        for match in _HOST_ABSENCE.finditer(sentence):
            if not _HISTORICAL_PREFIX.search(sentence[:match.start()]):
                host_absence = True
    if obsolete_package:
        out.append("obsolete package com.mobixournal.v2: use com.mobixournal for current commands")
    if host_absence:
        out.append(
            "current-host absence claim: describe the unverified behaviour, or label a dated "
            "historical observation; detect the current host using AGENTS.md"
        )
    apk_install_desc = False
    device_evidence_desc = False
    for sentence in re.split(r"(?<=[.!?])\s+|\n", description):
        for match in _INSTALL_APK.finditer(sentence):
            if not _HISTORICAL_PREFIX.search(sentence[:match.start()]):
                apk_install_desc = True
        for match in _DEVICE_EVIDENCE.finditer(sentence):
            if not _HISTORICAL_PREFIX.search(sentence[:match.start()]):
                device_evidence_desc = True
    # A subject named "emulator" is not evidence of a runtime requirement:
    # configuration/parser tests can run on the JVM. Require a location or an
    # explicit device pass, rather than inferring execution from two keywords.
    device_run = (_DEVICE_LOCATION.search(title) and _RUN.search(title)) or _DEVICE_PASS.search(title)
    if (task.get("level", "task") == "task"
            and not task.get("emulator_debug", False)
            and (
                (task.get("category") == "test" and device_run)
                or (apk_install_desc and device_evidence_desc)
            )):
        out.append(
            "device/emulator verification is required by the task but emulator_debug is false "
            "(also its default): set --emulator-debug, or correct the task's verification goal"
        )
    return out
