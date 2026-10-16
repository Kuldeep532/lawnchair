# Nexus Assistant

Nexus Assistant is embedded in the Nexus Launcher app.

## Implemented in source

- Accessible Compose assistant screen with text and voice input.
- Hindi speech recognition through Android's recognizer, with a system settings fallback if unavailable.
- Android TextToSpeech spoken output.
- Device command routing for web search, opening apps by label, flashlight control, Internet/Wi-Fi settings, Bluetooth settings, general settings, alarms, and timers.
- On-demand download prompt for a lightweight local GGUF chat model and Chatterbox ONNX voice assets.
- Download progress, app-private storage, partial-file cleanup, retry behavior, and shared voice-download state in Book Library.
- Local chat wrapper using Llamatik's Android llama.cpp artifact. It loads the downloaded GGUF model and routes free-form prompts to on-device generation.

## Models

**Local chat model:** [SmolLM2-135M-Instruct Q4_K_M](https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF), approximately 105 MB. It is a small model suited to a lightweight on-device chat proof of concept, not a high-capability assistant model.

**High-quality voice assets:** [Chatterbox ONNX](https://huggingface.co/onnx-community/chatterbox-ONNX). The selected Q4 ONNX assets are over 1 GB combined. The model card is MIT-licensed and currently provides English speech-generation assets.

## Important remaining limitation

Downloading the Chatterbox model files is implemented, but actual neural speech generation from those files is **not yet integrated**. Chatterbox needs an end-to-end ONNX pipeline for text tokenization, model inputs, voice conditioning, decoding and waveform output; merely including ONNX Runtime or downloading the files does not make speech playback work. Android TextToSpeech remains the active voice engine until that pipeline is implemented and tested.

The first download prompt currently tracks whether model assets exist and can be loaded as a local chat model. A finished APK build and tests on a real Android device are still required before claiming release readiness. Android may use network services for speech recognition; voice input is not guaranteed to be offline or unlimited. Normal apps cannot directly toggle Wi-Fi on modern Android, so the assistant opens the system panel.


## Online AI through Supabase

- Nexus Assistant now tries the `nexus-ai-chat` Supabase Edge Function before falling back to local on-device chat.
- The assistant settings provide an accessible provider selector for Gemini, OpenAI, and Anthropic.
- The Android app contains only the project URL and the public publishable key. Provider API keys and Supabase secret keys must remain in Edge Function secrets.
- Online AI requires the app's existing sign-in flow to supply its verified Supabase access token to `NexusCloudAssistant.saveSession(context, accessToken, refreshToken)`. The current launcher code does not yet expose an established shared Supabase session provider; until that is wired to the real authentication flow, online requests show a sign-in message and the local assistant remains available.
- OpenAI and Anthropic calls are subject to the existing Edge Function's premium-access checks. Gemini access is controlled by the server's configured credentials.
- This cloud request currently sends the latest question without conversation history. Conversation persistence and session refresh should be wired through the app's canonical Supabase session manager before release.
