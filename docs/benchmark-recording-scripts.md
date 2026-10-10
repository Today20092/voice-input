# Benchmark recording scripts

Record each passage as a separate audio file on your Galaxy S25 Ultra. Use the same recorder, microphone position, and quiet room for all four. Speak naturally at your usual dictation speed. Leave about one second of silence at the beginning and end. Disable audio effects if your recorder offers that choice. Keep the original recording format; do not convert or trim it yet.

These scripts form a small personal dictation sample. Results describe this speaker and these recordings, rather than general model accuracy.

Read only the passage beneath each filename. Do not say the filename or punctuation instructions. If you change a word, either record again or provide the exact words you actually said so the reference transcript can be corrected. Punctuation, casing, and number formatting will need separate treatment from spoken-word accuracy.

## 01-everyday

Hey Sam, I'm running about fifteen minutes late. Please start without me, and save me a seat near the window. After lunch, I need to pick up groceries, return a library book, and call the repair shop. If the package arrives while I'm out, leave it inside the front door. I'll send you a message when I'm on my way home.

## 02-numbers-and-names

My appointment with Doctor Maya Patel is on Thursday, November nineteenth, at nine forty-five in the morning. The reference number is seven four two nine zero six. I ordered three cables for twenty-four dollars and fifty cents, plus a five-dollar delivery fee. Please send the receipt to Jordan Rivera. The meeting room is on the sixth floor, and the access code is zero eight three one.

## 03-technical-dictation

I'm testing offline speech recognition on a Samsung Galaxy S twenty-five Ultra. The app supports Orukeet, Moonshine, Parakeet, Nemotron, and Whisper. Run the same audio through every model, measure initialization and decoding separately, and export a JSON report. If a model fails to load, record the error and continue. Before updating the README, check the reference transcript, word error rate, and memory measurements.

## 04-longer-natural-speech

Yesterday I tried to organize a weekend trip, and almost every detail changed. At first, we planned to leave early on Saturday, take the train, and meet everyone at the station. Then Alex remembered that the museum opens later than we expected, so we moved lunch to the beginning of the day. I thought that would make things easier, but the restaurant couldn't seat all of us together. We decided to bring sandwiches instead and find somewhere outside to eat.

The weather forecast was another problem. It said there might be rain in the afternoon, although the morning should be clear. I packed a light jacket, an umbrella, and an extra pair of socks. Before leaving, I checked the tickets twice and downloaded the directions because the signal can be unreliable along the route. In the end, the trip went smoothly. We missed one stop, walked a little farther than planned, and still had enough time to see everything we wanted.

## Recording handoff

For each recording, supply the original file and any spoken deviations from its script. If there are no deviations, the script is the reference text. Keep false starts, repeated words, and fillers in the reference if they remain in the recording.

When the suite is assembled, retain each original and create a shared canonical audio input for every model. Record the audio hashes, actual durations, conversion settings, and reference text in the suite manifest so future runs use the same inputs.
