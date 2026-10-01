package com.numbawang.leimaus.data.update

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Save deduplication state only after an enabled notification was actually posted. */
class BackgroundUpdateCheck(
    private val check: suspend () -> UpdateStatus,
    private val lastNotifiedVersion: () -> String,
    private val notify: (UpdateStatus.Available) -> Boolean,
    private val saveNotifiedVersion: (String) -> Unit,
) {
    suspend fun execute() {
        val status = check()
        currentCoroutineContext().ensureActive()
        if (shouldNotifyAboutUpdate(status, lastNotifiedVersion())) {
            val available = status as UpdateStatus.Available
            if (notify(available)) saveNotifiedVersion(available.versionName)
        }
    }
}
