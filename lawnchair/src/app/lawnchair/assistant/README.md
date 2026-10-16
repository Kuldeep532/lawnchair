# Nexus Assistant

Nexus Assistant is embedded in Nexus Launcher. This document describes current source behavior; it does not claim device-tested release readiness.

## Available in source

- Compose screen with text and voice input, plus Android TextToSpeech output.
- Hindi-capable speech recognition through Android's recognizer. Recognition can depend on system/network services and is not guaranteed to work offline.
- Device actions for web search, launching apps by label, flashlight, opening Internet/Wi-Fi and Bluetooth settings, general settings, alarms, and timers.
- Optional on-device chat through Llamatik/llama.cpp using a downloaded GGUF model.
- Optional ElevenLabs online speech through the configured Supabase gateway. The provider key must remain on the server.
- Tools Studio shortcuts and protected-text utility are maintained separately in the Tools Studio activity.

## Lightweight local chat model

**SmolLM2-135M-Instruct Q4_K_M GGUF** is downloaded to app-private storage on demand. The model is around 105 MB and is a lightweight local chat option, not a high-capability general assistant. Download completion is not considered inference readiness: the model must load successfully before it can answer locally.

Model source: [Hugging Face — SmolLM2 GGUF](https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF).

## Speech output: what works and what does not

**Local voice:** The implemented offline speech path uses the text-to-speech engine installed on the user's Android device. Available languages and naturalness depend on the installed voice data and whether that voice supports offline speech.

**ElevenLabs:** The optional online neural voice uses the authenticated Supabase gateway. It needs a working signed-in session, correctly configured server-side credentials, and any required plan or credits. The code path still needs verification against the app's canonical authentication lifecycle and on a real device.

**Chatterbox:** Chatterbox speech generation is not currently integrated. The earlier large download-only path was removed; the project does not currently tokenize input, run Chatterbox inference, decode audio, and play that generated waveform end to end. Do not describe Chatterbox as a working local voice engine until that pipeline has been implemented and tested.

**Coqui TTS:** Coqui is not integrated as a native on-device Android inference engine. It should not be presented as available until a suitable model and compatible Android inference pipeline are selected and validated.

**Piper:** The repository contains a Piper voice catalog and file downloader, but a catalog and downloaded ONNX files alone do not prove Piper speech generation works. A compatible runtime, inference flow, and audio playback must be wired and tested before describing Piper as a working voice option.

## Online AI and session status

The assistant has a provider selector for Gemini, OpenAI, and Anthropic and attempts to use the configured Supabase AI gateway before using local chat. Online access is not verified until the app's actual sign-in/sign-out flow supplies and refreshes the user's session consistently. Conversation history is not yet confirmed as persistent across requests.

API secrets belong on the server, never in the APK. Provider and premium access depend on server configuration.

## Release verification still required

- Confirm the Android debug build and formatting checks after the latest source changes.
- Test first-run and repeat model downloads, interrupted/low-storage handling, and local model load failures.
- Test local TTS and ElevenLabs playback, network failure fallback, language selection, and TalkBack/screen-reader navigation.
- Wire and verify session creation, refresh, and sign-out against the app's canonical authentication flow before claiming online AI or ElevenLabs is release-ready.
- Exercise every Tools Studio destination and Secure Vault flows on a physical Android device.

On modern Android, an ordinary app generally cannot toggle Wi-Fi directly; the assistant opens the Android Internet panel instead.
