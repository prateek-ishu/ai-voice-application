// ---------- state ----------
const state = {
  deck: null,
  index: 0,
  connected: false,
  phase: "idle", // idle | listening | thinking | speaking
  turn: 0,          // id of the last message sent to the server
  activeTurn: null, // the turn whose reply we still want; null after an interrupt
  utterance: null,  // what the presenter is saying right now
  spokenText: "",
};

const $ = (id) => document.getElementById(id);
const STATUS_LABELS = {
  disconnected: "Reconnecting to server",
  idle: "Ready",
  listening: "Listening",
  thinking: "Working on it",
  speaking: "Speaking",
};

// ---------- rendering ----------
function renderSlide() {
  const slides = state.deck?.slides ?? [];
  const slide = slides[state.index];
  if (!slide) return;

  const title = $("slide-title");
  title.textContent = slide.title;
  title.style.animation = "none"; // restart the entrance animation
  void title.offsetWidth;
  title.style.animation = "";

  $("slide-bullets").replaceChildren(...slide.bullets.map((b) => {
    const li = document.createElement("li");
    li.textContent = b;
    return li;
  }));

  $("slide-progress").replaceChildren(...slides.map((_, i) => {
    const dot = document.createElement("span");
    dot.className = i === state.index ? "dot dot--on" : "dot";
    return dot;
  }));

  $("slide").setAttribute("aria-label", `Slide ${state.index + 1} of ${slides.length}`);
  $("slide-count").textContent = `${state.index + 1} / ${slides.length}`;
}

function renderStatus() {
  const status = state.connected ? state.phase : "disconnected";
  $("status").className = `status status--${status}`;
  $("status-text").textContent = STATUS_LABELS[status];

  // Speak and Send stay enabled while the presenter talks or thinks: using them interrupts it.
  const offline = status === "disconnected";
  $("mic-btn").disabled = offline && !recorder.isRecording;
  $("send-btn").disabled = offline || !$("typed").value.trim();
  $("stop-btn").disabled = state.phase === "idle" && !recorder.isRecording;
}

function setPhase(phase) {
  state.phase = phase;
  renderStatus();
}

function addMessage(role, text) {
  $("transcript-empty").hidden = true;
  const li = document.createElement("li");
  li.className = `msg msg--${role}`;
  const who = document.createElement("span");
  who.className = "msg__who";
  who.textContent = role === "user" ? "You" : role === "error" ? "Error" : "Presenter";
  const p = document.createElement("p");
  p.textContent = text;
  li.append(who, p);
  $("transcript-list").append(li);
  li.scrollIntoView({ behavior: "smooth", block: "end" });
}

// ---------- text-to-speech (browser built-in, free) ----------
const synth = window.speechSynthesis;
let voice = null;

function pickVoice() {
  const voices = synth?.getVoices() ?? [];
  voice = voices.find((v) => v.lang.startsWith("en") && v.localService)
    ?? voices.find((v) => v.lang.startsWith("en"))
    ?? voices[0] ?? null;
}
if (synth) {
  pickVoice();
  synth.onvoiceschanged = pickVoice; // Chrome loads voices asynchronously
}

function speak(text) {
  if (!synth || !text) return setPhase("idle");
  synth.cancel();
  const u = new SpeechSynthesisUtterance(text);
  if (voice) u.voice = voice;
  u.rate = 1.0;
  state.utterance = u;
  state.spokenText = text.toLowerCase();
  u.onstart = () => {
    if (state.utterance === u && voiceInterruptOn()) recorder.start("barge");
  };
  u.onend = u.onerror = () => {
    if (state.utterance !== u) return; // cancelled by an interrupt or replaced by a newer reply
    state.utterance = null;
    if (recorder.mode === "barge" && !recorder.heard) recorder.abort();
    if (state.phase === "speaking") setPhase("idle");
  };
  setPhase("speaking");
  synth.speak(u);
}

// ---------- interrupting ----------
// Stops the presenter mid-sentence and forgets any reply still on its way.
function interrupt() {
  state.utterance = null;
  synth?.cancel();
  state.activeTurn = null;
  if (state.phase === "speaking" || state.phase === "thinking") setPhase("idle");
}

function stopEverything() {
  recorder.abort();
  interrupt();
  setPhase("idle");
}

