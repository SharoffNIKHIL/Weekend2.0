// app/src/main/resources/static/app.js — Weekend PWA client (UI v3). No third-party scripts; talks only to this origin.
// Model output, agent messages and stored text are always rendered with textContent, never as HTML.
"use strict";

const TZ = "Asia/Kolkata";
const USD_TO_INR = 96.12; // display-only estimate; same FX as the project tracker (2026-10-02)
const DELETE_PHRASE = "DELETE ALL MY DATA";
const VIEWS = ["home", "chat", "agents", "tasks", "reminders", "approvals", "payments", "messages", "notifications", "memories", "settings", "folder"];
const KINDS = { FACT: ["Fact", "accent"], PREFERENCE: ["Preference", ""], TASK: ["Task", ""] };
const FOLDER_ICONS = ["folder", "home", "work", "money", "health", "travel", "code", "cart", "star", "book"];
const NOTE_ICONS = { REMINDER: "bell", TASK: "task", APPROVAL: "approve", PAYMENT: "card", MESSAGE: "mail", SYSTEM: "sparkle" };

const state = {
  conversationId: null, costUsd: 0, memories: [], memoryFilter: "all", info: null,
  catalog: { agents: [], tools: [], remoteAllowed: false }, agentId: "weekend",
  folders: [], taskFilter: "OPEN", folderId: null,
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

async function loadHome() {
  $("greeting").textContent = greeting();
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
  try { await post("/api/folders", f); toast("Folder created"); loadHome(); } catch (e) { toast(e.message, "error"); }
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

// ---------- agents ----------
function activeAgent() {
  return state.catalog.agents.find((a) => a.id === state.agentId) || state.catalog.agents[0];
}

function setAgent(id) {
  state.agentId = id;
  try { localStorage.setItem("weekend.agent", id); } catch { /* per-device convenience only */ }
  $("agent-select").value = id;
  const a = activeAgent();
  if (a) $("chat-sub").textContent = (a.id === "weekend" ? "Weekend" : a.name) + " answers · write actions always wait for your yes.";
  document.querySelectorAll(".agent").forEach((c) => c.classList.toggle("active", c.dataset.id === id));
}

function conditionChips(a) {
  if (a.kind === "REMOTE") return [tag(a.endpointHost || "remote", "", "globe"), tag("Every message waits for your yes", "accent", "shield")];
  if (a.kind === "BUILTIN") return [tag("All tools", "", "tool"), tag("Writes wait for your yes", "", "shield")];
  const c = a.conditions;
  const chips = [tag(c.allowedTools === null ? "All tools" : c.allowedTools.length ? "Tools: " + c.allowedTools.join(", ") : "No tools", "", "tool")];
  if (c.confirmAllTools) chips.push(tag("Every action waits for your yes", "accent", "shield"));
  if (c.thinkHarder) chips.push(tag("Thinks harder", "", "sparkle"));
  chips.push(tag("Max " + c.maxToolSteps + " steps"));
  return chips;
}

function agentCard(a) {
  const avatar = a.kind === "BUILTIN" ? el("img", { src: "logo.svg", alt: "" }) : icon(a.kind === "REMOTE" ? "link" : "agent");
  const kind = { BUILTIN: "Built in", CUSTOM: "Custom", REMOTE: "Connected" }[a.kind];
  const actions = [];
  actions.push(a.id === state.agentId ? button("In use", null, "btn sm", "check") : button("Use in chat", () => { setAgent(a.id); location.hash = "chat"; }, "btn primary sm", "chat"));
  if (a.instructionsChars) actions.push(button("Instructions", () => showInstructions(a), "btn ghost sm", "file"));
  if (a.kind === "CUSTOM") actions.push(button("Conditions", () => editConditions(a), "btn ghost sm", "settings"));
  if (a.kind === "REMOTE") actions.push(button("Test", async () => {
    try { const r = await post("/api/agents/" + encodeURIComponent(a.id) + "/test"); toast(r.healthy ? a.name + " is reachable" : a.name + " did not answer", r.healthy ? "" : "error"); } catch (e) { toast(e.message, "error"); }
  }, "btn ghost sm", "link"));
  if (a.editable) actions.push(iconButton("trash", "Delete agent", async () => {
    if (!(await confirmDialog("Delete " + a.name + "?", a.kind === "REMOTE" ? "Weekend disconnects it and its token stops working." : "Its instructions are deleted."))) return;
    try { await del("/api/agents/" + encodeURIComponent(a.id)); if (state.agentId === a.id) setAgent("weekend"); toast("Agent deleted"); loadAgents(); } catch (e) { toast(e.message, "error"); }
  }, "danger"));
  const source = a.instructionsSource && a.kind === "CUSTOM"
    ? el("div", { class: "source" }, [icon("file", "ic sm"), a.instructionsSource + (a.instructionsTitle ? " · " + a.instructionsTitle : "") + " · " + a.instructionsLines + (a.instructionsLines === 1 ? " line" : " lines")]) : null;
  const leaves = a.dataLeavesIndia && a.kind !== "REMOTE" ? el("p", { class: "flag" }, [icon("globe", "ic sm"), el("span", { text: "Prompts are processed outside India." })]) : null;
  return el("li", { class: "agent" + (a.id === state.agentId ? " active" : ""), "data-id": a.id }, [
    el("div", { class: "top" }, [el("span", { class: "avatar" }, avatar), el("div", {}, [el("h3", { text: a.name }), tag(kind, a.kind === "BUILTIN" ? "accent" : "")])]),
    a.description ? el("p", { class: "desc", text: a.description }) : null,
    el("div", { class: "conds" }, conditionChips(a)),
    source, leaves,
    el("div", { class: "actions" }, actions),
  ]);
}

async function loadAgents() {
  state.catalog = await api("/api/agents");
  if (!state.catalog.agents.some((a) => a.id === state.agentId)) state.agentId = "weekend";
  $("agent-select").replaceChildren(...state.catalog.agents.map((a) => el("option", { value: a.id, text: a.name })));
  $("agent-list").replaceChildren(...state.catalog.agents.map(agentCard));
  setAgent(state.agentId);
  conditionsEditor($("custom-conditions"), null);
  $("remote-off").hidden = state.catalog.remoteAllowed;
  $("remote-form").querySelectorAll("input, button").forEach((x) => (x.disabled = !state.catalog.remoteAllowed));
}

/** Fills a fieldset with condition controls; cond null = defaults (all tools). */
function conditionsEditor(box, cond) {
  const c = cond || { allowedTools: null, confirmAllTools: false, thinkHarder: false, maxToolSteps: 5 };
  box.replaceChildren(el("legend", { text: "Conditions" }),
    el("div", { class: "tool-list" }, state.catalog.tools.map((t) => el("label", {}, [
      el("input", { type: "checkbox", value: t, "data-tool": "", checked: c.allowedTools === null || c.allowedTools.includes(t) }), t]))),
    el("label", {}, [el("input", { type: "checkbox", "data-confirm": "", checked: c.confirmAllTools }), "Every action waits for my yes"]),
    el("label", {}, [el("input", { type: "checkbox", "data-think": "", checked: c.thinkHarder }), "Always think harder (stronger model, costs more)"]),
    el("label", {}, ["Max tool steps", el("input", { type: "number", min: "0", max: "10", value: String(c.maxToolSteps), "data-steps": "" })]));
}

function readConditions(box) {
  const tools = [...box.querySelectorAll("[data-tool]")];
  const picked = tools.filter((x) => x.checked).map((x) => x.value);
  return {
    allowedTools: picked.length === tools.length ? null : picked,
    confirmAllTools: box.querySelector("[data-confirm]").checked,
    thinkHarder: box.querySelector("[data-think]").checked,
    maxToolSteps: Math.max(0, Math.min(10, Number(box.querySelector("[data-steps]").value) || 0)),
  };
}

async function showInstructions(a) {
  try {
    const r = await api("/api/agents/" + encodeURIComponent(a.id) + "/instructions");
    await sheet(a.name + " · instructions", [el("p", { text: (a.instructionsSource || "") + " · added after Weekend's safety rules, which always win." }), el("pre", { text: r.text })]);
  } catch (e) { toast(e.message, "error"); }
}

async function editConditions(a) {
  const box = el("fieldset", { class: "conditions" });
  conditionsEditor(box, a.conditions);
  if (!(await sheet(a.name + " · conditions", [box], "Save"))) return;
  try { await put("/api/agents/" + encodeURIComponent(a.id) + "/conditions", readConditions(box)); toast("Conditions saved"); loadAgents(); } catch (e) { toast(e.message, "error"); }
}

async function createCustom(event) {
  event.preventDefault();
  try {
    const a = await post("/api/agents/custom", { name: $("custom-name").value, description: $("custom-desc").value || null,
      instructions: $("custom-instructions").value, conditions: readConditions($("custom-conditions")) });
    event.target.reset();
    toast(a.name + " created");
    await loadAgents();
  } catch (e) { toast(e.message, "error"); }
}

async function connectRemote(event) {
  event.preventDefault();
  try {
    const r = await post("/api/agents/remote", { name: $("remote-name").value, endpoint: $("remote-endpoint").value, description: $("remote-desc").value || null });
    event.target.reset();
    await loadAgents();
    const code = el("code", { text: r.inboundToken });
    await sheet("Connected " + r.agent.name, [
      el("p", { text: "Give this token to the other agent so it can message you. Weekend keeps only a hash; this is the only time you will see it." }),
      el("div", { class: "token-box" }, [code, button("Copy", async () => {
        try { await navigator.clipboard.writeText(r.inboundToken); toast("Token copied"); } catch { toast("Copy not allowed here", "error"); }
      }, "btn", "copy")]),
      el("p", { text: "It sends: POST " + location.origin + "/agent-inbox with header Authorization: Bearer <token> and JSON {\"subject\", \"body\"}." }),
    ]);
  } catch (e) { toast(e.message, "error"); }
}

// ---------- chat ----------
function scrollChat() {
  const box = $("chat-scroll");
  box.scrollTop = box.scrollHeight;
}

function messageRow(role, children) {
  const avatar = role === "assistant" ? el("div", { class: "avatar" }, el("img", { src: "logo.svg", alt: "" })) : null;
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
  addUser(text);
  const typing = messageRow("assistant", el("div", { class: "typing", "aria-label": "Weekend is thinking" }, [el("i"), el("i"), el("i")]));
  $("send").disabled = true;
  try {
    const r = await post("/api/chat", { conversationId: state.conversationId, message: text, thinkHarder: $("think").checked, agentId: state.agentId });
    state.conversationId = r.conversationId;
    typing.remove();
    const tags = [];
    if (r.agentId && r.agentId !== "weekend") tags.push(tag(r.agentName, "accent", "agent"));
    tags.push(tag(r.model, r.agentId === "weekend" ? "accent" : "", "sparkle"));
    r.toolsUsed.forEach((t) => tags.push(tag(t, "", "tool")));
    if (r.memorySaved) tags.push(tag("memory saved", "", "memory"));
    const cost = Number(r.costUsd || 0);
    if (cost > 0) tags.push(tag(money(cost)));
    const p = r.pendingConfirmation;
    const reply = p && r.reply.trim() === p.summary.trim() ? "I need your OK before I do this." : r.reply;
    const row = addAssistant(reply, tags);
    if (p) row.querySelector(".body").append(pendingCard(p));
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
    setAgent("weekend");
    refreshCounts();
    toast("All data deleted");
  } catch (e) {
    toast("Not deleted: " + e.message, "error");
  }
}

// ---------- navigation ----------
function showView(hash) {
  let [name, arg] = hash.split("/");
  if (!VIEWS.includes(name) || (name === "folder" && !arg)) name = "home";
  const navName = name === "folder" ? "home" : name;
  document.querySelectorAll(".nav button").forEach((b) => (b.dataset.view === navName ? b.setAttribute("aria-current", "page") : b.removeAttribute("aria-current")));
  document.querySelectorAll(".view").forEach((v) => (v.hidden = v.id !== "view-" + name));
  const loaders = {
    home: loadHome, agents: loadAgents, tasks: loadTasks, reminders: loadReminders, approvals: loadApprovals,
    payments: loadPayments, messages: loadMessages, notifications: loadNotifications, memories: loadMemories,
    settings: loadInfo, folder: () => loadFolder(arg),
  };
  if (loaders[name]) loaders[name]().catch((e) => { toast(e.message, "error"); if (name === "folder") location.hash = "home"; });
  if (name === "chat") $("input").focus({ preventScroll: true });
}

document.addEventListener("DOMContentLoaded", () => {
  try { state.agentId = localStorage.getItem("weekend.agent") || "weekend"; } catch { /* default agent */ }
  $("composer").addEventListener("submit", (e) => { e.preventDefault(); send($("input").value); });
  $("input").addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey && !e.isComposing) { e.preventDefault(); send($("input").value); }
  });
  $("input").addEventListener("input", autosize);
  document.querySelectorAll("#suggestions .chip").forEach((c) => c.addEventListener("click", () => send(c.dataset.prompt)));
  $("new-chat").addEventListener("click", newChat);
  $("agent-select").addEventListener("change", (e) => setAgent(e.target.value));
  $("quick-ask").addEventListener("submit", (e) => {
    e.preventDefault();
    const q = $("quick-input").value.trim();
    if (!q) return;
    $("quick-input").value = "";
    location.hash = "chat";
    send(q);
  });

  $("new-folder").addEventListener("click", newFolder);
  $("folder-rename").addEventListener("click", async () => {
    const s = state.folders.find((x) => x.folder.id === state.folderId);
    const f = await folderSheet("Edit folder", s && s.folder);
    if (!f) return;
    try { await put("/api/folders/" + encodeURIComponent(state.folderId), f); toast("Folder saved"); loadFolder(state.folderId); } catch (e) { toast(e.message, "error"); }
  });
  $("folder-delete").addEventListener("click", async () => {
    if (!(await confirmDialog("Delete this folder?", "Its tasks and reminders are kept; they just won't be in a folder."))) return;
    try { await del("/api/folders/" + encodeURIComponent(state.folderId)); toast("Folder deleted"); location.hash = "home"; } catch (e) { toast(e.message, "error"); }
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
  $("custom-form").addEventListener("submit", createCustom);
  $("remote-form").addEventListener("submit", connectRemote);

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
  loadAgents().catch(() => { /* not signed in yet */ });
  showView(location.hash.slice(1));
  setInterval(() => { checkHealth(); refreshCounts(); }, 30000);
  if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});
});
