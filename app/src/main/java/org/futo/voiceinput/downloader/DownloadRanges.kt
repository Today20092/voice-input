package org.futo.voiceinput.downloader

internal data class DownloadRange(val start: Long, val endInclusive: Long) {
    val size = endInclusive - start + 1

    fun resumeAt(downloadedBytes: Long) = (start + downloadedBytes.coerceIn(0L, size))
}

internal fun downloadRanges(size: Long, count: Int = 4): List<DownloadRange> {
    require(size > 0 && count > 0)
    val actualCount = minOf(size, count.toLong()).toInt()
    return (0 until actualCount).map { index ->
        val start = size * index / actualCount
        DownloadRange(start, size * (index + 1) / actualCount - 1)
    }
}
