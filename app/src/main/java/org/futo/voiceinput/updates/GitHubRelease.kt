package org.futo.voiceinput.updates

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val FORK_RELEASES_URL = "https://github.com/Today20092/voice-input/releases"
private val releaseJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class GitHubAsset(val name: String, val browser_download_url: String)

@Serializable
private data class GitHubRelease(
    val tag_name: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<GitHubAsset>
)

// GitHub tags describe versions, not Android version codes. Compare numeric components.
internal fun releaseVersion(version: String): List<Int>? {
    val match = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-[A-Za-z0-9.-]+)?$").matchEntire(version)
        ?: return null
    return (1..3).map { match.groupValues[it].toIntOrNull() ?: return null }
}

internal fun isNewerRelease(latest: String, installed: String): Boolean {
    val next = releaseVersion(latest) ?: return false
    val current = releaseVersion(installed) ?: return false
    for (index in next.indices) {
        if (next[index] != current[index]) return next[index] > current[index]
    }
    return installed.contains("-beta") || installed.contains("-rc") || installed.contains("-alpha")
}

internal fun parseGitHubRelease(body: String): UpdateResult? {
    val release = releaseJson.decodeFromString<GitHubRelease>(body)
    if (release.draft || release.prerelease ||
        !Regex("^v?\\d+\\.\\d+\\.\\d+$").matches(release.tag_name) ||
        releaseVersion(release.tag_name) == null) return null
    val expectedName = "futo-voice-input-moonshine-${release.tag_name}.apk"
    val asset = release.assets.singleOrNull {
        it.name == expectedName &&
            it.browser_download_url == "$FORK_RELEASES_URL/download/${release.tag_name}/$expectedName"
    } ?: return null
    return UpdateResult(0, asset.browser_download_url, release.tag_name.removePrefix("v"), true)
}
