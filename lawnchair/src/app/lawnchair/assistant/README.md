# Nexus Assistant

Nexus Assistant is embedded in the Nexus Launcher codebase at `lawnchair/src/app/lawnchair/assistant`.

## Currently implemented

- Screen-reader-friendly Compose interface for text and voice input.
- Hindi speech recognition through the Android speech recognizer.
- Spoken responses through Android TextToSpeech when a Hindi voice is available on the device.
- Web search, with a browser search fallback.
- Flashlight control through Android's camera torch API.
- Wi-Fi and mobile Internet controls via the Android Internet panel (regular apps cannot directly toggle Wi-Fi on modern Android versions).
- Opening installed apps by their visible launcher label, plus common app aliases.
- Bluetooth, system settings, alarms and timer shortcuts.
- Safe error messages if Android cannot start the requested action.

## Voice-model status

The currently active voice engine is Android TextToSpeech. The earlier Hugging Face download request was not an end-to-end speech solution: a downloaded ONNX file alone cannot synthesize speech. To fulfil offline neural voice calls, the project still needs a compatible ONNX runtime such as Sherpa-ONNX, validated model/config downloads, language/voice selection, and playback tests across supported Android ABIs.

## What “free and unlimited” means

Local command handling and Android TextToSpeech do not require a per-call AI API. Web search and the Android recognizer may rely on Internet access or device/system services. A truly offline general-purpose conversational model would require downloading a language model to the phone and accounting for device storage, RAM, heat, and battery use. No cloud service can be promised unlimited and free without provider limits.

## Safety boundaries

Android restricts several device controls to system apps, device-owner privileges, or explicit user confirmation. The assistant opens system panels for settings it cannot change directly. It does not attempt to bypass Android permission or privacy controls.
