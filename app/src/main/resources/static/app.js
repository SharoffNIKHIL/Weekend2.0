// app/src/main/resources/static/app.js — Weekend PWA client (UI v2). No third-party scripts; talks only to this origin.
// Model output and stored text are always rendered with textContent, never as HTML.
"use strict";

const TZ = "Asia/Kolkata";
const USD_TO_INR = 96.12; // display-only estimate; same FX as the project tracker (2026-10-02)
const DELETE_PHRASE = "DELETE ALL MY DATA";
const VIEWS = ["chat", "memories", "reminders", "settings"];
const KINDS = { FACT: ["Fact", "accent"], PREFERENCE: ["Preference", "ok"], TASK: ["Task", "warn"] };

const state = { conversationId: null, costUsd: 0, memories: [], memoryFilter: "all", info: null };
const $ = (id) => document.getElementById(id);

// ---------- small DOM helpers ----------
function el(tag, props = {}, children = []) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === "class") node.className = v;
    else if (k === "text") node.textContent = v;
    else if (k.startsWith("on")) node.addEventListener(k.slice(2), v);
    else node.setAttribute(k, v);
  }
  for (const c of [].concat(children)) if (c) node.append(c);
  return node;
}

function icon(name, cls = "ic") {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("class", cls);
  svg.setAttribute("aria-hidden", "true");
  const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
  use.setAttribute("href", "#i-" + name);
  svg.append(use);
  return svg;
}

function iconButton(name, label, onclick, extra = "") {
  return el("button", { class: "icon-btn " + extra, "aria-label": label, title: label, type: "button", onclick }, icon(name, "ic sm"));
}

function tag(text, kind = "", iconName = null) {
  return el("span", { class: "tag " + kind }, [iconName ? icon(iconName) : null, text]);
}

function toast(text, kind = "") {
  const t = el("div", { class: "toast " + kind, text });
  $("toasts").append(t);
  setTimeout(() => t.remove(), 4000);
}

function confirmDialog(title, text, okLabel = "Delete") {
  const d = $("confirm");
  $("confirm-title").textContent = title;
  $("confirm-text").textContent = text;
  $("confirm-ok").textContent = okLabel;
  return new Promise((resolve) => {
    d.addEventListener("close", () => resolve(d.returnValue === "ok"), { once: true });
    d.showModal();
  });
}

// ---------- formatting (IST) ----------
const fmtDate = new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeZone: TZ });
const fmtTime = new Intl.DateTimeFormat("en-IN", { hour: "numeric", minute: "2-digit", timeZone: TZ });
const fmtDay = new Intl.DateTimeFormat("en-IN", { day: "numeric", timeZone: TZ });
const fmtMon = new Intl.DateTimeFormat("en-IN", { month: "short", timeZone: TZ });
const dayKey = (d) => new Intl.DateTimeFormat("en-CA", { timeZone: TZ }).format(d); // YYYY-MM-DD in IST

function relativeDay(date) {
  const diff = Math.round((Date.parse(dayKey(date)) - Date.parse(dayKey(new Date()))) / 86400000);
  if (diff === 0) return "Today";
  if (diff === 1) return "Tomorrow";
  if (diff === -1) return "Yesterday";
  if (diff > 1 && diff < 7) return new Intl.DateTimeFormat("en-IN", { weekday: "long", timeZone: TZ }).format(date);
  return fmtDate.format(date);
}

function money(usd) {
  const inr = usd * USD_TO_INR;
  return "₹" + inr.toFixed(inr < 1 ? 2 : 0) + " · $" + usd.toFixed(usd < 0.01 ? 4 : 2);
}

function greeting() {
  const h = Number(new Intl.DateTimeFormat("en-GB", { hour: "numeric", hourCycle: "h23", timeZone: TZ }).format(new Date()));
  return h < 5 ? "Working late?" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
}

// ---------- API ----------
function token() {
  try { return sessionStorage.getItem("weekend.token") || ""; } catch { return ""; }
}

async function api(path, options = {}) {
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  const t = token();
  if (t) headers.Authorization = "Bearer " + t;
  const res = await fetch(path, { ...options, headers, credentials: "same-origin" });
  if (res.status === 401) throw new Error("Not signed in: add your session token in Settings.");
  if (res.status === 429) throw new Error((await res.json()).error || "Daily cost cap reached.");
  if (!res.ok && res.status !== 204) throw new Error("Request failed (" + res.status + ")");
  return res.status === 204 ? null : res.json();
}

async function checkHealth() {
  const s = $("status");
  try {
    const r = await fetch("/healthz", { cache: "no-store" });
    if (!r.ok) throw new Error();
    s.className = "status ok";
    s.lastElementChild.textContent = "Online";
  } catch {
    s.className = "status down";
    s.lastElementChild.textContent = "Offline";
  }
}

