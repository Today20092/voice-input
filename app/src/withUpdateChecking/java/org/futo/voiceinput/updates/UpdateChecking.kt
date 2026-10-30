package org.futo.voiceinput.updates

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import org.futo.voiceinput.settings.LAST_UPDATE_CHECK_RESULT
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.setSetting

const val UPDATE_URL = "https://api.github.com/repos/Today20092/voice-input/releases/latest"
private val updateClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

suspend fun checkForUpdate(): UpdateResult? {
    return withContext(Dispatchers.IO) {
        val request = Request.Builder().url(UPDATE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Today20092-VoiceInput")
            .build()

        try {
            updateClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()?.let(::parseGitHubRelease)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}

suspend fun checkForUpdateAndSaveToPreferences(context: Context): Boolean {
    val updateResult = checkForUpdate()
    if(updateResult != null) {
        withContext(Dispatchers.IO) {
            context.setSetting(LAST_UPDATE_CHECK_RESULT, Json.encodeToString(updateResult))
        }
        return true
    }

    return false
}

suspend fun retrieveSavedLastUpdateCheckResult(context: Context): UpdateResult? {
    return UpdateResult.fromString(context.getSetting(LAST_UPDATE_CHECK_RESULT))
}

const val JOB_ID: Int = 15782789
fun scheduleUpdateCheckingJob(context: Context) {
    val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    if(jobScheduler.getPendingJob(JOB_ID) != null) {
        println("Job already scheduled, no need to do anything")
        return
    }

    var jobInfoBuilder = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckingService::class.java))
        .setPeriodic(1000 * 60 * 60 * 24 * 2) // every two days
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED) // on unmetered Wi-Fi
        .setPersisted(true) // persist after reboots

    // Update checking has minimum priority
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        jobInfoBuilder = jobInfoBuilder.setPriority(JobInfo.PRIORITY_MIN)
    }

    jobScheduler.schedule(jobInfoBuilder.build())
}
