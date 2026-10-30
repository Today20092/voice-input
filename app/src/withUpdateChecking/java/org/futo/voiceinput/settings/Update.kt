package org.futo.voiceinput.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import org.futo.voiceinput.openURI
import org.futo.voiceinput.updates.UpdateResult
import org.futo.voiceinput.updates.checkForUpdateAndSaveToPreferences
import org.futo.voiceinput.updates.retrieveSavedLastUpdateCheckResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
@Preview
fun ConditionalUpdate() {
    val (updateInfo, _) = useDataStore(LAST_UPDATE_CHECK_RESULT)

    val lastUpdateResult = if(!LocalInspectionMode.current){
        UpdateResult.fromString(updateInfo)
    } else {
        UpdateResult(123, "abc", "1.2.3")
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    NavigationItem(
        title = if (checking) "Checking for updates…" else "Check for updates",
        subtitle = status ?: "Installed ${UpdateResult.currentVersionString()} · GitHub stable releases",
        style = NavigationItemStyle.Misc,
        navigate = {
            if (!checking) {
                checking = true
                status = "Checking this fork's GitHub releases…"
                scope.launch {
                    try {
                        val success = checkForUpdateAndSaveToPreferences(context)
                        val result = if (success) retrieveSavedLastUpdateCheckResult(context) else null
                        status = when {
                            result == null -> "Couldn't check GitHub. Check your connection and try again."
                            result.isNewer() -> "Update available: ${result.nextVersionString}"
                            else -> "You're up to date. Latest stable release: ${result.nextVersionString}"
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        status = "Couldn't check GitHub. Please try again."
                    } finally {
                        checking = false
                    }
                }
            }
        }
    )
    if(lastUpdateResult != null && lastUpdateResult.isNewer()) {
        NavigationItem(
            title = "Update Available",
            subtitle = "${UpdateResult.currentVersionString()} -> ${lastUpdateResult.nextVersionString}",
            style = NavigationItemStyle.Misc,
            navigate = {
                context.openURI(lastUpdateResult.apkUrl)
            }
        )
    }
}
