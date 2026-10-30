# Phone microphone benchmark passages

Record each passage as a separate file named `benchmark-01.m4a`, `benchmark-02.m4a`, and `benchmark-03.m4a`.

Use your usual phone microphone, normal speaking voice, and usual phone distance. Keep the air conditioning and phone position the same for all three recordings. Leave about two seconds of room sound before and after each passage. Read only the blockquoted passage, including every word. Do not say the punctuation marks or headings. Numbers are written as words to make the intended speech clear.

If you make a reading mistake, restart that recording. Keep the original M4A files without trimming, noise removal, or other editing. Send the three files together. We can then check their actual codec, bitrate, sample rate, channel count, and duration.

## Recording one: everyday dictation

> Please remind me to pick up milk, eggs, and coffee after work tomorrow. I left my blue jacket on the chair beside the kitchen window. If it starts raining before I get home, please close the window and bring the shoes inside. I should be back around half past six, but I'll call if the bus is late.

## Recording two: names, numbers, and similar sounds

> Hi Maya, this is Daniel. Our appointment is on Thursday, October fifteenth, at nine forty-five in the morning. Please meet me at the west entrance of the library on Maple Street. The booking number is seven zero four two nine six. I paid thirty-seven dollars and fifty cents for two tickets. Bring the blue folder, not the black one. There are fifteen pages in the first report and fifty in the second. I thought you said we should leave at eight, but your message says we should wait.

## Recording three: longer technical dictation

> I'm testing an offline speech recognition app on my Android phone. The air conditioner is running in the background, and I'm speaking at my normal volume. Before each test, I want to check which model is selected and whether it is already loaded into memory. For a cold run, the model should start unloaded. For a warm run, it should already be available. I want to measure model loading, audio processing, and model unloading separately. The transcript should preserve what I actually say, including contractions, repeated words, and technical terms. For example, the app uses Kotlin, a microphone, and an audio buffer. It converts the recording into sixteen thousand samples per second before recognition. After the test finishes, save the complete transcript and the timing results. If recognition fails, keep the error message with that result. Don't count a failed run as a fast success. Finally, repeat the same recording so we can see whether the results stay consistent across several runs.

## What these recordings cover

The first passage covers a short everyday message. The second covers names, numbers, and easily confused words. The third provides a longer input with technical terms and contractions.

For word accuracy, agree on normalization before scoring. For example, `thirty-seven` and `37` should count as equivalent if we are measuring recognition rather than number formatting. Score punctuation and capitalization separately if needed.

These recordings give us fixed audio inputs for comparing models. Timing must be measured in the app on the same phone, using a defined cold or warm state. Three passages from one speaker in one room provide a personal benchmark; they do not establish accuracy for other speakers or recording environments. File-based tests also do not measure the live microphone and voice activity detection flow unless we explicitly route the recordings through that flow.
