package org.futo.voiceinput.updates

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.futo.voiceinput.BuildConfig

@Serializable
data class UpdateResult(
    val nextVersion: Int,
    val apkUrl: String,
    val nextVersionString: String,
    val githubRelease: Boolean = false
) {
    fun isNewer(): Boolean {
        return githubRelease && isNewerRelease(nextVersionString, currentVersionString())
    }

    companion object {
        fun currentVersion(): Int {
            return BuildConfig.VERSION_CODE
        }

        fun currentVersionString(): String {
            return BuildConfig.VERSION_NAME
        }

        fun fromString(value: String): UpdateResult? {
            if(value.isEmpty()) {
                return null
            }

            try {
                return Json.decodeFromString<UpdateResult>(value).takeIf {
                    it.githubRelease && it.apkUrl.startsWith("$FORK_RELEASES_URL/download/")
                }
            } catch(e: SerializationException) {
                return null
            } catch(e: IllegalArgumentException) {
                return null
            }
        }
    }
}
