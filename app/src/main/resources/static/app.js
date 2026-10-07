// app/src/main/resources/static/app.js — Weekend PWA client (UI v5: landing, features, images, art; helmet + Customise in companion.js). No third-party scripts; talks only to this origin.
// Model output, messages and stored text are always rendered with textContent, never as HTML.
"use strict";

const TZ = "Asia/Kolkata";
const USD_TO_INR = 96.12; // display-only estimate; same FX as the project tracker (2026-10-02)
const DELETE_PHRASE = "DELETE ALL MY DATA";
const VIEWS = ["home", "feature", "chat", "library", "day", "tasks", "reminders", "approvals", "payments", "messages", "notifications", "memories", "settings", "folder"];
const KINDS = { FACT: ["Fact", "accent"], PREFERENCE: ["Preference", ""], TASK: ["Task", ""] };
const FOLDER_ICONS = ["folder", "home", "work", "money", "health", "travel", "code", "cart", "star", "book"];
const NOTE_ICONS = { REMINDER: "bell", TASK: "task", APPROVAL: "approve", PAYMENT: "card", MESSAGE: "mail", SYSTEM: "sparkle" };

const state = {
  conversationId: null, costUsd: 0, memories: [], memoryFilter: "all", info: null,
  features: [], modes: [], featureId: "optimal", me: null, attachments: [], view: null,
  folders: [], taskFilter: "OPEN", folderId: null, logoSrc: "logo.svg",
};
const FEATURE_EXAMPLES = {
  studio: ["Make a 45 sec Short about the arctic fox", "Create 15 × 45 sec Shorts covering the whole of Kubernetes", "Build a 2 min video about black holes with voice and captions"],
  optimal: ["What should I focus on today?", "calculate 18% GST on 42,500", "Remind me to renew the passport"],
  hard: ["solve x^3 - 6x^2 + 11x - 6 = 0", "calculate (1 + 0.07)^30 * 250000", "stats 12 15 9 22 17 31"],
  smooth: ["Plan a relaxed Sunday for me", "Tell me something fun about Bengaluru", "Remember that I like filter coffee"],
  focused: ["Summarise my open tasks", "list my tasks", "calculate 3840 / 2160"],
  research: ["search compound interest", "Compare Cloud Run and GKE Autopilot for a small API", "search Indian Standard Time"],
  coding: ["Write a Python script that rotates log files", "Review this Terraform for drift risks", "calculate 2^16"],
  financial: ["my payments", "calculate 25000 * 12 * 1.07", "stats 2784 1840.5 25000"],
  designing: ["draw a clean dashboard wireframe", "Review the screenshot I attach for accessibility", "Suggest a colour palette for a calm app"],
  drawing: ["draw a sunset over the Western Ghats", "draw a night sky over a quiet lake", "paint misty mountains at dawn"],
  image: ["What is in this picture?", "Read the text in this screenshot", "draw this scene at night"],
  notes: ["notes weekly review", "save note Groceries: milk, eggs, filter coffee", "Turn my notes into tasks"],
};
const $ = (id) => document.getElementById(id);

// ---------- small DOM helpers ----------
function el(tag, props = {}, children = []) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (v === null || v === undefined || v === false) continue;
    if (k === "class") node.className = v;
    else if (k === "text") node.textContent = v;
    else if (k.startsWith("on")) node.addEventListener(k.slice(2), v);
    else node.setAttribute(k, v === true ? "" : v);
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

function button(label, onclick, cls = "btn", iconName = null) {
  return el("button", { class: cls, type: "button", onclick }, [iconName ? icon(iconName, "ic sm") : null, label]);
}

function tag(text, kind = "", iconName = null) {
  return el("span", { class: "tag " + kind }, [iconName ? icon(iconName) : null, text]);
}

function empty(text) {
  return el("li", { class: "empty", text });
}

function toast(text, kind = "") {
  const t = el("div", { class: "toast " + kind, text });
  const box = $("toasts");
  while (box.children.length >= 3) box.firstElementChild.remove(); // keep at most three on screen
  box.append(t);
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

/** A general dialog: body nodes, optional Save button. Resolves true when Save was pressed. */
function sheet(title, bodyNodes, okLabel = null) {
  const d = $("sheet");
  $("sheet-title").textContent = title;
  $("sheet-body").replaceChildren(...bodyNodes);
  $("sheet-ok").hidden = !okLabel;
  $("sheet-ok").textContent = okLabel || "Save";
  $("sheet-cancel").textContent = okLabel ? "Cancel" : "Close";
  d.returnValue = "";
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
const fmtInr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 });
const dayKey = (d) => new Intl.DateTimeFormat("en-CA", { timeZone: TZ }).format(d); // YYYY-MM-DD in IST

function relativeDay(date) {
  const diff = Math.round((Date.parse(dayKey(date)) - Date.parse(dayKey(new Date()))) / 86400000);
  if (diff === 0) return "Today";
  if (diff === 1) return "Tomorrow";
  if (diff === -1) return "Yesterday";
  if (diff > 1 && diff < 7) return new Intl.DateTimeFormat("en-IN", { weekday: "long", timeZone: TZ }).format(date);
  return fmtDate.format(date);
}

const when = (iso) => { const d = new Date(iso); return relativeDay(d) + ", " + fmtTime.format(d); };
const ago = (iso) => {
  const s = Math.max(0, (Date.now() - Date.parse(iso)) / 1000);
  if (s < 60) return "just now";
  if (s < 3600) return Math.floor(s / 60) + " min ago";
  if (s < 86400) return Math.floor(s / 3600) + " h ago";
  return when(iso);
};

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
  if (res.status === 204) return null;
  let body = null;
  try { body = await res.json(); } catch { /* empty or not JSON */ }
  if (!res.ok) throw new Error((body && body.error) || (res.status === 404 ? "Not found (it may have changed already)." : "Request failed (" + res.status + ")"));
  return body;
}

const post = (path, body) => api(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) });
const put = (path, body) => api(path, { method: "PUT", body: JSON.stringify(body) });
const del = (path) => api(path, { method: "DELETE" });

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

// ---------- counts and home ----------
function setCount(name, n) {
  document.querySelectorAll('[data-count="' + name + '"]').forEach((b) => { b.hidden = !n; b.textContent = n > 99 ? "99+" : n; });
  const tile = document.querySelector('[data-tile="' + name + '"]');
  if (tile) {
    tile.querySelector("b").textContent = n;
    if (name === "approvals") tile.classList.toggle("hot", n > 0);
  }
}

async function refreshCounts() {
  try {
    const h = await api("/api/home");
    Object.entries(h.counts).forEach(([k, v]) => setCount(k, v));
    state.folders = h.folders;
    fillFolderSelects();
    return h;
  } catch { return null; }
}

function folderIconName(key) {
  return FOLDER_ICONS.includes(key) ? key : "folder";
}

function folderName(id) {
  const f = state.folders.find((x) => x.folder.id === id);
  return f ? f.folder.name : null;
}

