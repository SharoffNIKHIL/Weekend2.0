// app/src/main/resources/static/companion.js — the helmet companion and the agent "Tune" panel.
// Loaded after app.js (shares its globals: state, $, el, icon, api, put, toast, setAgent, loadAgents).
// The helmet follows a mouse cursor; when the active agent runs at High/Max efficiency it flies to the chat box,
// switches to its serious face and shows FOCUS. Uses the private brand pack (/brand/) when present.
"use strict";

const MODE_INFO = {
  FUNNY: ["Funny", "smile", "Playful, light; memory only; asks before changes and anything external"],
  DISCIPLINED: ["Disciplined", "shield", "Facts only, terse, focus mode; asks before every tool"],
  WORK: ["Work", "work", "Accurate and on-task; web on; asks before changes and anything external"],
  BROWSE: ["Browse", "search", "Curious researcher; wide search; web searches pre-approved"],
};
const EFFICIENCY = ["", "Eco", "Balanced", "Standard", "High", "Max"];

/** Removes anything executable from an SVG before it is put into the page. */
function cleanSvg(text) {
  const doc = new DOMParser().parseFromString(text, "image/svg+xml");
  const svg = doc.documentElement;
  if (!svg || svg.nodeName !== "svg" || doc.querySelector("parsererror")) return null;
  svg.querySelectorAll("script, foreignObject, iframe, object, embed").forEach((n) => n.remove());
  svg.querySelectorAll("*").forEach((n) => [...n.attributes].forEach((a) => {
    if (/^on/i.test(a.name) || (/href$/i.test(a.name) && !a.value.startsWith("#"))) n.removeAttribute(a.name);
  }));
  return document.importNode(svg, true);
}

