package com.numbawang.leimaus.data.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BackgroundUpdateCheckTest {
    private fun available(version: String = "1.0-new") =
        UpdateStatus.Available(version, "https://example.invalid/test.apk", 10, "a".repeat(64), 1)

    @Test fun `daily checks notify once per new version`() = runTest {
        var status = available()
        var last = ""
        val sent = mutableListOf<String>()
        val check = BackgroundUpdateCheck({ status }, { last }, { sent += it.versionName; true }, { last = it })
        check.execute()
        check.execute()
        assertEquals(listOf("1.0-new"), sent)
        status = available("1.0-next")
        check.execute()
        assertEquals(listOf("1.0-new", "1.0-next"), sent)
        assertEquals("1.0-next", last)
    }

    @Test fun `failed current and local checks never notify or mark sent`() = runTest {
        for (status in listOf(UpdateStatus.Failed("offline"), UpdateStatus.UpToDate("1.0-installed"), UpdateStatus.LocalBuild)) {
            BackgroundUpdateCheck({ status }, { "" }, { fail("Unexpected notification"); true },
                { fail("Unexpected saved version") }).execute()
        }
    }

    @Test fun `disabled notification remains eligible when permission is restored`() = runTest {
        var allowed = false
        var last = ""
        var sent = 0
        val check = BackgroundUpdateCheck({ available() }, { last }, { if (allowed) sent++; allowed }, { last = it })
        check.execute()
        assertEquals("", last)
        allowed = true
        check.execute()
        assertEquals(1, sent)
        assertEquals("1.0-new", last)
    }

    @Test fun `stopped check propagates cancellation`() = runTest {
        val service = object : UpdateService {
            override suspend fun fetchLatest(): UpdateInfo = throw CancellationException("stopped")
        }
        try {
            CheckForUpdateUseCase(service, "1.0-installed").execute()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
