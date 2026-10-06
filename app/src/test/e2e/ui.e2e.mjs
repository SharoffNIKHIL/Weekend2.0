// app/src/test/e2e/ui.e2e.mjs — browser end-to-end tests for the Weekend PWA against the `local,ui` preview env.
// Drives headless Chrome over the DevTools protocol (Node 22+, no npm packages) and asserts on the real DOM.
//   1. cd app && java -jar target/weekend-assistant.jar --spring.profiles.active=local,ui      (http://127.0.0.1:8081)
//   2. node src/test/e2e/ui.e2e.mjs                       env: BASE_URL, CHROME, SHOTS_DIR (optional screenshots)
// Exit code 0 = all passed. Never point BASE_URL at a real environment: the tests create and delete data.
import { spawn } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const BASE = process.env.BASE_URL || "http://127.0.0.1:8081/";
const SHOTS = process.env.SHOTS_DIR || "";
const CHROME = process.env.CHROME || ["/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", "/usr/bin/google-chrome",
  "/usr/bin/google-chrome-stable", "/usr/bin/chromium", "/usr/bin/chromium-browser"].find((p) => existsSync(p));
if (!/^http:\/\/(127\.0\.0\.1|localhost)(:\d+)?\//.test(BASE)) {
  console.error("Refusing to run: BASE_URL must be a loopback preview env, got " + BASE);
  process.exit(2);
}
if (!CHROME) { console.error("Chrome not found; set CHROME"); process.exit(2); }
if (SHOTS) mkdirSync(SHOTS, { recursive: true });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const profile = mkdtempSync(join(tmpdir(), "weekend-e2e-"));
const port = 9300 + Math.floor(Math.random() * 500);
const chrome = spawn(CHROME, ["--headless=new", "--remote-debugging-port=" + port, "--no-first-run", "--no-default-browser-check",
  "--hide-scrollbars", "--user-data-dir=" + profile, ...(process.env.CI ? ["--no-sandbox"] : []), "about:blank"], { stdio: "ignore" });

let target;
for (let i = 0; i < 80 && !target; i++) {
  await sleep(250);
  try { target = (await (await fetch(`http://127.0.0.1:${port}/json`)).json()).find((t) => t.type === "page"); } catch { /* starting */ }
}
if (!target) { console.error("Chrome did not start"); chrome.kill(); process.exit(2); }

const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((r) => (ws.onopen = r));
let seq = 0;
const waiting = new Map();
const pageErrors = [];
ws.onmessage = (m) => {
  const d = JSON.parse(m.data);
  if (d.method === "Runtime.exceptionThrown") pageErrors.push(d.params.exceptionDetails.exception?.description || d.params.exceptionDetails.text);
  if (d.method === "Runtime.consoleAPICalled" && d.params.type === "error") pageErrors.push(d.params.args.map((a) => a.value ?? a.description).join(" "));
  // Network log: 4xx from /api is expected in negative tests (the UI shows a toast); anything else counts.
  if (d.method === "Log.entryAdded" && d.params.entry.level === "error") {
    const e = d.params.entry;
    const handled4xx = /status of 4\d\d/.test(e.text) && /\/api\//.test(e.url || "");
    if (!handled4xx) pageErrors.push(e.text + " " + (e.url || ""));
  }
  if (waiting.has(d.id)) { waiting.get(d.id)(d); waiting.delete(d.id); }
};
const cdp = (method, params = {}) => new Promise((r) => { const id = ++seq; waiting.set(id, r); ws.send(JSON.stringify({ id, method, params })); });
await cdp("Page.enable"); await cdp("Runtime.enable"); await cdp("Log.enable");

