package org.futo.voiceinput.shared

/** Lifecycle surface used by the shared view; the supplied session owns capture. */
interface RecognizerSession {
    fun start()
    fun finish()
    fun cancel()
    fun reset()
    fun inhibitAutoStop(inhibit: Boolean)
    fun openPermissionSettings()
}
