package com.numbawang.leimaus.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Goes through the real Moshi configuration rather than a hand-rolled parser. The failure mode
 * this guards against is a DTO that compiles and passes every use-case test — those inject a fake
 * service and never touch Moshi — but has no usable adapter at runtime, which surfaces only on the
 * phone as "Failed to find the generated JsonAdapter class".
 */
class UpdateInfoParsingTest {

    /** Shaped exactly like the heredoc in .github/workflows/build-test-apk.yml. */
    private val publishedPayload = """
        {
          "versionName": "1.0-9d9fe8f",
          "commit": "9d9fe8f",
          "builtAtUtc": "2026-08-08T12:32:27Z",
          "apkUrl": "https://github.com/merilainen-star/HourWizard/releases/download/test-build/Numbawang-test.apk",
          "apkSizeBytes": 19436901,
          "apkSha256": "8f3b39ddc4a22e88a35e334e5d36e740d0a176d47010f9467e119e44dd3c2c28"
        }
    """.trimIndent()

    @Test
    fun `the payload CI publishes parses`() {
        val info = HttpUpdateService.parseUpdateInfo(publishedPayload)

        assertEquals("1.0-9d9fe8f", info.versionName)
        assertEquals("9d9fe8f", info.commit)
        assertEquals("2026-08-08T12:32:27Z", info.builtAtUtc)
        assertEquals(19_436_901L, info.apkSizeBytes)
        assertEquals(
            "8f3b39ddc4a22e88a35e334e5d36e740d0a176d47010f9467e119e44dd3c2c28",
            info.apkSha256,
        )
        assertTrue(info.apkUrl.endsWith("Numbawang-test.apk"))
    }

    /** A truncated or unrelated body must fail loudly rather than yielding a half-built object. */
    @Test
    fun `a missing field is rejected`() {
        val result = runCatching {
            HttpUpdateService.parseUpdateInfo("""{"versionName": "1.0-abc1234"}""")
        }

        assertTrue("expected the parse to fail", result.isFailure)
    }

    @Test
    fun `text that is not json is rejected`() {
        val result = runCatching { HttpUpdateService.parseUpdateInfo("<html>404</html>") }

        assertTrue("expected the parse to fail", result.isFailure)
    }

    /** The constant and the workflow have to point at the same release, or the check reads a 404. */
    @Test
    fun `the metadata url points at the test-build release`() {
        assertEquals(
            "https://github.com/merilainen-star/HourWizard/releases/download/test-build/latest.json",
            HttpUpdateService.LATEST_JSON_URL,
        )
    }
}