function fillFolderSelects() {
  ["task-folder", "reminder-folder"].forEach((id) => {
    const sel = $(id);
    const keep = sel.value;
    sel.replaceChildren(el("option", { value: "", text: "No folder" }),
      ...state.folders.map((f) => el("option", { value: f.folder.id, text: f.folder.name })));
    sel.value = state.folders.some((f) => f.folder.id === keep) ? keep : "";
  });
}

async function loadDay() {
  $("today").textContent = new Intl.DateTimeFormat("en-IN", { weekday: "long", day: "numeric", month: "long", timeZone: TZ }).format(new Date()) + " · IST";
  const h = await refreshCounts();
  if (!h) return;
  const list = $("folders");
  list.replaceChildren(...h.folders.map(folderTile), el("li", { class: "folder" },
    el("button", { class: "new", type: "button", onclick: newFolder, "aria-label": "New folder" },
      [el("span", { class: "folder-icon" }, icon("plus")), el("strong", { text: "New folder" })])));
  $("up-next").replaceChildren(...(h.upNext.length ? h.upNext.map((u) => el("li", { class: "row-card clickable",
    onclick: () => (location.hash = u.folderId ? "folder/" + u.folderId : u.type === "task" ? "tasks" : "reminders") }, [
    el("span", { class: "lead" }, icon(u.type === "task" ? "task" : "bell")),
    el("div", { class: "main" }, [el("strong", { text: u.title }), el("span", { text: when(u.dueAt) + " IST" + (u.folderId && folderName(u.folderId) ? " · " + folderName(u.folderId) : "") })]),
    tag(u.type === "task" ? "Task" : "Reminder", "accent"),
  ])) : [empty("Nothing due. Enjoy the weekend.")]));
}

function folderTile(s) {
  const slots = s.preview.slice(0, 4).map((t) => el("span", { class: "mini", text: t }));
  while (slots.length < 4) slots.push(el("span", { class: "mini empty-slot" }));
  const count = s.openTasks + s.upcomingReminders;
  const parts = [];
  if (s.openTasks) parts.push(s.openTasks + (s.openTasks === 1 ? " task" : " tasks"));
  if (s.upcomingReminders) parts.push(s.upcomingReminders + (s.upcomingReminders === 1 ? " reminder" : " reminders"));
  return el("li", { class: "folder" }, el("a", { href: "#folder/" + s.folder.id, "aria-label": s.folder.name + ", " + (parts.join(", ") || "empty") }, [
    el("span", { class: "folder-icon" }, [...slots, el("span", { class: "glyph" }, icon(folderIconName(s.folder.icon))),
      count ? el("span", { class: "badge", text: String(count) }) : null]),
    el("strong", { text: s.folder.name }),
    el("small", { text: parts.join(" · ") || "Empty" }),
  ]));
}

function iconPicker(selected) {
  let value = selected || "folder";
  const wrap = el("div", { class: "icon-picker", role: "group", "aria-label": "Folder icon" });
  FOLDER_ICONS.forEach((k) => {
    const b = el("button", { type: "button", "aria-pressed": String(k === value), "aria-label": k, title: k, onclick: () => {
      value = k;
      wrap.querySelectorAll("button").forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
    } }, icon(k));
    wrap.append(b);
  });
  return { node: wrap, value: () => value };
}

async function folderSheet(title, folder) {
  const name = el("input", { maxlength: "60", placeholder: "Folder name", "aria-label": "Folder name", id: "folder-name", value: folder ? folder.name : "" });
  const picker = iconPicker(folder && folder.icon);
  setTimeout(() => name.focus(), 50);
  if (!(await sheet(title, [name, picker.node], folder ? "Save" : "Create"))) return null;
  return { name: name.value.trim(), icon: picker.value() };
}

async function newFolder() {
  const f = await folderSheet("New folder");
  if (!f) return;
  try { await post("/api/folders", f); toast("Folder created"); loadDay(); } catch (e) { toast(e.message, "error"); }
}

// ---------- folder view ----------
async function loadFolder(id) {
  state.folderId = id;
  await refreshCounts();
  const c = await api("/api/folders/" + encodeURIComponent(id));
  $("h-folder").textContent = c.folder.name;
  $("folder-badge").replaceChildren(icon(folderIconName(c.folder.icon)));
  const openTasks = c.tasks.filter((t) => t.status === "OPEN").length;
  const upcoming = c.reminders.filter((r) => r.status === "SCHEDULED").length;
  $("folder-sub").textContent = openTasks + " open tasks · " + upcoming + " upcoming reminders";
  $("folder-tasks").replaceChildren(...(c.tasks.length ? c.tasks.map((t) => taskRow(t, () => loadFolder(id))) : [empty("No tasks in this folder.")]));
  $("folder-reminders").replaceChildren(...(c.reminders.length ? c.reminders.map((r) => reminderRow(r, () => loadFolder(id))) : [empty("No reminders in this folder.")]));
}

// ---------- tasks ----------
function taskRow(t, reload) {
  const done = t.status === "DONE";
  const meta = [];
  if (t.dueAt) meta.push("Due " + when(t.dueAt));
  if (t.folderId && folderName(t.folderId)) meta.push(folderName(t.folderId));
  if (done && t.completedAt) meta.push("Done " + ago(t.completedAt));
  return el("li", { class: "row-card" + (done ? " done" : "") }, [
    el("button", { class: "check" + (done ? " on" : ""), type: "button", "aria-label": done ? "Mark as not done" : "Mark as done", onclick: async () => {
      try { await post("/api/tasks/" + encodeURIComponent(t.id) + (done ? "/reopen" : "/complete")); reload(); refreshCounts(); } catch (e) { toast(e.message, "error"); }
    } }, icon("check")),
    el("div", { class: "main" }, [el("strong", { text: t.title }), meta.length ? el("span", { text: meta.join(" · ") }) : null]),
    el("div", { class: "end" }, [
      t.priority !== "NORMAL" ? tag(t.priority === "HIGH" ? "High" : "Low", t.priority === "HIGH" ? "accent" : "") : null,
      iconButton("trash", "Delete task", async () => {
        if (!(await confirmDialog("Delete this task?", "“" + t.title + "” will be deleted."))) return;
        try { await del("/api/tasks/" + encodeURIComponent(t.id)); toast("Task deleted"); reload(); refreshCounts(); } catch (e) { toast(e.message, "error"); }
      }, "danger"),
    ]),
  ]);
}

async function loadTasks() {
  await refreshCounts();
  const all = await api("/api/tasks");
  const f = state.taskFilter;
  const items = all.filter((t) => f === "ALL" || t.status === f);
  const box = $("task-groups");
  box.replaceChildren();
  if (!items.length) {
    box.append(el("ul", { class: "rows" }, empty(f === "DONE" ? "Nothing done yet." : "No open tasks. Add one above.")));
    return;
  }
  const groups = [...state.folders.map((s) => [s.folder.id, s.folder.name, s.folder.icon]), [null, "No folder", "folder"]];
  groups.forEach(([id, name, ic]) => {
    const inGroup = items.filter((t) => (t.folderId || null) === id);
    if (!inGroup.length) return;
    box.append(el("h3", { class: "group-title" }, [icon(folderIconName(ic), "ic sm"), name + " · " + inGroup.length]),
      el("ul", { class: "rows" }, inGroup.map((t) => taskRow(t, loadTasks))));
  });
}

