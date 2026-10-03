// app/src/main/resources/static/app.js — Weekend PWA client. No third-party scripts; talks only to this origin.
"use strict";

const state = { conversationId: null, pendingId: null };
const $ = (id) => document.getElementById(id);

function token() {
  try { return sessionStorage.getItem("weekend.token") || ""; } catch { return ""; }
}

async function api(path, options = {}) {
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  const t = token();
  if (t) headers.Authorization = "Bearer " + t;
  const res = await fetch(path, { ...options, headers, credentials: "same-origin" });
  if (res.status === 401) throw new Error("Not signed in: add your session token in Data → Session.");
  if (res.status === 429) throw new Error((await res.json()).error);
  if (!res.ok && res.status !== 204) throw new Error("Request failed (" + res.status + ")");
  return res.status === 204 ? null : res.json();
}

function toast(text) {
  const el = $("toast");
  el.textContent = text;
  el.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (el.hidden = true), 4000);
}

function addMessage(role, text, meta) {
  const li = document.createElement("li");
  li.className = "msg " + role;
  li.textContent = text; // textContent: never interpret model output as HTML
  if (meta) {
    const m = document.createElement("span");
    m.className = "meta";
    m.textContent = meta;
    li.appendChild(m);
  }
  $("messages").appendChild(li);
  li.scrollIntoView({ block: "end" });
  return li;
}

function showPending(p) {
  state.pendingId = p ? p.id : null;
  $("pending").hidden = !p;
  if (p) $("pending-text").textContent = p.summary;
}

async function send(event) {
  event.preventDefault();
  const input = $("input");
  const text = input.value.trim();
  if (!text) return;
  input.value = "";
  addMessage("user", text);
  const thinking = addMessage("assistant thinking", "Thinking…");
  $("send").disabled = true;
  try {
    const r = await api("/api/chat", {
      method: "POST",
      body: JSON.stringify({ conversationId: state.conversationId, message: text, thinkHarder: $("think").checked }),
    });
    state.conversationId = r.conversationId;
    thinking.remove();
    const tools = r.toolsUsed.length ? " · tools: " + r.toolsUsed.join(", ") : "";
    addMessage("assistant", r.reply, r.model + tools + (r.memorySaved ? " · memory saved" : ""));
    showPending(r.pendingConfirmation);
  } catch (e) {
    thinking.remove();
    toast(e.message);
  } finally {
    $("send").disabled = false;
    input.focus();
  }
}

async function answerPending(approved) {
  if (!state.pendingId) return;
  try {
    const r = await api("/api/confirm/" + encodeURIComponent(state.pendingId), {
      method: "POST", body: JSON.stringify({ approved }),
    });
    addMessage("assistant", r.result);
  } catch (e) {
    toast(e.message);
  }
  showPending(null);
}

function listItem(text, actions) {
  const li = document.createElement("li");
  const span = document.createElement("span");
  span.textContent = text;
  li.appendChild(span);
  actions.forEach(([label, fn]) => {
    const b = document.createElement("button");
    b.textContent = label;
    b.addEventListener("click", fn);
    li.appendChild(b);
  });
  return li;
}

async function loadMemories() {
  const list = $("memory-list");
  list.replaceChildren();
  const items = await api("/api/memories");
  if (!items.length) list.appendChild(listItem("No memories yet.", []));
  items.forEach((m) => list.appendChild(listItem((m.pinned ? "📌 " : "") + m.text, [
    [m.pinned ? "Unpin" : "Pin", async () => { await api("/api/memories/" + m.id + "/pin", { method: "POST", body: JSON.stringify({ pinned: !m.pinned }) }); loadMemories(); }],
    ["Delete", async () => { if (confirm("Delete this memory?")) { await api("/api/memories/" + m.id, { method: "DELETE" }); loadMemories(); } }],
  ])));
}

async function loadReminders() {
  const list = $("reminder-list");
  list.replaceChildren();
  const items = await api("/api/reminders");
  if (!items.length) list.appendChild(listItem("No reminders.", []));
  const fmt = new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Kolkata" });
  items.forEach((r) => list.appendChild(listItem(fmt.format(new Date(r.dueAt)) + " — " + r.text + " (" + r.status.toLowerCase() + ")",
    r.status === "SCHEDULED" ? [["Cancel", async () => { await api("/api/reminders/" + r.id, { method: "DELETE" }); loadReminders(); }]] : [])));
}

async function exportData() {
  try {
    const data = await api("/api/export");
    const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }));
    const a = Object.assign(document.createElement("a"), { href: url, download: "weekend-export.json" });
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    toast(e.message);
  }
}

async function deleteAll(event) {
  event.preventDefault();
  try {
    await api("/api/delete-all", { method: "POST", body: JSON.stringify({ confirmation: $("delete-phrase").value }) });
    toast("All data deleted.");
    $("messages").replaceChildren();
    state.conversationId = null;
  } catch (e) {
    toast("Not deleted: type the exact phrase.");
  }
}

function switchTab(name) {
  document.querySelectorAll(".tabs button").forEach((b) => b.setAttribute("aria-selected", String(b.dataset.tab === name)));
  document.querySelectorAll(".panel").forEach((p) => (p.hidden = p.id !== "panel-" + name));
  const loaders = { memories: loadMemories, reminders: loadReminders };
  if (loaders[name]) loaders[name]().catch((e) => toast(e.message));
}

document.addEventListener("DOMContentLoaded", () => {
  $("composer").addEventListener("submit", send);
  $("input").addEventListener("keydown", (e) => { if (e.key === "Enter" && !e.shiftKey) send(e); });
  $("pending-yes").addEventListener("click", () => answerPending(true));
  $("pending-no").addEventListener("click", () => answerPending(false));
  $("export").addEventListener("click", exportData);
  $("delete-form").addEventListener("submit", deleteAll);
  $("token-form").addEventListener("submit", (e) => {
    e.preventDefault();
    try { sessionStorage.setItem("weekend.token", $("token").value.trim()); toast("Session saved for this tab."); } catch { toast("Storage unavailable."); }
    $("token").value = "";
  });
  document.querySelectorAll(".tabs button").forEach((b) => b.addEventListener("click", () => switchTab(b.dataset.tab)));
  if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});
});