// ---------- chat ----------
function scrollChat() {
  const box = $("chat-scroll");
  box.scrollTop = box.scrollHeight;
}

function messageRow(role, children) {
  const avatar = role === "assistant" ? el("div", { class: "avatar" }, el("img", { src: "icon.svg", alt: "" })) : null;
  const li = el("li", { class: "msg " + role }, [avatar, el("div", { class: "body" }, children)]);
  $("empty-chat").hidden = true;
  $("messages").append(li);
  scrollChat();
  return li;
}

function addUser(text) {
  messageRow("user", el("div", { class: "text", text }));
}

function addAssistant(text, metaTags = []) {
  const copy = iconButton("copy", "Copy reply", async () => {
    try { await navigator.clipboard.writeText(text); toast("Copied"); } catch { toast("Copy not allowed here", "error"); }
  });
  return messageRow("assistant", [el("div", { class: "text", text }), el("div", { class: "meta" }, [...metaTags, copy])]);
}

function pendingCard(p) {
  const card = el("div", { class: "confirm-card" });
  const decide = async (approved) => {
    card.querySelectorAll("button").forEach((b) => (b.disabled = true));
    try {
      const r = await api("/api/confirm/" + encodeURIComponent(p.id), { method: "POST", body: JSON.stringify({ approved }) });
      card.classList.add("done");
      card.querySelector("h3").lastChild.textContent = approved ? "Approved" : "Declined";
      card.querySelector(".row").replaceWith(el("p", { class: "hint", text: r.result }));
      refreshCounts();
    } catch (e) {
      card.querySelectorAll("button").forEach((b) => (b.disabled = false));
      toast(e.message, "error");
    }
  };
  card.append(
    el("h3", {}, [icon("shield", "ic sm"), "Needs your confirmation"]),
    el("p", { text: p.summary }),
    el("div", { class: "tool", text: "Tool: " + p.tool }),
    el("div", { class: "row" }, [
      el("button", { class: "btn primary", type: "button", onclick: () => decide(true) }, [icon("check", "ic sm"), "Yes, do it"]),
      el("button", { class: "btn", type: "button", onclick: () => decide(false) }, [icon("x", "ic sm"), "No"]),
    ]),
  );
  return card;
}

async function send(text) {
  text = (text || "").trim();
  if (!text) return;
  const input = $("input");
  input.value = "";
  autosize();
  addUser(text);
  const typing = messageRow("assistant", el("div", { class: "typing", "aria-label": "Weekend is thinking" }, [el("i"), el("i"), el("i")]));
  $("send").disabled = true;
  try {
    const r = await api("/api/chat", {
      method: "POST",
      body: JSON.stringify({ conversationId: state.conversationId, message: text, thinkHarder: $("think").checked }),
    });
    state.conversationId = r.conversationId;
    typing.remove();
    const tags = [tag(r.model, "accent", "sparkle")];
    r.toolsUsed.forEach((t) => tags.push(tag(t, "", "tool")));
    if (r.memorySaved) tags.push(tag("memory saved", "ok", "memory"));
    const cost = Number(r.costUsd || 0);
    if (cost > 0) tags.push(tag(money(cost)));
    const p = r.pendingConfirmation;
    const reply = p && r.reply.trim() === p.summary.trim() ? "I need your OK before I do this." : r.reply;
    const row = addAssistant(reply, tags);
    if (p) row.querySelector(".body").append(pendingCard(p));
    state.costUsd += cost;
    $("cost-pill").hidden = state.costUsd <= 0;
    $("cost-pill").textContent = "This tab: " + money(state.costUsd);
    if (r.memorySaved) refreshCounts();
    scrollChat();
  } catch (e) {
    typing.remove();
    toast(e.message, "error");
  } finally {
    $("send").disabled = false;
    input.focus();
  }
}

function newChat() {
  state.conversationId = null;
  $("messages").replaceChildren();
  $("empty-chat").hidden = false;
  $("input").focus();
}

function autosize() {
  const t = $("input");
  t.style.height = "auto";
  t.style.height = t.scrollHeight + "px";
}

// ---------- memories ----------
async function loadMemories() {
  state.memories = await api("/api/memories");
  setCount("memories", state.memories.length);
  renderMemories();
}

