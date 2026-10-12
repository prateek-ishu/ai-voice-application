# AI Voice Presentation (Java / Spring Boot + Ollama)

A slide deck you control by voice. Speak (or type) to move between slides, jump to any slide,
or ask questions answered from the speaker notes. The presenter replies out loud.

Everything is free: the language model runs locally in Ollama, and the browser handles
speech recognition and speech synthesis.

```
Browser                                     Spring Boot (port 8000)           Ollama (port 11434)
 mic ─▶ SpeechRecognition ─▶ text ─(WebSocket)─▶ VoiceAgent ─▶ LlmService ─▶ /api/chat
 speaker ◀─ speechSynthesis ◀─ speech ◀─(WebSocket)─┘
```

Simple commands ("next", "go back", "slide 3", "start over") are handled without calling the model.

## Requirements

- Java 17 or newer
- Maven 3.9+ (or IntelliJ's bundled Maven)
- [Ollama](https://ollama.com/download)
- Chrome or Edge for voice input (Firefox can still type)

## Set up Ollama (once)

Install Ollama, then download a model:

```bash
ollama pull llama3.2:3b
```

`llama3.2:3b` runs on most laptops. If you have a GPU or 16 GB+ RAM, `llama3.1:8b` or `qwen2.5:7b`
answer questions better. Ollama runs in the background after install; if it isn't running, start it with `ollama serve`.

## Run it

```bash
cd backend
mvn spring-boot:run
```

Open http://localhost:8000.

**In IntelliJ:** open the `backend` folder and run `VoicePresentationApplication`.

To use a different model without editing files, set the environment variable `OLLAMA_MODEL`
(for example `OLLAMA_MODEL=qwen2.5:7b`).

No `mvn` on your PATH? Use IntelliJ's bundled Maven, for example on Windows:
`"C:\Program Files\JetBrains\IntelliJ IDEA 2023.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" spring-boot:run`

**Tests:** `mvn test` (no Ollama needed)

## Voice service (optional, Python)

`voice-service/` is an offline speech-to-text (faster-whisper) and text-to-speech (Piper) API on port 8001.
The page currently uses the browser's built-in speech, so the backend runs fine without it.

```bash
cd voice-service
python -m venv .venv
.venv\Scripts\activate             # macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
copy .env.example .env             # macOS/Linux: cp .env.example .env
python -m piper.download_voices en_US-lessac-medium --download-dir models
uvicorn app.main:app --port 8001
```

The Whisper model downloads on first start. Check http://localhost:8001/health. Tests: `pytest`.

## Configuration

`backend/src/main/resources/application.yml`:

| Setting | Default | Notes |
|---|---|---|
| `app.ollama.base-url` | `http://localhost:11434` | or env `OLLAMA_BASE_URL` |
| `app.ollama.model` | `llama3.2:3b` | or env `OLLAMA_MODEL`; must be pulled first |
| `app.ollama.timeout-seconds` | `120` | first call is slow while the model loads |
| `app.ollama.keep-alive` | `10m` | how long the model stays in memory |

## Good to know

- **Speech recognition in Chrome and Edge sends audio to the browser vendor's servers.** It's free,
  but not offline. For fully offline voice input, run a local Whisper server (e.g. whisper.cpp)
  and send audio to it instead.
- **Speech synthesis is fully local** and uses the voices installed on your computer.
- Mic access needs `localhost` or HTTPS.

## Project layout

```
backend/src/main/java/com/voicepresentation/
├── VoicePresentationApplication.java   entry point
├── config/        AppProperties, WebSocketConfig, OllamaClient
├── controller/    SlideController       GET /api/health, GET /api/slides
├── websocket/     VoiceWebSocketHandler /ws, one VoiceAgent per session
├── model/         Slide, Deck, AgentAction, LlmDecision, AgentResponse
└── service/       SlideService, SlideNavigator, LlmService, VoiceAgent, VoiceAgentFactory
backend/src/main/resources/
├── application.yml
├── data/slides.json     the deck
└── static/              index.html, style.css, app.js (served at /)
```

## Editing the deck

Edit `data/slides.json`. Each slide has `title`, `bullets`, and `notes`.
The notes are what the presenter reads and uses to answer questions.

## WebSocket protocol

| Direction | Message | Meaning |
|---|---|---|
| client → server | `{"type":"text","text":"..."}` | what the user said or typed |
| client → server | `{"type":"navigate","action":"next\|previous\|goto","slide":3}` | silent navigation |
| server → client | `{"type":"status","state":"thinking\|idle"}` | progress |
| server → client | `{"type":"agent_response", ...}` | `action`, `slide_index`, `transcript`, `speech` |
| server → client | `{"type":"error","message":"..."}` | turn failed, with how to fix it |
