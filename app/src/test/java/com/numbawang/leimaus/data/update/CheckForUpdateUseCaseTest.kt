package com.numbawang.leimaus.data.update

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckForUpdateUseCaseTest {

    private fun published(versionName: String, sizeBytes: Long = 19_420_493) =
        UpdateInfo(
            versionName = versionName,
            commit = versionName.substringAfter('-'),
            builtAtUtc = "2026-08-08T09:00:00Z",
            apkUrl = "https://example.invalid/Numbawang-test.apk",
            apkSizeBytes = sizeBytes,
        )

    private fun service(info: UpdateInfo) = object : UpdateService {
        override suspend fun fetchLatest() = info
    }

    private fun failing(message: String) = object : UpdateService {
        override suspend fun fetchLatest(): UpdateInfo = error(message)
    }

    /** A build the network never has to be consulted about. */
    @Test
    fun `a version name without a commit is a local build`() = runTest {
        val status = CheckForUpdateUseCase(
            service = failing("should not be called"),
            installedVersionName = "1.0",
        ).execute()

        assertEquals(UpdateStatus.LocalBuild, status)
    }

    @Test
    fun `the same version name is up to date`() = runTest {
        val status = CheckForUpdateUseCase(
            service = service(published("1.0-c07cfac")),
            installedVersionName = "1.0-c07cfac",
        ).execute()

        assertEquals(UpdateStatus.UpToDate("1.0-c07cfac"), status)
    }

    /**
     * Commit hashes cannot be ordered, so "different from what is published" is the only signal
     * available — and the right one, since the published build is the current test build.
     */
    @Test
    fun `a different version name offers the published build`() = runTest {
        val status = CheckForUpdateUseCase(
            service = service(published("1.0-a1b2c3d")),
            installedVersionName = "1.0-c07cfac",
        ).execute()

        val available = status as UpdateStatus.Available
        assertEquals("1.0-a1b2c3d", available.versionName)
        assertEquals("https://example.invalid/Numbawang-test.apk", available.apkUrl)
    }

    @Test
    fun `the size is rounded to the nearest megabyte`() = runTest {
        val status = CheckForUpdateUseCase(
            service = service(published("1.0-a1b2c3d", sizeBytes = 19_420_493)),
            installedVersionName = "1.0-c07cfac",
        ).execute()

        assertEquals(19, (status as UpdateStatus.Available).sizeMb)
    }

    /** A failed check must never be reported as "up to date". */
    @Test
    fun `a network failure is reported with its reason`() = runTest {
        val status = CheckForUpdateUseCase(
            service = failing("GitHub vastasi HTTP 503"),
            installedVersionName = "1.0-c07cfac",
        ).execute()

        val failed = status as UpdateStatus.Failed
        assertTrue(failed.reason.contains("503"))
    }

    private fun available(versionName: String) =
        UpdateStatus.Available(versionName, "https://example.invalid/Numbawang-test.apk", 22)

    @Test
    fun `an unseen version is worth a notification`() {
        assertTrue(shouldNotifyAboutUpdate(available("1.0-a1b2c3d"), lastNotifiedVersion = ""))
    }

    /** The check runs every morning; the same build must not be announced day after day. */
    @Test
    fun `a version already notified about is not repeated`() {
        assertFalse(
            shouldNotifyAboutUpdate(available("1.0-a1b2c3d"), lastNotifiedVersion = "1.0-a1b2c3d")
        )
    }

    @Test
    fun `a newer version after a notified one is announced again`() {
        assertTrue(
            shouldNotifyAboutUpdate(available("1.0-e5f6g7h"), lastNotifiedVersion = "1.0-a1b2c3d")
        )
    }

    /** Silence is the right answer for everything that is not an available build. */
    @Test
    fun `no other status raises a notification`() {
        assertFalse(shouldNotifyAboutUpdate(UpdateStatus.UpToDate("1.0-a1b2c3d"), ""))
        assertFalse(shouldNotifyAboutUpdate(UpdateStatus.LocalBuild, ""))
        assertFalse(shouldNotifyAboutUpdate(UpdateStatus.Failed("ei verkkoa"), ""))
        assertFalse(shouldNotifyAboutUpdate(UpdateStatus.Idle, ""))
        assertFalse(shouldNotifyAboutUpdate(UpdateStatus.Checking, ""))
    }
}
