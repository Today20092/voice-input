package org.futo.voiceinput.updates

import org.junit.Assert.*
import org.junit.Test

class GitHubReleaseTest {
    private fun release(tag: String = "v1.4.4", prerelease: Boolean = false,
                        url: String = "$FORK_RELEASES_URL/download/$tag/futo-voice-input-moonshine-$tag.apk") = """
        {"tag_name":"$tag","draft":false,"prerelease":$prerelease,
         "assets":[{"name":"futo-voice-input-moonshine-$tag.apk","browser_download_url":"$url"}],
         "ignored_future_field":true}
    """.trimIndent()

    @Test fun parsesStableForkApk() {
        val result = parseGitHubRelease(release())!!
        assertEquals("1.4.4", result.nextVersionString)
        assertTrue(result.githubRelease)
    }

    @Test fun rejectsPrereleasesMissingApksAndOtherRepositories() {
        assertNull(parseGitHubRelease(release(prerelease = true)))
        assertNull(parseGitHubRelease(release(tag = "v1.4.4-beta.1")))
        assertNull(parseGitHubRelease(release(url = "https://example.com/update.apk")))
        assertNull(parseGitHubRelease(release().replace("\"draft\":false", "\"draft\":true")))
        assertNull(parseGitHubRelease(release().replace("futo-voice-input-moonshine-", "other-")))
    }

    @Test fun comparesVersionsNumericallyAndHandlesFlavorSuffixes() {
        assertTrue(isNewerRelease("1.10.0", "1.9.9-moonshine"))
        assertFalse(isNewerRelease("1.4.4", "1.4.4-moonshine"))
        assertFalse(isNewerRelease("1.4.3", "1.4.4-moonshine"))
        assertTrue(isNewerRelease("1.4.4", "1.4.4-harper-beta.1-moonshine"))
        assertFalse(isNewerRelease("1.4.4", "1.5.0-beta.1-moonshine"))
        assertFalse(isNewerRelease("invalid", "1.4.4"))
    }

    @Test fun ignoresCachedUpstreamResults() {
        assertNull(UpdateResult.fromString("""{"nextVersion":999,"apkUrl":"https://voiceinput.futo.org/app.apk","nextVersionString":"9.0.0"}"""))
    }
}