const STOP_WORDS = new Set(["stop", "stop talking", "stop it", "be quiet", "quiet", "pause", "wait", "hold on", "shut up", "enough"]);
const isStopWord = (text) => STOP_WORDS.has(text.toLowerCase().replace(/[^\w\s]/g, "").trim());

// While the presenter talks, the mic may hear the speakers. Words that mostly match
// what is being said are treated as that echo, not as the user.
function isEcho(text) {
  const words = text.toLowerCase().replace(/[^\w\s]/g, "").split(/\s+/).filter(Boolean);
  if (!words.length) return true;
  const spoken = new Set(state.spokenText.replace(/[^\w\s]/g, "").split(/\s+/));
  return words.filter((w) => spoken.has(w)).length / words.length >= 0.7;
}

function voiceInterruptOn() {
  return $("barge-toggle").checked;
}

// ---------- WebSocket with auto-reconnect ----------
const socket = {
  ws: null,
  retry: 0,

  connect() {
    const proto = location.protocol === "https:" ? "wss" : "ws";
    this.ws = new WebSocket(`${proto}://${location.host}/ws`);

    this.ws.onopen = () => {
      this.retry = 0;
      state.connected = true;
      renderStatus();
    };
    this.ws.onmessage = (e) => handleServerMessage(JSON.parse(e.data));
    this.ws.onclose = () => {
      state.connected = false;
      renderStatus();
      const delay = Math.min(1000 * 2 ** this.retry++, 10000);
      setTimeout(() => this.connect(), delay);
    };
  },

  get isOpen() {
    return this.ws?.readyState === WebSocket.OPEN;
  },

  sendJson(obj) {
    if (this.isOpen) this.ws.send(JSON.stringify(obj));
  },
};

// Every message carries a turn id; the server echoes it so replies to interrupted turns can be ignored.
function sendTurn(msg) {
  if (!socket.isOpen) {
    addMessage("error", "Not connected to the server, so that wasn't sent. Check the backend is running.");
    return setPhase("idle");
  }
  state.activeTurn = ++state.turn;
  socket.sendJson({ ...msg, turn: state.activeTurn });
}

function handleServerMessage(m) {
  const current = m.turn === undefined || m.turn === state.activeTurn;

  if (m.type === "status") {
    if (current) setPhase(m.state);
    return;
  }
  if (m.type === "error") {
    addMessage("error", m.message);
    if (current) setPhase("idle");
    return;
  }
  if (m.type === "agent_response") {
    // Always follow the server's slide so both sides agree, but only speak replies the user still wants.
    state.index = m.slide_index;
    renderSlide();
    if (m.transcript) addMessage("user", m.transcript);
    if (!current) return;
    if (m.speech) {
      addMessage("agent", m.speech);
      speak(m.speech);
    } else {
      setPhase("idle");
    }
  }
}

// ---------- speech-to-text (browser built-in, free) ----------
// Works in Chrome and Edge. Firefox doesn't support it, so typing is the fallback there.
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