async function addTask(event) {
  event.preventDefault();
  try {
    await post("/api/tasks", { title: $("task-title").value, folderId: $("task-folder").value || null,
      dueLocal: $("task-due").value || null, priority: $("task-priority").value });
    $("task-title").value = ""; $("task-due").value = "";
    toast("Task added");
    loadTasks();
  } catch (e) { toast(e.message, "error"); }
}

// ---------- reminders ----------
function reminderRow(r, reload) {
  const due = new Date(r.dueAt);
  const past = r.status !== "SCHEDULED";
  const status = { SCHEDULED: ["Scheduled", "accent"], DELIVERED: ["Delivered", ""], CANCELLED: ["Cancelled", ""] }[r.status] || [r.status, ""];
  const folder = r.folderId && folderName(r.folderId);
  return el("li", { class: "rem" + (past ? " past" : "") }, [
    el("div", { class: "date" }, [el("b", { text: fmtDay.format(due) }), el("small", { text: fmtMon.format(due) })]),
    el("div", { class: "what" }, [el("strong", { text: r.text }), el("span", { text: relativeDay(due) + ", " + fmtTime.format(due) + " IST" + (folder ? " · " + folder : "") })]),
    tag(status[0], status[1]),
    past ? null : button("Cancel", async () => {
      if (!(await confirmDialog("Cancel this reminder?", "“" + r.text + "” won't be sent.", "Cancel reminder"))) return;
      try { await del("/api/reminders/" + encodeURIComponent(r.id)); toast("Reminder cancelled"); reload(); refreshCounts(); } catch (e) { toast(e.message, "error"); }
    }, "btn ghost"),
  ]);
}

async function loadReminders() {
  await refreshCounts();
  const items = await api("/api/reminders");
  const upcoming = items.filter((r) => r.status === "SCHEDULED").sort((a, b) => a.dueAt.localeCompare(b.dueAt));
  const past = items.filter((r) => r.status !== "SCHEDULED").sort((a, b) => b.dueAt.localeCompare(a.dueAt));
  $("reminders-upcoming").replaceChildren(...(upcoming.length ? upcoming.map((r) => reminderRow(r, loadReminders)) : [empty("No upcoming reminders.")]));
  $("reminders-past").replaceChildren(...(past.length ? past.map((r) => reminderRow(r, loadReminders)) : [empty("Nothing here yet.")]));
}

async function addReminder(text, dueLocal, folderId, reload) {
  try {
    await post("/api/reminders", { text, dueLocal, folderId: folderId || null });
    toast("Reminder set");
    reload();
    refreshCounts();
    return true;
  } catch (e) { toast(e.message, "error"); return false; }
}

// ---------- approvals ----------
async function loadApprovals() {
  const items = await api("/api/approvals");
  $("approval-list").replaceChildren(...(items.length ? items.map((a) => el("li", { class: "row-card" }, [
    el("span", { class: "lead" }, icon(a.type === "PAYMENT" ? "card" : "approve")),
    el("div", { class: "main" }, [el("strong", { text: a.title }), el("p", { text: a.detail }), el("span", { text: ago(a.createdAt) })]),
    el("div", { class: "end" }, [
      button("Yes, do it", () => decide(a, true), "btn primary", "check"),
      button("No", () => decide(a, false), "btn", "x"),
    ]),
  ])) : [empty("Nothing is waiting for you.")]));
  refreshCounts();
}

async function decide(a, approved) {
  try {
    const r = await post("/api/approvals/" + a.type.toLowerCase() + "/" + encodeURIComponent(a.id), { approved });
    toast(r.result.length > 140 ? r.result.slice(0, 137) + "…" : r.result);
  } catch (e) { toast(e.message, "error"); }
  loadApprovals();
}

// ---------- payments ----------
async function loadPayments() {
  const items = await api("/api/payments");
  const sum = (s) => items.filter((p) => p.status === s).reduce((n, p) => n + Number(p.amountInr), 0);
  $("payments-sub").textContent = fmtInr.format(sum("PENDING_APPROVAL")) + " waiting for approval · " + fmtInr.format(sum("APPROVED")) + " approved, not yet paid";
  const status = { PENDING_APPROVAL: ["Needs approval", "accent"], APPROVED: ["Approved", "accent"], REJECTED: ["Rejected", ""], PAID: ["Paid", ""] };
  $("payment-list").replaceChildren(...(items.length ? items.map((p) => {
    const act = async (path, body, msg) => {
      try { await post("/api/payments/" + encodeURIComponent(p.id) + path, body); toast(msg); loadPayments(); refreshCounts(); } catch (e) { toast(e.message, "error"); }
    };
    const actions = [];
    if (p.status === "PENDING_APPROVAL") actions.push(button("Approve", () => act("/decision", { approved: true }, "Approved. Pay it elsewhere, then mark it paid."), "btn primary", "check"),
      button("Reject", () => act("/decision", { approved: false }, "Rejected"), "btn", "x"));
    if (p.status === "APPROVED") actions.push(button("Mark paid", () => act("/paid", undefined, "Marked as paid"), "btn primary", "check"));
    actions.push(iconButton("trash", "Delete payment", async () => {
      if (!(await confirmDialog("Delete this payment?", p.payee + " · " + fmtInr.format(Number(p.amountInr))))) return;
      try { await del("/api/payments/" + encodeURIComponent(p.id)); loadPayments(); refreshCounts(); } catch (e) { toast(e.message, "error"); }
    }, "danger"));
    const meta = [p.dueAt ? "Due " + fmtDate.format(new Date(p.dueAt)) : "No due date", p.note].filter(Boolean).join(" · ");
    return el("li", { class: "row-card" + (p.status === "PAID" || p.status === "REJECTED" ? " muted" : "") }, [
      el("span", { class: "lead" }, icon("card")),
      el("div", { class: "main" }, [el("strong", { text: p.payee }), el("span", { text: meta })]),
      el("span", { class: "amount", text: fmtInr.format(Number(p.amountInr)) }),
      tag(status[p.status][0], status[p.status][1]),
      el("div", { class: "end" }, actions),
    ]);
  }) : [empty("No payments yet.")]));
}

async function addPayment(event) {
  event.preventDefault();
  try {
    await post("/api/payments", { payee: $("payment-payee").value, amountInr: Number($("payment-amount").value),
      dueDate: $("payment-due").value || null, note: $("payment-note").value || null });
    ["payment-payee", "payment-amount", "payment-due", "payment-note"].forEach((id) => ($(id).value = ""));
    toast("Payment added. It needs your approval.");
    loadPayments();
    refreshCounts();
  } catch (e) { toast(e.message, "error"); }
}

