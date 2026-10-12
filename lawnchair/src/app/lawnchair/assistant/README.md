# Nexus Assistant

Nexus Assistant is integrated into the Lawnchair app source tree under `app.lawnchair.assistant`.

## Included in this first implementation

- Accessible Compose screen with text commands, microphone button, and spoken response.
- Web search and a general search fallback.
- Flashlight toggle through Android's camera torch API.
- Internet settings panel for Wi-Fi controls, because Android 10+ does not let ordinary apps directly enable/disable Wi-Fi.
- Android TextToSpeech fallback so spoken responses work without bundling a speech engine in the base APK.
- On first launch, attempts to download a public Piper ONNX voice file from Hugging Face to app-specific external storage. Download is user-visible via Android's Download Manager.

## Important limitation

The downloaded `.onnx` file is currently only a downloaded model asset. It is not yet executed by a Sherpa-ONNX/Piper inference runtime, so it does **not** change the active voice engine yet. The app currently speaks through Android TextToSpeech. To make that downloaded voice actually speak, integrate a compatible native runtime and verify its license, ABI support, model metadata, and device performance.

This is a first working slice, not a claim that every possible assistant capability has been implemented. Opening arbitrary installed apps by natural-language name and advanced device controls need explicit resolver/intent handling and Android permission/system-role support.