// mode "manual": started with the Speak button.
// mode "barge": listens quietly while the presenter talks; the first real words interrupt it.
const recorder = {
  isRecording: false,
  mode: null,
  rec: null,
  finalText: "",
  hadError: false,
  heard: false,

  start(mode = "manual") {
    if (!SpeechRecognition) {
      if (mode === "manual") {
        addMessage("error", "This browser can't do speech recognition. Use Chrome or Edge, or type your message.");
      }
      return;
    }
    this.abort();
    if (mode === "manual") interrupt();

    const rec = new SpeechRecognition();
    rec.lang = "en-US";
    rec.interimResults = true; // show words as you speak
    rec.continuous = false;    // stop automatically after a pause
    this.rec = rec;
    this.mode = mode;
    this.finalText = "";
    this.hadError = false;
    this.heard = mode === "manual";

    rec.onresult = (e) => {
      if (rec !== this.rec) return;
      let final = "";
      let interim = "";
      for (const r of e.results) {
        if (r.isFinal) final += r[0].transcript;
        else interim += r[0].transcript;
      }
      const text = (final + interim).trim();
      if (!this.heard) {
        if (isEcho(text)) return;
        // The user started talking over the presenter.
        this.heard = true;
        interrupt();
        this.setButton(true);
        setPhase("listening");
      }
      this.finalText = final;
      $("typed").value = text;
    };

    rec.onerror = (e) => {
      if (rec !== this.rec) return;
      this.hadError = true;
      if (!this.heard || e.error === "aborted") return; // barge listening ends quietly
      const messages = {
        "not-allowed": "Microphone access is blocked. Allow it in your browser's site settings, then try again.",
        "no-speech": "I didn't hear anything. Press Speak and try again.",
        "network": "Speech recognition needs an internet connection in this browser.",
        "audio-capture": "No microphone was found. Plug one in or type your message.",
      };
      addMessage("error", messages[e.error] ?? `Speech recognition stopped: ${e.error}`);
    };

    rec.onend = () => {
      if (rec !== this.rec) return; // replaced or aborted
      const heard = this.heard;
      this.rec = null;
      this.mode = null;
      this.setButton(false);

      if (!heard) {
        // Recognition times out after a few silent seconds; keep listening while the presenter talks.
        if (state.utterance && voiceInterruptOn()) this.start("barge");
        return;
      }

      // Stopping early can leave words only as interim results; the text box holds final + interim.
      const text = this.finalText.trim() || $("typed").value.trim();
      $("typed").value = "";
      if (!text) {
        if (!this.hadError) addMessage("error", "No words were recognised. Speak a little louder, or type your message.");
        return setPhase("idle");
      }
      if (isStopWord(text)) return setPhase("idle"); // "stop" only interrupts
      sendTurn({ type: "text", text });
      setPhase("thinking");
    };

    try {
      rec.start();
    } catch (err) {
      this.rec = null;
      this.mode = null;
      if (mode === "manual") addMessage("error", `Couldn't start the microphone: ${err.message}`);
      return;
    }
    if (mode === "manual") {
      this.setButton(true);
      setPhase("listening");
    }
  },

  stop() {
    this.rec?.stop(); // onend sends whatever was recognised
  },

  // Ends recognition without sending anything.
  abort() {
    const rec = this.rec;
    this.rec = null;
    this.mode = null;
    this.setButton(false);
    rec?.abort();
  },

  setButton(on) {
    this.isRecording = on;
    $("mic-btn").classList.toggle("mic--on", on);
    $("mic-btn").setAttribute("aria-pressed", String(on));
    $("mic-label").textContent = on ? "Stop and send" : "Speak";
    renderStatus();
  },
};

// ---------- user actions ----------
function navigate(action, slide) {
  recorder.abort();
  interrupt();
  sendTurn({ type: "navigate", action, slide });
}

$("mic-btn").addEventListener("click", () => (recorder.isRecording ? recorder.stop() : recorder.start("manual")));
$("stop-btn").addEventListener("click", stopEverything);
$("prev-btn").addEventListener("click", () => navigate("previous"));
$("next-btn").addEventListener("click", () => navigate("next"));

$("typed").addEventListener("input", renderStatus);
$("type-form").addEventListener("submit", (e) => {
  e.preventDefault();
  const text = $("typed").value.trim();
  if (!text || $("send-btn").disabled) return;
  recorder.abort();
  interrupt();
  $("typed").value = "";
  if (isStopWord(text)) return setPhase("idle");
  sendTurn({ type: "text", text });
  setPhase("thinking");
});

document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") return stopEverything();
  if (e.target.tagName === "INPUT") return;
  if (e.key === "ArrowRight" || e.key === "PageDown") navigate("next");
  if (e.key === "ArrowLeft" || e.key === "PageUp") navigate("previous");
});

// Remember the voice-interrupt choice in this browser.
try {
  $("barge-toggle").checked = localStorage.getItem("voiceInterrupt") === "on";
} catch { /* storage unavailable; default off */ }
$("barge-toggle").addEventListener("change", () => {
  try {
    localStorage.setItem("voiceInterrupt", voiceInterruptOn() ? "on" : "off");
  } catch { /* ignore */ }
  if (voiceInterruptOn() && state.utterance) recorder.start("barge");
  if (!voiceInterruptOn() && recorder.mode === "barge" && !recorder.heard) recorder.abort();
});

// ---------- start ----------
fetch("/api/slides")
  .then((res) => {
    if (!res.ok) throw new Error(`Couldn't load slides (HTTP ${res.status}). Is the server running?`);
    return res.json();
  })
  .then((deck) => {
    state.deck = deck;
    $("deck-title").textContent = deck.title;
    renderSlide();
  })
  .catch((err) => addMessage("error", err.message));

renderStatus();
socket.connect();