function renderMemories() {
  const q = $("memory-search").value.trim().toLowerCase();
  const f = state.memoryFilter;
  const items = state.memories
    .filter((m) => f === "all" || (f === "pinned" ? m.pinned : m.kind === f))
    .filter((m) => !q || m.text.toLowerCase().includes(q))
    .sort((a, b) => (b.pinned - a.pinned) || b.createdAt.localeCompare(a.createdAt));
  const list = $("memory-list");
  list.replaceChildren();
  if (!items.length) {
    list.append(el("li", { class: "empty", text: state.memories.length ? "No memories match." : "Nothing remembered yet. Say \"remember that …\" in chat." }));
    return;
  }
  items.forEach((m) => {
    const [label, kind] = KINDS[m.kind] || [m.kind, ""];
    const pin = iconButton("pin", m.pinned ? "Unpin" : "Pin", async () => {
      try {
        await api("/api/memories/" + encodeURIComponent(m.id) + "/pin", { method: "POST", body: JSON.stringify({ pinned: !m.pinned }) });
        await loadMemories();
      } catch (e) { toast(e.message, "error"); }
    }, m.pinned ? "on" : "");
    const del = iconButton("trash", "Delete", async () => {
      if (!(await confirmDialog("Delete this memory?", "“" + m.text + "” will be deleted permanently."))) return;
      try { await api("/api/memories/" + encodeURIComponent(m.id), { method: "DELETE" }); toast("Memory deleted"); await loadMemories(); }
      catch (e) { toast(e.message, "error"); }
    }, "danger");
    const foot = m.pinned
      ? [icon("pin", "ic sm"), "Pinned · kept until you delete it"]
      : [icon("clock", "ic sm"), "Saved " + fmtDate.format(new Date(m.createdAt)) + (m.expiresAt ? " · expires " + fmtDate.format(new Date(m.expiresAt)) : "")];
    list.append(el("li", { class: "mem" + (m.pinned ? " pinned" : "") }, [
      el("div", { class: "top" }, [tag(label, kind), m.pinned ? tag("Pinned", "accent", "pin") : null, el("div", { class: "actions" }, [pin, del])]),
      el("div", { class: "text", text: m.text }),
      el("div", { class: "foot" }, foot),
    ]));
  });
}

// ---------- reminders ----------
async function loadReminders() {
  const items = await api("/api/reminders");
  const upcoming = items.filter((r) => r.status === "SCHEDULED").sort((a, b) => a.dueAt.localeCompare(b.dueAt));
  const past = items.filter((r) => r.status !== "SCHEDULED").sort((a, b) => b.dueAt.localeCompare(a.dueAt));
  setCount("reminders", upcoming.length);
  const row = (r, isPast) => {
    const due = new Date(r.dueAt);
    const status = { SCHEDULED: ["Scheduled", "accent"], DELIVERED: ["Delivered", "ok"], CANCELLED: ["Cancelled", ""] }[r.status] || [r.status, ""];
    const cancel = isPast ? null : el("button", {
      class: "btn ghost", type: "button",
      onclick: async () => {
        if (!(await confirmDialog("Cancel this reminder?", "“" + r.text + "” won't be sent.", "Cancel reminder"))) return;
        try { await api("/api/reminders/" + encodeURIComponent(r.id), { method: "DELETE" }); toast("Reminder cancelled"); loadReminders(); }
        catch (e) { toast(e.message, "error"); }
      },
    }, "Cancel");
    return el("li", { class: "rem" + (isPast ? " past" : "") }, [
      el("div", { class: "date" }, [el("b", { text: fmtDay.format(due) }), el("small", { text: fmtMon.format(due) })]),
      el("div", { class: "what" }, [el("strong", { text: r.text }), el("span", { text: relativeDay(due) + ", " + fmtTime.format(due) + " IST" })]),
      tag(status[0], status[1]),
      cancel,
    ]);
  };
  const fill = (id, list, isPast, emptyText) => {
    $(id).replaceChildren(...(list.length ? list.map((r) => row(r, isPast)) : [el("li", { class: "empty", text: emptyText })]));
  };
  fill("reminders-upcoming", upcoming, false, "No upcoming reminders.");
  fill("reminders-past", past, true, "Nothing here yet.");
}

function setCount(name, n) {
  const b = $("count-" + name);
  b.hidden = !n;
  b.textContent = n;
}

function refreshCounts() {
  loadMemories().catch(() => {});
  loadReminders().catch(() => {});
}

// ---------- settings ----------
async function loadInfo() {
  try {
    const i = await api("/api/info");
    state.info = i;
    $("info-model").textContent = i.modelDefault;
    $("info-strong").textContent = i.modelStrong;
    $("info-location").textContent = i.processingLocation;
    $("info-cap").textContent = money(Number(i.dailyCostCapUsd)) + " per day";
    $("info-flag").hidden = !i.dataLeavesIndia;
    document.querySelectorAll("[data-ret]").forEach((s) => { s.textContent = i.retentionDays[s.dataset.ret]; });
    $("chat-sub").textContent = "Model: " + i.modelDefault + " · write actions always wait for your yes.";
  } catch { /* not signed in yet: keep defaults */ }
  checkHealth();
}

