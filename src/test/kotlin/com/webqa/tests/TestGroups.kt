package com.webqa.tests

/** TestNG groups: suites select tests by group, so renaming a test does not silently drop it from a suite. */
object TestGroups {
    /** Critical path, run by CI on every push. */
    const val SMOKE = "smoke"

    /** Needs the WireMock container: excluded from suites that run without Docker. */
    const val WIREMOCK = "wiremock"

    /** Changes shared stand data irreversibly (e.g. buys stock): runs only in regression.xml. */
    const val DESTRUCTIVE = "destructive"
}