// ---------- helpers ----------
async function js(expr) {
  const r = await cdp("Runtime.evaluate", { expression: `(async () => { ${expr} })()`, awaitPromise: true, returnByValue: true });
  if (r.result.exceptionDetails) throw new Error("page JS failed: " + (r.result.exceptionDetails.exception?.description || r.result.exceptionDetails.text));
  return r.result.result.value;
}
const q = (sel) => JSON.stringify(sel);
async function waitFor(expr, what, timeout = 4000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    try { if (await js(`return !!(${expr});`)) return; } catch { /* not ready */ }
    await sleep(80);
  }
  throw new Error("timed out waiting for " + what);
}
async function viewport(width, height, mobile, dark) {
  await cdp("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 2, mobile });
  await cdp("Emulation.setEmulatedMedia", { features: [{ name: "prefers-color-scheme", value: dark ? "dark" : "light" }] });
}
async function go(hash) {
  await cdp("Page.navigate", { url: BASE + "#" + hash });
  await waitFor(`document.readyState === "complete" && !document.getElementById("view-${hash.split("/")[0]}").hidden`, "view " + hash);
  await sleep(350);
}
async function route(hash) { await js(`location.hash = ${q(hash)};`); await sleep(400); }
const click = (sel) => js(`const e = document.querySelector(${q(sel)}); if (!e) throw new Error("no element " + ${q(sel)}); e.click();`);
const clickText = (sel, text) => js(`const e = [...document.querySelectorAll(${q(sel)})].find((x) => x.textContent.trim().includes(${q(text)}));
  if (!e) throw new Error("no " + ${q(sel)} + " with text " + ${q(text)}); e.click();`);
const type = (sel, value) => js(`const e = document.querySelector(${q(sel)}); e.value = ${q(value)}; e.dispatchEvent(new Event("input", { bubbles: true }));`);
const submit = (sel) => js(`document.querySelector(${q(sel)}).requestSubmit();`);
const count = (sel) => js(`return document.querySelectorAll(${q(sel)}).length;`);
const text = (sel) => js(`const e = document.querySelector(${q(sel)}); return e ? e.textContent : null;`);
const lastToast = () => js(`const t = [...document.querySelectorAll("#toasts .toast")].pop(); return t ? t.textContent : "";`);
async function dialogOk(id = "confirm") { await waitFor(`document.getElementById(${q(id)}).open`, id + " dialog"); await js(`document.querySelector("#${id} button[value=ok]").click();`); await sleep(300); }
async function shot(name) {
  if (!SHOTS) return;
  const r = await cdp("Page.captureScreenshot", { format: "png" });
  writeFileSync(join(SHOTS, name + ".png"), Buffer.from(r.result.data, "base64"));
}
function assert(cond, msg) { if (!cond) throw new Error(msg); }
const eq = (a, b, msg) => assert(a === b, `${msg}: expected ${JSON.stringify(b)}, got ${JSON.stringify(a)}`);

// ---------- tests ----------
const tests = [];
const test = (name, fn) => tests.push([name, fn]);

test("home shows greeting, six category tiles with counts, folders and up next", async () => {
  await viewport(1440, 900, false, false);
  await go("home");
  await waitFor(`document.querySelectorAll("#folders .folder").length >= 6`, "folders");
  assert(/Good|Working/.test(await text("#greeting")), "greeting");
  eq(await count("#tiles .tile"), 6, "tiles");
  eq(await text('[data-tile="tasks"] b'), "7", "open tasks tile");
  eq(await text('[data-tile="reminders"] b'), "5", "upcoming reminders tile");
  eq(await text('[data-tile="approvals"] b'), "2", "approvals tile");
  assert(await js(`return document.querySelector('[data-tile="approvals"]').classList.contains("hot");`), "approvals tile is highlighted");
  eq(await count("#folders .folder a"), 5, "five folders");
  eq(await count("#folders .folder-icon .mini"), 20, "each folder icon shows four mini slots");
  eq(await count("#up-next .row-card"), 5, "up next rows");
  eq(await js(`return document.querySelector(".brand img").getAttribute("src");`), "logo.svg", "helmet logo in the sidebar");
  await shot("01-home");
});

test("a folder opens with its items, and items can be added to it", async () => {
  await clickText("#folders .folder a", "Work");
  await waitFor(`!document.getElementById("view-folder").hidden && document.getElementById("h-folder").textContent === "Work"`, "work folder");
  await waitFor(`document.querySelectorAll("#folder-tasks .row-card").length === 3`, "work tasks");
  eq(await count("#folder-reminders .rem"), 2, "work reminders");
  await type("#folder-task-title", "E2E folder task");
  await submit("#folder-task-form");
  await waitFor(`document.querySelectorAll("#folder-tasks .row-card").length === 4`, "added task");
  await shot("02-folder");
});

test("a new folder is created from the dialog with a chosen icon", async () => {
  await route("home");
  await click("#new-folder");
  await waitFor(`document.getElementById("sheet").open`, "folder dialog");
  await type("#folder-name", "E2E Books");
  await js(`document.querySelector('#sheet .icon-picker button[aria-label="book"]').click();`);
  await dialogOk("sheet");
  await waitFor(`[...document.querySelectorAll("#folders strong")].some((s) => s.textContent === "E2E Books")`, "new folder tile");
  assert(await js(`return [...document.querySelectorAll("#folders .folder a")].find((a) => a.textContent.includes("E2E Books")).querySelector("use").getAttribute("href") === "#i-book";`), "book icon");
});

test("tasks: add with folder and priority, complete, see it under Done, delete", async () => {
  await route("tasks");
  await waitFor(`document.querySelectorAll("#task-folder option").length > 1`, "folder options");
  await type("#task-title", "E2E task to finish");
  await js(`const s = document.getElementById("task-folder"); s.value = [...s.options].find((o) => o.text === "Home").value;
    document.getElementById("task-priority").value = "HIGH";`);
  await submit("#task-form");
  await waitFor(`[...document.querySelectorAll("#task-groups .row-card strong")].some((s) => s.textContent === "E2E task to finish")`, "task row");
  await js(`[...document.querySelectorAll("#task-groups .row-card")].find((r) => r.textContent.includes("E2E task to finish")).querySelector(".check").click();`);
  await waitFor(`![...document.querySelectorAll("#task-groups strong")].some((s) => s.textContent === "E2E task to finish")`, "task leaves Open");
  await clickText("#task-filter button", "Done");
  await waitFor(`[...document.querySelectorAll("#task-groups .row-card.done strong")].some((s) => s.textContent === "E2E task to finish")`, "task under Done");
  await js(`[...document.querySelectorAll("#task-groups .row-card")].find((r) => r.textContent.includes("E2E task to finish")).querySelector(".icon-btn.danger").click();`);
  await dialogOk();
  await waitFor(`![...document.querySelectorAll("#task-groups strong")].some((s) => s.textContent === "E2E task to finish")`, "task deleted");
  await clickText("#task-filter button", "Open");
});

test("reminders: add for the future, cancel through the confirm dialog", async () => {
  await route("reminders");
  await type("#reminder-text", "E2E reminder");
  await js(`document.getElementById("reminder-due").value = "2099-05-01T09:30";`);
  await submit("#reminder-form");
  await waitFor(`[...document.querySelectorAll("#reminders-upcoming strong")].some((s) => s.textContent === "E2E reminder")`, "upcoming reminder");
  assert((await text("#reminders-upcoming")).includes("9:30"), "IST time shown");
  await js(`[...document.querySelectorAll("#reminders-upcoming .rem")].find((r) => r.textContent.includes("E2E reminder")).querySelector("button").click();`);
  await dialogOk();
  await waitFor(`[...document.querySelectorAll("#reminders-past strong")].some((s) => s.textContent === "E2E reminder")`, "cancelled reminder");
  await type("#reminder-text", "In the past");
  await js(`document.getElementById("reminder-due").value = "2000-01-01T09:00";`);
  await submit("#reminder-form");
  await waitFor(`[...document.querySelectorAll("#toasts .toast.error")].some((t) => t.textContent.includes("future"))`, "past-time error");
});

test("approvals: approve the payment, decline the tool call, counts update", async () => {
  await route("approvals");
  await waitFor(`document.querySelectorAll("#approval-list .row-card").length === 2`, "two approvals");
  await js(`[...document.querySelectorAll("#approval-list .row-card")].find((r) => r.textContent.includes("Payment to")).querySelector(".btn.primary").click();`);
  await waitFor(`document.querySelectorAll("#approval-list .row-card").length === 1`, "one approval left");
  assert((await lastToast()).includes("does not pay"), "payment approval explains Weekend never pays");
  await js(`document.querySelector("#approval-list .row-card .btn:not(.primary)").click();`);
  await waitFor(`document.querySelector("#approval-list .empty")`, "no approvals");
  await waitFor(`document.querySelector('[data-count="approvals"]').hidden`, "approvals badge cleared");
  await shot("03-approvals-empty");
});

test("payments: request, approve, mark paid; card numbers are refused", async () => {
  await route("payments");
  await type("#payment-payee", "E2E Internet");
  await type("#payment-amount", "999.50");
  await js(`document.getElementById("payment-due").value = "2099-02-01";`);
  await submit("#payment-form");
  await waitFor(`[...document.querySelectorAll("#payment-list .row-card")].some((r) => r.textContent.includes("E2E Internet") && r.textContent.includes("Needs approval"))`, "pending payment");
  assert((await text("#payment-list")).includes("₹999.50"), "INR amount");
  const row = `[...document.querySelectorAll("#payment-list .row-card")].find((r) => r.textContent.includes("E2E Internet"))`;
  await js(`${row}.querySelector(".btn.primary").click();`);
  await waitFor(`${row}.textContent.includes("Mark paid")`, "approved payment");
  await js(`${row}.querySelector(".btn.primary").click();`);
  await waitFor(`${row}.textContent.includes("Paid") && ${row}.classList.contains("muted")`, "paid payment");
  await type("#payment-payee", "Card test");
  await type("#payment-amount", "10");
  await type("#payment-note", "card 4111 1111 1111 1111");
  await submit("#payment-form");
  await waitFor(`[...document.querySelectorAll("#toasts .toast.error")].some((t) => t.textContent.includes("card numbers"))`, "card number refused");
  await shot("04-payments");
});

test("messages: unread messages from agents, mark read, delete", async () => {
  await route("messages");
  await waitFor(`document.querySelectorAll("#message-list .row-card").length >= 2`, "messages");
  const unread = await count("#message-list .row-card.unread");
  assert(unread >= 2, "unread messages");
  await click("#message-list .row-card.unread");
  await waitFor(`document.querySelectorAll("#message-list .row-card.unread").length === ${unread - 1}`, "one marked read");
  const before = await count("#message-list .row-card");
  await click("#message-list .row-card .icon-btn.danger");
  await waitFor(`document.querySelectorAll("#message-list .row-card").length === ${before - 1}`, "message deleted");
});

test("notifications: list, open one, mark all read", async () => {
  await route("notifications");
  await waitFor(`document.querySelectorAll("#notification-list .row-card").length > 0`, "notifications");
  await click("#read-all");
  await waitFor(`document.querySelectorAll("#notification-list .row-card.unread").length === 0`, "all read");
  await waitFor(`document.querySelector('[data-count="notifications"]').hidden`, "badge cleared");
});

test("agents: built-in, config and demo agents; create a custom agent with conditions", async () => {
  await route("agents");
  await waitFor(`document.querySelectorAll("#agent-list .agent").length >= 2`, "agent cards");
  const names = await js(`return [...document.querySelectorAll("#agent-list h3")].map((h) => h.textContent);`);
  assert(names.includes("Weekend") && names.includes("Demo agent"), "Weekend and Demo agent listed: " + names);
  assert(await js(`return document.querySelector('#agent-list .agent[data-id="weekend"]').classList.contains("active");`), "Weekend active by default");
  eq(await js(`return document.getElementById("remote-off").hidden;`), true, "remote agents allowed on loopback");
  await type("#custom-name", "E2E Clock");
  await type("#custom-instructions", "Only tell the time.");
  await js(`document.querySelectorAll("#custom-conditions [data-tool]").forEach((c) => (c.checked = c.value === "current_time"));
    document.querySelector("#custom-conditions [data-confirm]").checked = true;`);
  await submit("#custom-form");
  await waitFor(`[...document.querySelectorAll("#agent-list h3")].some((h) => h.textContent === "E2E Clock")`, "custom agent card");
  const chips = await js(`return [...document.querySelectorAll("#agent-list .agent")].find((a) => a.textContent.includes("E2E Clock")).querySelector(".conds").textContent;`);
  assert(chips.includes("Tools: current_time") && chips.includes("Every action waits for your yes"), "condition chips: " + chips);
  await shot("05-agents");
});

test("instructions open in a dialog as plain text", async () => {
  await js(`[...document.querySelectorAll("#agent-list .agent")].find((a) => a.textContent.includes("E2E Clock")).querySelector(".btn.ghost").click();`);
  await waitFor(`document.getElementById("sheet").open && document.querySelector("#sheet pre")`, "instructions dialog");
  eq(await text("#sheet pre"), "Only tell the time.", "instructions text");
  await js(`document.getElementById("sheet-cancel").click();`);
});

test("chat with the custom agent: even a read tool waits for approval", async () => {
  await js(`[...document.querySelectorAll("#agent-list .agent")].find((a) => a.textContent.includes("E2E Clock")).querySelector(".btn.primary").click();`);
  await waitFor(`!document.getElementById("view-chat").hidden`, "chat view");
  eq(await js(`return document.getElementById("agent-select").selectedOptions[0].text;`), "E2E Clock", "agent picker");
  await type("#input", "what time is it?");
  await submit("#composer");
  await waitFor(`document.querySelector("#messages .confirm-card")`, "confirmation card");
  assert((await text("#messages .confirm-card")).includes("current_time"), "pending current_time");
  await js(`document.querySelector("#messages .confirm-card .btn.primary").click();`);
  await waitFor(`document.querySelector("#messages .confirm-card.done")`, "approved");
});

test("chat with the remote demo agent travels over loopback only after approval", async () => {
  await js(`const s = document.getElementById("agent-select"); s.value = [...s.options].find((o) => o.text === "Demo agent").value; s.dispatchEvent(new Event("change"));`);
  await click("#new-chat");
  await type("#input", "Hello demo agent");
  await submit("#composer");
  await waitFor(`document.querySelector("#messages .confirm-card")`, "remote confirmation");
  const summary = await text("#messages .confirm-card p");
  assert(summary.includes("127.0.0.1") && summary.includes("leaves Weekend"), "summary names the host: " + summary);
  await js(`document.querySelector("#messages .confirm-card .btn.primary").click();`);
  await waitFor(`(document.querySelector("#messages .confirm-card.done p.hint") || {}).textContent?.includes("Demo agent here")`, "demo agent reply", 8000);
  await shot("06-chat-remote");
});

test("default agent: 'add task' needs approval and then shows up in Tasks", async () => {
  await js(`const s = document.getElementById("agent-select"); s.value = "weekend"; s.dispatchEvent(new Event("change"));`);
  await click("#new-chat");
  await type("#input", "add task E2E task from chat");
  await submit("#composer");
  await waitFor(`document.querySelector("#messages .confirm-card")`, "task confirmation");
  await js(`document.querySelector("#messages .confirm-card .btn.primary").click();`);
  await waitFor(`document.querySelector("#messages .confirm-card.done")`, "approved");
  await route("tasks");
  await waitFor(`[...document.querySelectorAll("#task-groups strong")].some((s) => s.textContent === "E2E task from chat")`, "task from chat");
});

test("home quick-ask sends the question to chat", async () => {
  await route("home");
  await type("#quick-input", "What time is it?");
  await submit("#quick-ask");
  await waitFor(`!document.getElementById("view-chat").hidden && [...document.querySelectorAll("#messages .msg.user")].some((m) => m.textContent.includes("What time is it?"))`, "question in chat");
});

test("untrusted text is rendered as text, never as HTML", async () => {
  await route("tasks");
  await type("#task-title", '<img src=x onerror="window.__xss=1">');
  await submit("#task-form");
  await waitFor(`[...document.querySelectorAll("#task-groups strong")].some((s) => s.textContent.startsWith("<img"))`, "escaped task");
  eq(await js(`return document.querySelectorAll("#task-groups img").length;`), 0, "no injected <img>");
  eq(await js(`return window.__xss === undefined;`), true, "no script ran");
});

test("settings: dark theme toggle, retention values, delete-all needs the exact phrase", async () => {
  await route("settings");
  await waitFor(`document.getElementById("info-model").textContent !== "—"`, "info loaded");
  eq(await text("#info-location"), "this device (offline model)", "processing location");
  eq(await js(`return document.querySelector('#view-settings [data-ret="notifications"]').textContent;`), "30", "notification retention");
  await clickText("#theme button", "Dark");
  eq(await js(`return document.documentElement.dataset.theme;`), "dark", "dark theme");
  await shot("07-settings-dark");
  await clickText("#theme button", "Auto");
  eq(await js(`return document.documentElement.dataset.theme === undefined;`), true, "auto theme");
  await type("#delete-phrase", "delete all my data");
  eq(await js(`return document.getElementById("delete-btn").disabled;`), true, "wrong phrase keeps delete disabled");
  await type("#delete-phrase", "");
});

test("accessibility basics: every control has a name, images have alt, ids are unique", async () => {
  await go("home");
  const unnamed = await js(`return [...document.querySelectorAll("button, a[href], input:not([type=hidden]), select, textarea")]
    .filter((e) => !(e.getAttribute("aria-label") || e.textContent.trim() || e.labels?.length || e.getAttribute("placeholder") || e.title || e.closest("label")))
    .map((e) => e.outerHTML.slice(0, 80));`);
  eq(unnamed.length, 0, "unnamed controls " + JSON.stringify(unnamed));
  eq(await js(`return [...document.images].filter((i) => !i.hasAttribute("alt")).length;`), 0, "images without alt");
  const dupes = await js(`const ids = [...document.querySelectorAll("[id]")].map((e) => e.id); return ids.filter((x, i) => ids.indexOf(x) !== i);`);
  eq(dupes.length, 0, "duplicate ids " + dupes);
});

test("phone layout: bottom tab bar with five items and no sideways scrolling on any screen", async () => {
  await viewport(390, 844, true, false);
  await go("home");
  eq(await js(`return [...document.querySelectorAll(".nav button")].filter((b) => getComputedStyle(b).display !== "none").length;`), 5, "visible tabs");
  eq(await js(`return getComputedStyle(document.querySelector(".sidebar")).position;`), "fixed", "bottom bar");
  await shot("08-phone-home");
  for (const v of ["home", "chat", "agents", "tasks", "reminders", "approvals", "payments", "messages", "notifications", "memories", "settings"]) {
    await route(v);
    const over = await js(`return document.documentElement.scrollWidth - window.innerWidth;`);
    assert(over <= 1, v + " overflows sideways by " + over + "px");
  }
  await viewport(390, 844, true, true);
  await route("agents");
  await shot("09-phone-agents-dark");
  await route("payments");
  await shot("10-phone-payments-dark");
  await viewport(1440, 900, false, false);
});

test("no JavaScript errors were logged during the run", async () => {
  eq(pageErrors.length, 0, "page errors " + JSON.stringify(pageErrors));
});

// ---------- run ----------
let failed = 0;
const started = Date.now();
for (const [name, fn] of tests) {
  try {
    await fn();
    console.log("ok   - " + name);
  } catch (e) {
    failed++;
    console.log("FAIL - " + name + "\n       " + e.message);
    await shot("FAIL-" + name.replace(/[^a-z0-9]+/gi, "-").slice(0, 40));
  }
}
console.log(`\n${tests.length - failed}/${tests.length} UI tests passed in ${((Date.now() - started) / 1000).toFixed(1)} s`);
ws.close();
chrome.kill();
process.exit(failed ? 1 : 0);