function showSessionState() {
  const has = !!token();
  $("session-state").className = "hint" + (has ? " ok" : "");
  $("session-state").textContent = has
    ? "Signed in for this tab. The token is cleared when you close the tab."
    : "Until passkey sign-in ships (Phase 2), paste your session token. It is kept in this tab only and cleared when you close it.";
}

function applyTheme(mode) {
  if (mode === "auto") delete document.documentElement.dataset.theme;
  else document.documentElement.dataset.theme = mode;
  try { mode === "auto" ? localStorage.removeItem("weekend.theme") : localStorage.setItem("weekend.theme", mode); } catch { /* per-device convenience only */ }
  document.querySelectorAll("[data-theme-set]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.themeSet === mode)));
}

async function exportData() {
  try {
    const data = await api("/api/export");
    const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }));
    el("a", { href: url, download: "weekend-export-" + dayKey(new Date()) + ".json" }).click();
    URL.revokeObjectURL(url);
    toast("Export downloaded");
  } catch (e) {
    toast(e.message, "error");
  }
}

async function deleteAll(event) {
  event.preventDefault();
  if ($("delete-phrase").value !== DELETE_PHRASE) return;
  if (!(await confirmDialog("Delete everything?", "All conversations, memories, reminders and tool calls will be deleted. This can't be undone.", "Delete everything"))) return;
  try {
    await api("/api/delete-all", { method: "POST", body: JSON.stringify({ confirmation: $("delete-phrase").value }) });
    $("delete-phrase").value = "";
    $("delete-btn").disabled = true;
    newChat();
    refreshCounts();
    toast("All data deleted");
  } catch (e) {
    toast("Not deleted: " + e.message, "error");
  }
}

// ---------- navigation ----------
function showView(name) {
  if (!VIEWS.includes(name)) name = "chat";
  document.querySelectorAll(".nav button").forEach((b) => (b.dataset.view === name ? b.setAttribute("aria-current", "page") : b.removeAttribute("aria-current")));
  document.querySelectorAll(".view").forEach((v) => (v.hidden = v.id !== "view-" + name));
  const loaders = { memories: loadMemories, reminders: loadReminders, settings: loadInfo };
  if (loaders[name]) loaders[name]().catch((e) => toast(e.message, "error"));
  if (name === "chat") $("input").focus({ preventScroll: true });
}

document.addEventListener("DOMContentLoaded", () => {
  $("greeting").textContent = greeting();
  $("composer").addEventListener("submit", (e) => { e.preventDefault(); send($("input").value); });
  $("input").addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey && !e.isComposing) { e.preventDefault(); send($("input").value); }
  });
  $("input").addEventListener("input", autosize);
  document.querySelectorAll("#suggestions .chip").forEach((c) => c.addEventListener("click", () => send(c.dataset.prompt)));
  $("new-chat").addEventListener("click", newChat);

  $("memory-search").addEventListener("input", renderMemories);
  document.querySelectorAll("#memory-filter button").forEach((b) => b.addEventListener("click", () => {
    state.memoryFilter = b.dataset.filter;
    document.querySelectorAll("#memory-filter button").forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
    renderMemories();
  }));

  $("token-form").addEventListener("submit", (e) => {
    e.preventDefault();
    try { sessionStorage.setItem("weekend.token", $("token").value.trim()); toast("Session saved for this tab"); } catch { toast("Storage unavailable", "error"); }
    $("token").value = "";
    showSessionState();
    loadInfo();
    refreshCounts();
  });
  $("token-clear").addEventListener("click", () => {
    try { sessionStorage.removeItem("weekend.token"); } catch { /* nothing stored */ }
    showSessionState();
    toast("Signed out of this tab");
  });
  document.querySelectorAll("[data-theme-set]").forEach((b) => b.addEventListener("click", () => applyTheme(b.dataset.themeSet)));
  applyTheme(document.documentElement.dataset.theme || "auto");
  $("export").addEventListener("click", exportData);
  $("delete-phrase").addEventListener("input", (e) => ($("delete-btn").disabled = e.target.value !== DELETE_PHRASE));
  $("delete-form").addEventListener("submit", deleteAll);

  document.querySelectorAll(".nav button").forEach((b) => b.addEventListener("click", () => { location.hash = b.dataset.view; }));
  window.addEventListener("hashchange", () => showView(location.hash.slice(1)));
  showView(location.hash.slice(1));
  showSessionState();
  loadInfo();
  refreshCounts();
  setInterval(checkHealth, 60000);
  if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});
});
