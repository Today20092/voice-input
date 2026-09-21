package org.futo.voiceinput.settings

enum class SettingsDestination(val route: String) {
    Home("home"),
    AudioHistory("audioHistory"),
    Advanced("advanced"),
    Help("help"),
    Languages("languages"),
    Testing("testing"),
    Models("models"),
    TranscriptCleanup("transcriptCleanup"),
    PersonalDictionary("personalDictionary"),
    Diagnostics("diagnostics"),
    Input("input"),
    Themes("themes"),
    Credits("credits"),
    Dependencies("dependencies"),
    PleasePay("pleasePay"),
    Paid("paid"),
    Error("error")
}