const Companion = {
  svgText: null, x: innerWidth - 120, y: innerHeight - 160, tx: innerWidth - 120, ty: innerHeight - 160,
  enabled: true, finePointer: matchMedia("(pointer: fine)").matches, reduced: matchMedia("(prefers-reduced-motion: reduce)").matches,

  async init() {
    try { this.enabled = localStorage.getItem("weekend.companion") !== "off"; } catch { /* default on */ }
    $("companion-toggle").checked = this.enabled;
    $("companion-toggle").addEventListener("change", (e) => {
      this.enabled = e.target.checked;
      try { localStorage.setItem("weekend.companion", this.enabled ? "on" : "off"); } catch { /* per-device only */ }
      this.sync();
    });
    await this.loadBrand();
    addEventListener("pointermove", (e) => {
      if (e.pointerType !== "mouse") return;
      const first = this.mx === undefined;
      this.finePointer = true;
      this.mx = e.clientX;
      this.my = e.clientY;
      if (!this.docked) { this.tx = e.clientX + 22; this.ty = e.clientY + 18; }
      if (first) { this.x = this.tx; this.y = this.ty; this.sync(); } // appear next to the cursor on the first move
    }, { passive: true });
    addEventListener("resize", () => this.sync());
    const tick = () => {
      const k = this.reduced ? 1 : 0.14;
      this.x += (this.tx - this.x) * k;
      this.y += (this.ty - this.y) * k;
      const tilt = Math.max(-14, Math.min(14, (this.tx - this.x) * 0.25));
      $("companion").style.transform = `translate(${this.x.toFixed(1)}px, ${this.y.toFixed(1)}px) rotate(${tilt.toFixed(1)}deg)`;
      requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
    this.sync();
  },

  /** Prefers the private brand pack (when /brand.json says it exists); falls back to the public assets. */
  async loadBrand() {
    let brand = {};
    try { const r = await fetch("/brand.json", { cache: "no-cache" }); if (r.ok) brand = await r.json(); } catch { /* public assets */ }
    for (const url of [brand.companion ? "/brand/companion.svg" : null, "companion.svg"].filter(Boolean)) {
      try {
        const r = await fetch(url, { cache: "no-cache" });
        if (r.ok && (r.headers.get("content-type") || "").includes("svg")) { this.svgText = await r.text(); break; }
      } catch { /* try the next one */ }
    }
    if (brand.logo) {
      state.logoSrc = "/brand/logo.svg";
      document.querySelectorAll('img[data-brand="logo"], .msg .avatar img').forEach((i) => (i.src = state.logoSrc));
    }
    if (brand.icon) {
      const ico = document.querySelector('link[rel="icon"]');
      if (ico) ico.href = "/brand/icon.svg";
    }
    const svg = this.svgText && cleanSvg(this.svgText);
    if (svg) $("companion").querySelector(".bob").replaceChildren(svg);
  },

  /** A fresh copy of the helmet for previews (moods switch with a class on the wrapper). */
  clone() {
    return this.svgText ? cleanSvg(this.svgText.replace(/\b(wk[bp])-/g, "$1p-").replace(/#(wk[bp])-/g, "#$1p-")) : null;
  },

  sync() {
    const c = $("companion");
    const agent = state.catalog.agents.find((a) => a.id === state.agentId);
    const mood = (agent && agent.mood ? agent.mood : "CALM").toLowerCase();
    c.classList.remove("mood-happy", "mood-calm", "mood-serious", "mood-curious");
    c.classList.add("mood-" + mood);
    const view = (location.hash.slice(1) || "home").split("/")[0];
    const focus = !!(agent && agent.focusMode);
    this.docked = focus && view === "chat";
    c.classList.toggle("docked", this.docked);
    if (this.docked) {
      const r = $("composer").getBoundingClientRect();
      this.tx = r.right - 64;
      this.ty = r.top - 74;
    } else if (this.mx !== undefined) {
      this.tx = this.mx + 22;
      this.ty = this.my + 18;
    }
    // Focus mode away from chat: the helmet leaves for the chat box. Otherwise it follows a mouse, if switched on.
    const visible = this.docked || (!focus && this.enabled && this.finePointer && this.mx !== undefined);
    c.classList.toggle("away", !visible);
    const eff = $("chat-efficiency");
    if (agent && eff) {
      eff.value = agent.persona.efficiency;
      eff.disabled = agent.kind === "REMOTE";
      $("chat-efficiency-label").textContent = agent.kind === "REMOTE" ? "n/a" : EFFICIENCY[agent.persona.efficiency];
      eff.closest(".eff-pick").classList.toggle("focus", focus);
      fill(eff);
    }
  },
};

function fill(range) {
  const min = Number(range.min), max = Number(range.max);
  range.style.setProperty("--fill", ((Number(range.value) - min) / (max - min)) * 100 + "%");
}

async function savePersona(agentId, persona) {
  const view = await put("/api/agents/" + encodeURIComponent(agentId) + "/persona", persona);
  const i = state.catalog.agents.findIndex((a) => a.id === agentId);
  if (i >= 0) state.catalog.agents[i] = view;
  return view;
}

// ---------- Tune panel ----------
const Tune = {
  agentId: null, draft: null,

  init() {
    ["humor", "truth", "focus", "efficiency"].forEach((k) => $("s-" + k).addEventListener("input", (e) => {
      this.draft[k] = Number(e.target.value);
      this.draft.mode = this.matchingMode();
      this.paint();
    }));
    ["search", "approval"].forEach((k) => $("s-" + k).addEventListener("change", (e) => {
      this.draft[k] = e.target.value;
      this.draft.mode = this.matchingMode();
      this.paint();
    }));
    $("tune-agent").addEventListener("change", (e) => this.select(e.target.value));
    $("tune-reset").addEventListener("click", () => this.applyMode(this.draft.mode === "CUSTOM" ? "WORK" : this.draft.mode));
    $("tune-save").addEventListener("click", async () => {
      try {
        const v = await savePersona(this.agentId, this.draft);
        toast(v.name + ": " + (MODE_INFO[v.persona.mode] ? MODE_INFO[v.persona.mode][0] : "Custom") + " mode, " + EFFICIENCY[v.persona.efficiency] + " efficiency saved");
        await loadAgents();
      } catch (e) { toast(e.message, "error"); }
    });
  },

  render() {
    const tunable = state.catalog.agents.filter((a) => a.kind !== "REMOTE");
    if (!tunable.length) return;
    const sel = $("tune-agent");
    sel.replaceChildren(...tunable.map((a) => el("option", { value: a.id, text: a.name })));
    const keep = tunable.some((a) => a.id === this.agentId) ? this.agentId : (tunable.some((a) => a.id === state.agentId) ? state.agentId : tunable[0].id);
    $("tune-modes").replaceChildren(...state.catalog.modes.map((m) => {
      const [label, ic, hint] = MODE_INFO[m.mode] || [m.mode, "sparkle", ""];
      return el("button", { class: "mode", type: "button", "data-mode": m.mode, "aria-pressed": "false", onclick: () => this.applyMode(m.mode) },
        [icon(ic), el("strong", { text: label }), el("small", { text: hint })]);
    }));
    $("web-off").hidden = state.catalog.webAllowed;
    this.select(keep);
  },

  select(id) {
    this.agentId = id;
    $("tune-agent").value = id;
    const a = state.catalog.agents.find((x) => x.id === id);
    this.draft = { ...a.persona };
    this.paint();
  },

  applyMode(mode) {
    const m = state.catalog.modes.find((x) => x.mode === mode);
    if (m) this.draft = { ...m.persona };
    this.paint();
  },

  matchingMode() {
    const keys = ["humor", "truth", "focus", "efficiency", "search", "approval"];
    const m = state.catalog.modes.find((x) => keys.every((k) => x.persona[k] === this.draft[k]));
    return m ? m.mode : "CUSTOM";
  },

  mood() {
    if (this.draft.efficiency >= 4) return "serious";
    return { FUNNY: "happy", DISCIPLINED: "serious", BROWSE: "curious" }[this.draft.mode] || "calm";
  },

  paint() {
    const d = this.draft;
    ["humor", "truth", "focus", "efficiency"].forEach((k) => {
      const r = $("s-" + k);
      r.value = d[k];
      fill(r);
      $("v-" + k).textContent = k === "efficiency" ? EFFICIENCY[d[k]] : d[k] + "/10";
    });
    $("s-search").value = d.search;
    $("s-approval").value = d.approval;
    document.querySelectorAll("#tune-modes .mode").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.mode === d.mode)));
    const budget = [null, ["standard model", 2, 512, 4], ["standard model", 4, 1024, 6], ["standard model", 5, 1536, 8],
      ["strong model", 8, 2048, 12], ["strong model", 10, 4096, 16]][d.efficiency];
    $("efficiency-hint").textContent = budget[0] + " · up to " + budget[1] + " tool steps · answers up to " + budget[2] + " tokens · "
      + budget[3] + " memories" + (d.efficiency >= 4 ? " · focus mode (costs more)" : "");
    const preview = $("tune-preview");
    if (!preview.firstChild && Companion.svgText) { const svg = Companion.clone(); if (svg) preview.append(svg); }
    preview.className = "tune-preview mood-" + this.mood();
    const label = MODE_INFO[d.mode] ? MODE_INFO[d.mode][0] : "Custom";
    $("tune-summary").textContent = label + " mode · " + EFFICIENCY[d.efficiency] + " efficiency · mood: " + this.mood();
  },
};

document.addEventListener("DOMContentLoaded", () => {
  Tune.init();
  Companion.init();
  $("chat-efficiency").addEventListener("input", (e) => {
    fill(e.target);
    $("chat-efficiency-label").textContent = EFFICIENCY[Number(e.target.value)];
  });
  $("chat-efficiency").addEventListener("change", async (e) => {
    const a = state.catalog.agents.find((x) => x.id === state.agentId);
    if (!a || a.kind === "REMOTE") return;
    const efficiency = Number(e.target.value);
    const persona = { ...a.persona, efficiency };
    const preset = state.catalog.modes.find((m) => m.mode === a.persona.mode);
    if (preset && preset.persona.efficiency !== efficiency) persona.mode = "CUSTOM";
    try {
      await savePersona(a.id, persona);
      Companion.sync();
      if (efficiency >= 4) toast("Focus mode on: " + EFFICIENCY[efficiency] + " efficiency, stronger model");
    } catch (err) { toast(err.message, "error"); }
  });
  addEventListener("hashchange", () => setTimeout(() => Companion.sync(), 50));
});