// ---------- messages and notifications ----------
async function loadMessages() {
  const items = await api("/api/messages");
  $("message-list").replaceChildren(...(items.length ? items.map((m) => {
    const li = el("li", { class: "row-card clickable" + (m.read ? "" : " unread"), onclick: async () => {
      if (m.read) return;
      try { await post("/api/messages/" + encodeURIComponent(m.id) + "/read"); li.classList.remove("unread"); m.read = true; refreshCounts(); } catch { /* ignore */ }
    } }, [
      el("span", { class: "lead" }, icon(m.fromAgentId === "system" ? "sparkle" : "agent")),
      el("div", { class: "main" }, [el("strong", { text: m.subject }), el("span", { text: m.fromName + " · " + ago(m.createdAt) }), el("p", { text: m.body })]),
      el("div", { class: "end" }, iconButton("trash", "Delete message", async (e) => {
        e.stopPropagation();
        try { await del("/api/messages/" + encodeURIComponent(m.id)); loadMessages(); refreshCounts(); } catch (err) { toast(err.message, "error"); }
      }, "danger")),
    ]);
    return li;
  }) : [empty("No messages from your agents.")]));
}

async function loadNotifications() {
  const items = await api("/api/notifications");
  $("notification-list").replaceChildren(...(items.length ? items.map((n) => el("li", { class: "row-card clickable" + (n.read ? "" : " unread"), onclick: async () => {
    try { if (!n.read) await post("/api/notifications/" + encodeURIComponent(n.id) + "/read"); } catch { /* ignore */ }
    if (n.link && /^#[a-z]+(\/[A-Za-z0-9-]+)?$/.test(n.link)) location.hash = n.link.slice(1); else loadNotifications();
    refreshCounts();
  } }, [
    el("span", { class: "lead" }, icon(NOTE_ICONS[n.kind] || "bell")),
    el("div", { class: "main" }, [el("strong", { text: n.title }), n.body ? el("p", { text: n.body }) : null, el("span", { text: ago(n.createdAt) })]),
  ])) : [empty("You're all caught up.")]));
}

// ---------- features (one agent, many behaviours) ----------
function feature(id) {
  return state.features.find((f) => f.id === id) || state.features[0];
}

async function loadFeatures() {
  const cat = await api("/api/features");
  state.features = cat.features;
  state.modes = cat.modes;
  if (!state.features.some((f) => f.id === state.featureId)) state.featureId = "optimal";
  renderFeatureMenu();
  setFeature(state.featureId, false);
  return cat;
}

function featureIcon(f, cls = "f-ic") {
  return el("span", { class: cls + " fi-" + f.id }, icon(f.icon));
}

function featureCard(f, i) {
  const off = f.capabilities.filter((c) => !c.available).length;
  return el("a", { class: "feature-card fi-" + f.id, href: "#feature/" + f.id, style: "--i:" + i, "data-feature": f.id }, [
    featureIcon(f),
    el("strong", { text: f.name }),
    el("span", { class: "fc-tag", text: f.tagline }),
    el("span", { class: "fc-foot" }, [
      el("small", { text: f.capabilities.length + (f.capabilities.length === 1 ? " plugin" : " plugins") + (off ? " · " + off + " off" : "") }),
      f.focusMode ? tag("focus", "accent", "gauge") : null,
    ]),
  ]);
}

async function loadHome() {
  const [me] = await Promise.all([api("/api/me").catch(() => null), state.features.length ? null : loadFeatures()]);
  state.me = me;
  const name = me && me.name ? me.name : null;
  typeOut($("greeting"), name ? "Hi " + name + "." : greeting() + ".");
  $("brand-sub").textContent = name ? name + "'s assistant" : "Your private assistant";
  const about = $("about");
  about.replaceChildren(...(me ? me.highlights.map((h, i) => el("li", { style: "--i:" + i, text: h })) : []));
  loadStudioHome();
  $("features-everyday").replaceChildren(...state.features.filter((f) => f.group === "EVERYDAY").map(featureCard));
  $("features-workspace").replaceChildren(...state.features.filter((f) => f.group === "WORKSPACE").map((f, i) => featureCard(f, i + 4)));
  const named = name ? "Hi " + name + "." : null;
  if (named) $("ask").textContent = "What are we making today, " + name + "?";
  const h = await refreshCounts();
  if (h) {
    const c = h.counts;
    $("day-summary").textContent = c.tasks + " open tasks · " + c.reminders + " reminders · " + c.approvals + " to approve";
  }
  if (typeof Companion !== "undefined") Companion.hero();
}

/** Types the greeting letter by letter (instant when animations are off). */
function typeOut(node, text) {
  clearInterval(typeOut.timer);
  if (!motionOn()) { node.textContent = text; return; }
  node.textContent = "";
  let i = 0;
  typeOut.timer = setInterval(() => {
    node.textContent = text.slice(0, ++i);
    if (i >= text.length) clearInterval(typeOut.timer);
  }, 45);
}

async function openFeature(id) {
  if (!state.features.length) await loadFeatures();
  const f = state.features.find((x) => x.id === id);
  if (!f) { location.hash = "home"; return; }
  state.pageFeature = f.id;
  $("f-icon").replaceChildren(featureIcon(f, "f-ic big"));
  $("f-group").textContent = f.group === "EVERYDAY" ? "How I think" : f.group === "STUDIO" ? "Editing room" : "Workspace";
  $("f-name").textContent = f.name;
  $("f-tagline").textContent = f.tagline;
  $("f-desc").textContent = f.description;
  $("f-examples").replaceChildren(...(FEATURE_EXAMPLES[f.id] || []).map((p, i) => el("button", {
    class: "chip example", type: "button", style: "--i:" + i, onclick: () => { setFeature(f.id); location.hash = "chat"; setTimeout(() => send(p), 350); },
  }, [icon("arrow", "ic sm"), p])));
  $("f-caps").replaceChildren(...f.capabilities.map((c) => el("li", { class: "cap" + (c.available ? "" : " off") }, [
    el("span", { class: "cap-dot" }),
    el("div", {}, [el("strong", { text: c.name }), el("small", { text: c.available ? c.description : c.reason })]),
    tag(c.kind === "CONNECTOR" ? "Connector" : "Plugin", c.kind === "CONNECTOR" ? "accent" : ""),
  ])));
  $("f-rules").replaceChildren(...f.guidelines.map((g) => el("li", { text: g })));
  if (typeof Tune !== "undefined") Tune.open(f);
}

function setFeature(id, sync = true) {
  const f = feature(id);
  if (!f) return;
  state.featureId = f.id;
  try { localStorage.setItem("weekend.feature", f.id); } catch { /* per-device convenience only */ }
  $("h-chat").textContent = f.name;
  $("pill-tagline").textContent = f.tagline;
  $("pill-icon").replaceChildren(featureIcon(f, "f-ic sm"));
  $("chat-hello").textContent = f.name + ": " + f.tagline;
  $("chat-intro").textContent = f.description;
  $("suggestions").replaceChildren(...(FEATURE_EXAMPLES[f.id] || []).map((p) => el("button", { class: "chip", type: "button", onclick: () => send(p) },
    [icon("sparkle", "ic sm"), p])));
  $("attach").hidden = !f.acceptsImages;
  if (!f.acceptsImages) clearAttachments();
  document.querySelectorAll("#feature-menu [data-id]").forEach((li) => li.setAttribute("aria-selected", String(li.dataset.id === f.id)));
  if (sync && typeof Companion !== "undefined") Companion.sync();
}

function renderFeatureMenu() {
  $("feature-menu").replaceChildren(...state.features.map((f) => el("li", {
    role: "option", "data-id": f.id, "aria-selected": String(f.id === state.featureId), tabindex: "0",
    onclick: () => { setFeature(f.id); toggleMenu(false); },
    onkeydown: (e) => { if (e.key === "Enter") { setFeature(f.id); toggleMenu(false); } },
  }, [featureIcon(f, "f-ic sm"), el("span", {}, [el("strong", { text: f.name }), el("small", { text: f.tagline })])])));
}

function toggleMenu(open) {
  const menu = $("feature-menu");
  const show = open === undefined ? menu.hidden : open;
  menu.hidden = !show;
  $("feature-pill").setAttribute("aria-expanded", String(show));
}

// ---------- images and art ----------
const MAX_SIDE = 1568; // larger images are scaled down before upload (Claude's recommended size; fewer tokens)

function clearAttachments() {
  state.attachments = [];
  renderThumbs();
}

function renderThumbs() {
  const box = $("thumbs");
  box.hidden = !state.attachments.length;
  box.replaceChildren(...state.attachments.map((a, i) => el("span", { class: "thumb" }, [
    el("img", { src: a, alt: "Attached image " + (i + 1) }),
    el("button", { type: "button", class: "thumb-x", "aria-label": "Remove image " + (i + 1), onclick: () => { state.attachments.splice(i, 1); renderThumbs(); } }, icon("x", "ic sm")),
  ])));
}

/** Reads image files, scales big ones down, keeps at most 4 (5 MB each server-side). */
async function addFiles(files) {
  for (const file of [...files]) {
    if (state.attachments.length >= 4) { toast("At most 4 images per message", "error"); break; }
    if (!/^image\/(png|jpeg|webp|gif)$/.test(file.type)) { toast(file.name + ": use PNG, JPEG, WebP or GIF", "error"); continue; }
    try { state.attachments.push(await shrink(file)); } catch { toast(file.name + " could not be read", "error"); }
  }
  renderThumbs();
}

function shrink(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = reject;
    reader.onload = () => {
      const url = reader.result;
      if (file.type === "image/gif") { resolve(url); return; }
      const img = new Image();
      img.onerror = reject;
      img.onload = () => {
        const scale = Math.min(1, MAX_SIDE / Math.max(img.naturalWidth, img.naturalHeight));
        if (scale === 1) { resolve(url); return; }
        const c = document.createElement("canvas");
        c.width = Math.round(img.naturalWidth * scale);
        c.height = Math.round(img.naturalHeight * scale);
        c.getContext("2d").drawImage(img, 0, 0, c.width, c.height);
        resolve(c.toDataURL(file.type === "image/png" ? "image/png" : "image/jpeg", 0.9));
      };
      img.src = url;
    };
    reader.readAsDataURL(file);
  });
}

