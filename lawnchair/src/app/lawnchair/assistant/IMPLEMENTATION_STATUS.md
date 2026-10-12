# Nexus Assistant — implementation status

## Integrated in `16-dev`

- The assistant screen is embedded in the launcher and registered in the manifest.
- Text/voice input, spoken responses, web search, flashlight, Internet/Wi-Fi panel, app launching, Bluetooth, settings, alarm and timer commands are connected.
- First-open popup requests the local model and Chatterbox voice assets, reports their downloaded/missing state, and shows download progress.
- Downloads use app-private storage, temporary files and retries after partial failures.
- Book Library voice settings now include Chatterbox asset status and a download action.
- Lightweight local chat model source: SmolLM2-135M-Instruct Q4_K_M GGUF (about 105 MB).
- Local model inference wrapper uses the published Llamatik Android library and routes free-form prompts through llama.cpp after the GGUF is loaded.

## Chatterbox high-quality voice status

- The assistant downloads a Q4 selection from the MIT-licensed Chatterbox ONNX repository; the selected assets total over 1 GB.
- Book Library accurately reports whether the model files have already downloaded.
- **Actual Chatterbox speech generation is not complete.** The end-to-end inference pipeline (tokenization, model inputs, conditioning, decoding and audio playback) is not yet integrated. The current spoken voice is Android TextToSpeech.
- Do not describe the downloaded ONNX files as a working voice engine until end-to-end playback tests pass.

## Remaining release checks

- Build the Android APK to confirm dependency and Kotlin API compatibility.
- Test local GGUF loading, free-form chat, screen-reader flows, low-storage handling and interrupted downloads on a physical device.
- If Chatterbox playback is required for release, finish and test its Android ONNX inference pipeline before release.
- Android speech recognition may rely on network/system services; voice input is not guaranteed to be offline or unlimited.
- Standard third-party apps cannot directly switch Wi-Fi on/off on modern Android, so the assistant opens the Android Internet panel.
