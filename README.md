# EnodaAI - Tamil Nadu style AI Voice Assistant

A premium Android AI voice assistant built with Kotlin and Jetpack Compose.
Speak or type, EnodaAI searches the web, thinks, and answers out loud in a
warm Tamil Nadu style - in English or Tamil, with a male or a female voice.

## Features

- **Voice and text input** - tap the glowing orb to speak, or type in the
  Indus-style chat box. Both routes use the same pipeline:
  request -> web search -> spoken reply.
- **LLM answers** - direct Groq access (OpenAI-compatible, llama-3.3-70b)
  using your own free API key, with the FastAPI backend as a fallback.
- **Web search** - keyless DuckDuckGo search, results injected into the
  prompt so answers are grounded in fresh results.
- **Local app control** - open any installed app by voice, in English,
  Tanglish or Tamil ("WhatsApp open pannunga", "YouTube திற").
- **Phone calls** - call a contact by name, with full permission handling.
- **Offline and Online modes** - Online uses the LLM plus web search.
  Offline answers from the device only: time, date, battery, quick
  arithmetic, small talk, app launching and calls.
- **Tamil Nadu male and female voices** - picks a real gendered voice when
  the device TTS engine has one, otherwise shapes pitch and rate so the two
  are clearly distinguishable.
- **Premium UI** - midnight gradient, a breathing voice orb that reflects
  the assistant state, gradient chat bubbles and a settings bottom sheet.

## Architecture

```
app/                     Android app (Kotlin, Jetpack Compose, Material 3)
  core/network/          Retrofit APIs (EnodaAI backend, Groq) + web search
  core/speech/           Speech recognition and text to speech wrappers
  data/                  Chat repository, settings store
  ui/                    Assistant screen, view model, premium theme
  utils/                 Local command handler, app launcher, phone calls,
                         offline brain
backend/                 FastAPI service (Gemini -> Groq, Tavily search)
```

The app works standalone: add a Groq API key in Settings for Online mode.
The backend is optional and can be pointed at any compatible deployment.

## Building

Requires JDK 17 or newer and the Android SDK (compileSdk 37, build-tools 37).

```bash
./gradlew :app:assembleDebug      # installable debug APK
./gradlew :app:assembleRelease    # unsigned release APK
```

Release signing is intentionally not configured in this repository - add your
own `signingConfigs` block and keep the keystore out of version control.

## Installing

Download the APK from the Releases page, copy it to your phone and allow
installation from unknown sources. Grant microphone, contacts and phone
permissions on first use.

## Notes

- Web search uses DuckDuckGo's HTML endpoint, so no search API key is needed.
- Speech recognition relies on the device's Google speech service.
- Tamil voice quality depends on the TTS engine installed on the device.

## License

Personal project by Vishnu Saravanan.