/** Splits a reply into text and ```svg blocks; svg blocks become artwork cards. */
function replyNodes(text) {
  const parts = [];
  const re = /```svg\s*([\s\S]*?)```/g;
  let last = 0;
  let m;
  while ((m = re.exec(text))) {
    if (m.index > last) parts.push(el("div", { class: "text", text: text.slice(last, m.index).trim() }));
    parts.push(artCard(m[1]));
    last = re.lastIndex;
  }
  const rest = text.slice(last).trim();
  if (rest || !parts.length) parts.push(el("div", { class: "text", text: rest }));
  return parts.filter((n) => n.classList.contains("art") || n.textContent);
}

function artCard(svgText) {
  const svg = typeof cleanSvg === "function" ? cleanSvg(svgText) : null;
  if (!svg) return el("div", { class: "text", text: "(The drawing could not be displayed safely.)" });
  svg.setAttribute("role", "img");
  svg.setAttribute("aria-label", "Artwork");
  const fig = el("figure", { class: "art" }, [el("div", { class: "art-frame" }, svg), el("figcaption", { class: "row" }, [
    button("4K PNG", () => WeekendArt.downloadPng(svg), "btn primary sm", "download"),
    button("SVG", () => WeekendArt.downloadSvg(svg), "btn sm", "download"),
    button("Full screen", () => { $("art-full-body").replaceChildren(svg.cloneNode(true)); $("art-full").showModal(); }, "btn ghost sm", "expand"),
  ])]);
  return fig;
}

/** 4K export: rasterises the sanitised SVG on a canvas 3840 px wide (height from the viewBox). */
const WeekendArt = {
  size(svg) {
    const vb = (svg.getAttribute("viewBox") || "0 0 3840 2160").split(/[\s,]+/).map(Number);
    const ratio = vb[3] > 0 && vb[2] > 0 ? vb[3] / vb[2] : 9 / 16;
    return { width: 3840, height: Math.round(3840 * ratio) };
  },
  async toCanvas(svg) {
    const { width, height } = this.size(svg);
    const copy = svg.cloneNode(true);
    copy.setAttribute("width", width);
    copy.setAttribute("height", height);
    const blob = new Blob([new XMLSerializer().serializeToString(copy)], { type: "image/svg+xml" });
    const url = URL.createObjectURL(blob);
    try {
      const img = await new Promise((resolve, reject) => { const i = new Image(); i.onload = () => resolve(i); i.onerror = reject; i.src = url; });
      const c = document.createElement("canvas");
      c.width = width;
      c.height = height;
      c.getContext("2d").drawImage(img, 0, 0, width, height);
      return c;
    } finally {
      URL.revokeObjectURL(url);
    }
  },
  async downloadPng(svg) {
    try {
      const c = await this.toCanvas(svg);
      c.toBlob((b) => { save(b, "weekend-art-" + c.width + "x" + c.height + ".png"); toast("4K PNG saved (" + c.width + "×" + c.height + ")"); }, "image/png");
    } catch { toast("Could not render the PNG", "error"); }
  },
  downloadSvg(svg) {
    save(new Blob([new XMLSerializer().serializeToString(svg)], { type: "image/svg+xml" }), "weekend-art.svg");
  },
};

function save(blob, name) {
  const url = URL.createObjectURL(blob);
  el("a", { href: url, download: name }).click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

// ---------- chat ----------
function scrollChat() {
  const box = $("chat-scroll");
  box.scrollTop = box.scrollHeight;
}

function messageRow(role, children) {
  const avatar = role === "assistant" ? el("div", { class: "avatar" }, el("img", { src: state.logoSrc, alt: "" })) : null;
  const li = el("li", { class: "msg " + role }, [avatar, el("div", { class: "body" }, children)]);
  $("empty-chat").hidden = true;
  $("messages").append(li);
  scrollChat();
  return li;
}

function addUser(text, images = []) {
  messageRow("user", [images.length ? el("div", { class: "msg-images" }, images.map((u, i) => el("img", { src: u, alt: "Attached image " + (i + 1) }))) : null,
    el("div", { class: "text", text })]);
}

function addAssistant(text, metaTags = []) {
  const copy = iconButton("copy", "Copy reply", async () => {
    try { await navigator.clipboard.writeText(text); toast("Copied"); } catch { toast("Copy not allowed here", "error"); }
  });
  return messageRow("assistant", [...replyNodes(text), el("div", { class: "meta" }, [...metaTags, copy])]);
}

function pendingCard(p) {
  const card = el("div", { class: "confirm-card" });
  const decideNow = async (approved) => {
    card.querySelectorAll("button").forEach((b) => (b.disabled = true));
    try {
      const r = await post("/api/confirm/" + encodeURIComponent(p.id), { approved });
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
      button("Yes, do it", () => decideNow(true), "btn primary", "check"),
      button("No", () => decideNow(false), "btn", "x"),
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
  const images = state.attachments.slice();
  clearAttachments();
  addUser(text, images);
  const typing = messageRow("assistant", el("div", { class: "typing", "aria-label": "Weekend is thinking" }, [el("i"), el("i"), el("i")]));
  $("send").disabled = true;
  try {
    const r = await post("/api/chat", { conversationId: state.conversationId, message: text, thinkHarder: $("think").checked,
      featureId: state.featureId, images });
    state.conversationId = r.conversationId;
    typing.remove();
    const f = feature(r.featureId);
    const tags = [el("span", { class: "tag accent" }, [f ? featureIcon(f, "f-ic xs") : null, r.featureName])];
    tags.push(tag(r.model, "", "sparkle"));
    if (images.length) tags.push(tag(images.length + (images.length === 1 ? " image" : " images"), "", "eye"));
    r.toolsUsed.forEach((t) => tags.push(tag(t, "", "tool")));
    if (r.memorySaved) tags.push(tag("memory saved", "", "memory"));
    const cost = Number(r.costUsd || 0);
    if (cost > 0) tags.push(tag(money(cost)));
    const p = r.pendingConfirmation;
    const reply = p && r.reply.trim() === p.summary.trim() ? "I need your OK before I do this." : r.reply;
    const row = addAssistant(reply, tags);
    if (p) row.querySelector(".body").append(pendingCard(p));
    if (r.studio) row.querySelector(".body").append(...studioNodes(r.studio));
    state.costUsd += cost;
    $("cost-pill").hidden = state.costUsd <= 0;
    $("cost-pill").textContent = "This tab: " + money(state.costUsd);
    refreshCounts();
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
    list.append(empty(state.memories.length ? "No memories match." : "Nothing remembered yet. Say \"remember that …\" in chat."));
    return;
  }
  items.forEach((m) => {
    const [label, kind] = KINDS[m.kind] || [m.kind, ""];
    const pin = iconButton("pin", m.pinned ? "Unpin" : "Pin", async () => {
      try { await post("/api/memories/" + encodeURIComponent(m.id) + "/pin", { pinned: !m.pinned }); await loadMemories(); } catch (e) { toast(e.message, "error"); }
    }, m.pinned ? "on" : "");
    const remove = iconButton("trash", "Delete", async () => {
      if (!(await confirmDialog("Delete this memory?", "“" + m.text + "” will be deleted permanently."))) return;
      try { await del("/api/memories/" + encodeURIComponent(m.id)); toast("Memory deleted"); await loadMemories(); } catch (e) { toast(e.message, "error"); }
    }, "danger");
    const foot = m.pinned
      ? [icon("pin", "ic sm"), "Pinned · kept until you delete it"]
      : [icon("clock", "ic sm"), "Saved " + fmtDate.format(new Date(m.createdAt)) + (m.expiresAt ? " · expires " + fmtDate.format(new Date(m.expiresAt)) : "")];
    list.append(el("li", { class: "mem" + (m.pinned ? " pinned" : "") }, [
      el("div", { class: "top" }, [tag(label, kind), m.pinned ? tag("Pinned", "accent", "pin") : null, el("div", { class: "actions" }, [pin, remove])]),
      el("div", { class: "text", text: m.text }),
      el("div", { class: "foot" }, foot),
    ]));
  });
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
    document.querySelectorAll("[data-ret]").forEach((s) => { if (i.retentionDays[s.dataset.ret] !== undefined) s.textContent = i.retentionDays[s.dataset.ret]; });
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
  if (!(await confirmDialog("Delete everything?", "All conversations, memories, tasks, reminders, payments, messages, folders and the agents you created will be deleted. This can't be undone.", "Delete everything"))) return;
  try {
    await post("/api/delete-all", { confirmation: $("delete-phrase").value });
    $("delete-phrase").value = "";
    $("delete-btn").disabled = true;
    newChat();
    loadFeatures().catch(() => {});
    refreshCounts();
    toast("All data deleted");
  } catch (e) {
    toast("Not deleted: " + e.message, "error");
  }
}

// ---------- navigation with transitions ----------
// ---------- Weekend Studio (videos) ----------
/** Opens a Studio chat and sends the first message. */
function startStudio(text) {
  setFeature("studio");
  newChat();
  location.hash = "chat";
  setTimeout(() => send(text), 350);
}

async function loadStudioHome() {
  const room = $("edit-room");
  if (motionOn()) requestAnimationFrame(() => setTimeout(() => room.classList.add("open"), 60)); else room.classList.add("open");
  clearInterval(loadStudioHome.timer);
  const started = Date.now();
  const tick = () => {
    const s = Math.floor((Date.now() - started) / 1000);
    $("room-clock").textContent = "REC " + [s / 3600, (s / 60) % 60, s % 60].map((v) => String(Math.floor(v)).padStart(2, "0")).join(":");
  };
  tick();
  loadStudioHome.timer = setInterval(() => { if (state.view && state.view.id === "view-home") tick(); }, 1000);
  const studio = state.features.find((f) => f.id === "studio");
  if (studio) {
    $("studio-status").replaceChildren(...studio.capabilities.filter((c) => c.id !== "MEMORY").map((c) => el("li", {
      class: "cap-pill" + (c.available ? "" : " off"), title: c.available ? (c.reason || c.description) : c.reason }, [
      icon({ RENDER: "film", RESEARCH: "globe", VOICE: "mic", PHOTOS: "eye" }[c.id] || "plug", "ic sm"),
      c.name + (c.available ? "" : " · off"),
    ])));
    const live = studio.capabilities.find((c) => c.id === "RENDER");
    $("on-air").classList.toggle("live", !!(live && live.available));
  }
  try {
    const projects = await api("/api/studio/projects");
    const n = projects.reduce((a, p) => a + p.jobs.filter((j) => j.status === "READY").length, 0);
    $("library-count").textContent = n ? n + (n === 1 ? " video" : " videos") : "Your videos";
  } catch { /* library count is decoration */ }
}

function studioNodes(sr) {
  const nodes = [];
  if (sr.plan && sr.plan.length) {
    nodes.push(el("ol", { class: "studio-plan" }, sr.plan.map((p) => el("li", { text: p.replace(/^\d+\.\s*/, "") }))));
  }
  if (sr.options && sr.options.length) {
    const box = el("div", { class: "studio-options" });
    sr.options.forEach((o, i) => box.append(el("button", {
      type: "button", class: "chip option" + (sr.kind === "CONFIRM" && i === 0 ? " primary" : ""),
      onclick: () => { box.querySelectorAll("button").forEach((b) => (b.disabled = true)); send(o); },
    }, [sr.kind === "CONFIRM" && i === 0 ? icon("play", "ic sm") : null, o])));
    nodes.push(box);
  }
  if (sr.kind === "STARTED" && sr.projectId) nodes.push(progressCard(sr.projectId));
  return nodes;
}

function fmtSeconds(s) {
  if (s == null) return "";
  const v = Math.round(s);
  return v < 60 ? v + " s" : Math.floor(v / 60) + ":" + String(v % 60).padStart(2, "0");
}

function jobRow(j) {
  const done = j.status === "READY" || j.status === "FAILED";
  return el("div", { class: "job " + j.status.toLowerCase(), "data-job": j.id }, [
    el("div", { class: "job-head" }, [
      el("strong", { text: (j.total > 1 ? j.number + ". " : "") + j.title }),
      el("small", { text: j.status === "FAILED" ? "Failed: " + (j.error || "unknown error") : done ? "" : j.stage + " · " + j.progress + "%" }),
    ]),
    done ? null : el("div", { class: "bar", role: "progressbar", "aria-valuenow": String(j.progress), "aria-valuemin": "0", "aria-valuemax": "100" },
      el("i", { style: "width:" + j.progress + "%" })),
  ]);
}

function videoCard(j, format) {
  const f = j.files;
  const meta = j.metadata || {};
  const video = el("video", { controls: "", preload: "metadata", playsinline: "", poster: f.thumbnail, src: f.video,
    "aria-label": "Video: " + j.title });
  const copy = (label, value) => iconButton("copy", label, async () => {
    try { await navigator.clipboard.writeText(value || ""); toast("Copied " + label.toLowerCase().replace("copy ", "")); } catch { toast("Copy not allowed here", "error"); }
  });
  const tags = [tag(fmtSeconds(j.seconds), "", "clock"), tag(format === "SHORT" ? "Short · 9:16" : "16:9", "", "film")];
  if (j.voice) tags.push(tag(j.voice, j.draftVoice ? "warn" : "ok", "mic"));
  if (j.draftVoice) tags.push(tag("draft voice: not for monetised uploads", "warn"));
  if (j.draftScript) tags.push(tag("offline draft script", "warn"));
  return el("article", { class: "video-card " + (format === "SHORT" ? "vertical" : "landscape"), "data-job": j.id }, [
    el("div", { class: "player" }, video),
    el("div", { class: "v-body" }, [
      el("h3", { text: (j.total > 1 ? "Part " + j.number + "/" + j.total + " · " : "") + (meta.title || j.title) }),
      el("div", { class: "v-tags" }, tags),
      meta.description ? el("details", { class: "v-desc" }, [el("summary", { text: "Description, sources and credits" }), el("pre", { text: meta.description })]) : null,
      el("div", { class: "v-actions" }, [
        el("a", { class: "btn primary", href: f.video + "&download=1", download: "" }, [icon("download", "ic sm"), "MP4"]),
        el("a", { class: "btn", href: f.captions + "&download=1", download: "" }, [icon("cc", "ic sm"), "Captions"]),
        copy("Copy title", meta.title || j.title),
        copy("Copy description", meta.description),
      ]),
    ]),
  ]);
}

/** Live progress for one project; turns each finished job into a player. Stops polling when done or removed. */
function progressCard(projectId) {
  const card = el("div", { class: "studio-progress", "data-project": projectId }, el("p", { class: "hint", text: "Starting…" }));
  let tries = 0;
  const poll = async () => {
    if (!card.isConnected && tries > 0) return;
    tries++;
    try {
      const p = await api("/api/studio/projects/" + encodeURIComponent(projectId));
      const pending = p.jobs.filter((j) => j.status === "QUEUED" || j.status === "WORKING");
      const ready = p.jobs.filter((j) => j.status === "READY").length;
      card.replaceChildren(
        el("p", { class: "studio-sum" }, [icon("film", "ic sm"), ready + " of " + p.jobs.length + " ready" + (pending.length ? " · rendering…" : "")]),
        ...p.jobs.map((j) => (j.status === "READY" ? videoCard(j, p.format) : jobRow(j))));
      if (pending.length) setTimeout(poll, 1500);
      else { refreshCounts(); if (ready) toast(ready === 1 ? "Your video is ready" : ready + " videos are ready"); }
      scrollChat();
    } catch (e) {
      if (tries < 40) setTimeout(poll, 3000); else card.replaceChildren(el("p", { class: "hint", text: "Lost track of the render: open the Studio library." }));
    }
  };
  setTimeout(poll, 400);
  return card;
}

async function loadLibrary() {
  const list = $("lib-list");
  const projects = await api("/api/studio/projects");
  if (!projects.length) {
    list.replaceChildren(empty("No videos yet. Tell me what to make: a Short, a series, or a long video."));
    return;
  }
  list.replaceChildren(...projects.map((p) => {
    const ready = p.jobs.filter((j) => j.status === "READY");
    const busy = p.jobs.some((j) => j.status === "QUEUED" || j.status === "WORKING");
    const head = el("header", { class: "lib-head" }, [
      el("div", {}, [
        el("h2", { text: p.topic || "Untitled" }),
        el("small", { text: (p.count > 1 ? p.count + " × " : "") + fmtSeconds(p.seconds) + " · " + (p.format === "SHORT" ? "Shorts" : "Video")
          + " · " + (p.voice ? "voice" : "no voice") + (p.captions ? " + captions" : "") + " · " + relativeDay(new Date(p.createdAt)) }),
      ]),
      iconButton("trash", "Delete project", async () => {
        if (!(await confirmDialog("Delete this project?", "Its videos, captions and thumbnails are removed permanently."))) return;
        try { await del("/api/studio/projects/" + encodeURIComponent(p.id)); toast("Deleted"); loadLibrary(); } catch (e) { toast(e.message, "error"); }
      }),
    ]);
    const body = busy ? progressCard(p.id) : el("div", { class: "lib-grid" }, [
      ...ready.map((j) => videoCard(j, p.format)),
      ...p.jobs.filter((j) => j.status === "FAILED").map(jobRow),
    ]);
    return el("section", { class: "lib-project" }, [head, body]);
  }));
}

function motionOn() {
  if (matchMedia("(prefers-reduced-motion: reduce)").matches) return false;
  try { return localStorage.getItem("weekend.motion") !== "off"; } catch { return true; }
}

function showView(hash) {
  let [name, arg] = hash.split("/");
  if (!VIEWS.includes(name) || ((name === "folder" || name === "feature") && !arg)) name = "home";
  const navName = name === "folder" ? "day" : name === "feature" ? "home" : name;
  document.querySelectorAll(".nav button").forEach((b) => (b.dataset.view === navName ? b.setAttribute("aria-current", "page") : b.removeAttribute("aria-current")));
  const next = $("view-" + name);
  const prev = state.view && state.view !== next ? state.view : null;
  const swap = () => {
    document.querySelectorAll(".view").forEach((v) => (v.hidden = v !== next));
    next.classList.remove("entering");
    if (motionOn()) { void next.offsetWidth; next.classList.add("entering"); }
    next.querySelector(".view-body")?.scrollTo?.(0, 0);
    if (typeof Companion !== "undefined") Companion.sync();
  };
  if (prev && motionOn()) {
    prev.classList.add("leaving");
    setTimeout(() => { prev.classList.remove("leaving"); swap(); }, 170);
  } else {
    swap();
  }
  state.view = next;
  const loaders = {
    home: loadHome, day: loadDay, tasks: loadTasks, reminders: loadReminders, approvals: loadApprovals,
    payments: loadPayments, messages: loadMessages, notifications: loadNotifications, memories: loadMemories,
    settings: loadInfo, folder: () => loadFolder(arg), feature: () => openFeature(arg), library: loadLibrary,
  };
  if (loaders[name]) loaders[name]().catch((e) => { toast(e.message, "error"); if (name === "folder") location.hash = "day"; });
  if (name === "chat") setTimeout(() => $("input").focus({ preventScroll: true }), 200);
  toggleMenu(false);
}

document.addEventListener("DOMContentLoaded", () => {
  try { state.featureId = localStorage.getItem("weekend.feature") || "optimal"; } catch { /* default feature */ }
  $("composer").addEventListener("submit", (e) => { e.preventDefault(); send($("input").value); });
  $("input").addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey && !e.isComposing) { e.preventDefault(); send($("input").value); }
  });
  $("input").addEventListener("input", autosize);
  $("new-chat").addEventListener("click", newChat);
  $("feature-pill").addEventListener("click", () => toggleMenu());
  document.addEventListener("click", (e) => { if (!e.target.closest(".chat-title")) toggleMenu(false); });
  $("studio-prompt").addEventListener("submit", (e) => { e.preventDefault(); const t = $("studio-input").value.trim(); $("studio-input").value = ""; startStudio(t || "Make a video"); });
  document.querySelectorAll(".s-tile[data-prompt]").forEach((b) => b.addEventListener("click", () => startStudio(b.dataset.prompt)));
  $("lib-new").addEventListener("click", () => { location.hash = "home"; setTimeout(() => $("studio-input").focus(), 250); });
  $("f-start").addEventListener("click", () => { setFeature(state.pageFeature); newChat(); location.hash = "chat"; });
  $("attach").addEventListener("click", () => $("file").click());
  $("file").addEventListener("change", (e) => { addFiles(e.target.files); e.target.value = ""; });
  $("composer").addEventListener("dragover", (e) => { if (feature(state.featureId)?.acceptsImages) e.preventDefault(); });
  $("composer").addEventListener("drop", (e) => { if (feature(state.featureId)?.acceptsImages) { e.preventDefault(); addFiles(e.dataTransfer.files); } });
  $("input").addEventListener("paste", (e) => {
    const imgs = [...(e.clipboardData?.files || [])].filter((f) => f.type.startsWith("image/"));
    if (imgs.length && feature(state.featureId)?.acceptsImages) { e.preventDefault(); addFiles(imgs); }
  });
  try { $("motion-toggle").checked = localStorage.getItem("weekend.motion") !== "off"; } catch { /* default on */ }
  $("motion-toggle").addEventListener("change", (e) => {
    try { localStorage.setItem("weekend.motion", e.target.checked ? "on" : "off"); } catch { /* per-device only */ }
    document.documentElement.classList.toggle("no-motion", !e.target.checked);
  });
  document.documentElement.classList.toggle("no-motion", !motionOn());

  $("new-folder").addEventListener("click", newFolder);
  $("folder-rename").addEventListener("click", async () => {
    const s = state.folders.find((x) => x.folder.id === state.folderId);
    const f = await folderSheet("Edit folder", s && s.folder);
    if (!f) return;
    try { await put("/api/folders/" + encodeURIComponent(state.folderId), f); toast("Folder saved"); loadFolder(state.folderId); } catch (e) { toast(e.message, "error"); }
  });
  $("folder-delete").addEventListener("click", async () => {
    if (!(await confirmDialog("Delete this folder?", "Its tasks and reminders are kept; they just won't be in a folder."))) return;
    try { await del("/api/folders/" + encodeURIComponent(state.folderId)); toast("Folder deleted"); location.hash = "day"; } catch (e) { toast(e.message, "error"); }
  });
  $("folder-task-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      await post("/api/tasks", { title: $("folder-task-title").value, folderId: state.folderId, dueLocal: $("folder-task-due").value || null, priority: "NORMAL" });
      e.target.reset();
      toast("Task added");
      loadFolder(state.folderId);
    } catch (err) { toast(err.message, "error"); }
  });
  $("folder-reminder-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    if (await addReminder($("folder-reminder-text").value, $("folder-reminder-due").value, state.folderId, () => loadFolder(state.folderId))) e.target.reset();
  });

  $("task-form").addEventListener("submit", addTask);
  document.querySelectorAll("#task-filter button").forEach((b) => b.addEventListener("click", () => {
    state.taskFilter = b.dataset.filter;
    document.querySelectorAll("#task-filter button").forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
    loadTasks().catch((e) => toast(e.message, "error"));
  }));
  $("reminder-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    if (await addReminder($("reminder-text").value, $("reminder-due").value, $("reminder-folder").value, loadReminders)) e.target.reset();
  });
  $("payment-form").addEventListener("submit", addPayment);
  $("read-all").addEventListener("click", async () => {
    try { await post("/api/notifications/read-all"); loadNotifications(); refreshCounts(); } catch (e) { toast(e.message, "error"); }
  });

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
  showSessionState();
  loadInfo();
  loadFeatures().catch(() => { /* not signed in yet */ });
  showView(location.hash.slice(1));
  setInterval(() => { checkHealth(); refreshCounts(); }, 30000);
  if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});
});
