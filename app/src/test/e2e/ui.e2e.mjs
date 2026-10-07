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
  await sleep(450);
}
async function route(hash) {
  await js(`location.hash = ${q(hash)};`);
  await waitFor(`!document.getElementById("view-${hash.split("/")[0]}").hidden`, "view " + hash);
  await sleep(450);
}
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
const reply = (needle) => `[...document.querySelectorAll("#messages .msg.assistant")].some((t) => t.textContent.includes(${q(needle)}))`;

test("landing is the editing room: greets the owner, studio tiles, plus eleven features", async () => {
  await viewport(1440, 900, false, false);
  await go("home");
  await waitFor(`document.querySelectorAll(".feature-card").length === 11`, "feature cards");
  await sleep(1200);
  assert(/^(Hi .+\.|Good \w+\.|Working late\?\.)$/.test(await text("#greeting")), "greeting: " + (await text("#greeting")));
  assert((await text("#ask")).startsWith("What are we making today"), "question: " + (await text("#ask")));
  eq(await count("#studio-tiles .s-tile"), 4, "studio tiles");
  assert(await js(`return document.getElementById("edit-room").classList.contains("open");`), "doors opened");
  eq(await js(`return [...document.querySelectorAll("#studio-status .cap-pill")].map((c) => c.textContent.split(" · ")[0]).join(",");`),
    "Video renderer,Web research (Claude),Voice-over,Licensed photos", "studio plugins and connectors");
  assert(await js(`return document.querySelector(".strip.top").getBoundingClientRect().width === document.getElementById("edit-room").getBoundingClientRect().width;`), "film strip spans the room");
  eq(await count("#features-everyday .feature-card"), 4, "everyday features");
  eq(await count("#features-workspace .feature-card"), 7, "workspace features");
  eq(await js(`return [...document.querySelectorAll(".feature-card strong")].map((s) => s.textContent).join(",");`),
    "Optimal,Hard,Smooth,Focused,Research,Coding,Financial,Designing,Drawing,Image,Notes", "feature names");
  assert(await js(`return document.querySelector("#hero-helmet svg") !== null;`), "big helmet on the landing page");
  assert((await text("#day-summary")).includes("7 open tasks"), "day summary");
  eq(await js(`return document.getElementById("view-home").classList.contains("entering") || getComputedStyle(document.getElementById("view-home")).opacity === "1";`), true, "view shown");
  await shot("01-landing");
});

test("studio: asks what is missing, shows the plan, renders an MP4 and drops it in the chat", async () => {
  await viewport(1440, 900, false, false);
  await go("home");
  await waitFor(`!!document.getElementById("studio-input")`, "studio prompt");
  await js(`document.getElementById("studio-input").value = "Make a 10 sec Short about the water cycle"; document.querySelector("#studio-prompt button").click();`);
  await waitFor(`document.querySelectorAll(".studio-options .chip").length === 4`, "voice question with four answers");
  assert(await js(`return ${reply("voice reading the script")};`), "asks about voice and captions");
  await click('.studio-options .chip:nth-child(2)'); // Captions only
  await waitFor(`!!document.querySelector(".chip.option.primary:not([disabled])")`, "plan with Start");
  assert(await js(`return ${reply("10 sec Short on The water cycle")};`), "plan summary");
  eq(await count(".studio-plan li"), 1, "one video planned");
  await shot("02-studio-plan");
  await click(".chip.option.primary:not([disabled])");
  await waitFor(`!!document.querySelector(".studio-progress")`, "progress card");
  const ffmpeg = await js(`return (await (await fetch("/api/studio/status", { headers: { Authorization: "Bearer " + sessionStorage.getItem("weekend.token") } })).json()).render;`);
  if (ffmpeg) {
    await waitFor(`!!document.querySelector(".studio-progress .video-card video")`, "finished video in the chat", 180000);
    const src = await js(`return document.querySelector(".video-card video").getAttribute("src");`);
    assert(/^\/media\/[0-9a-f-]+\/video\.mp4\?exp=\d+&sig=/.test(src), "signed media link: " + src);
    const head = await js(`const r = await fetch(${q("")} + document.querySelector(".video-card video").getAttribute("src"), { headers: { Range: "bytes=0-15" } }); return r.status + " " + r.headers.get("content-type");`);
    eq(head, "206 video/mp4", "video streams with ranges");
    assert(await js(`return [...document.querySelectorAll(".video-card .v-actions a")].map((a) => a.textContent).join(",") === "MP4,Captions";`), "download buttons");
    await shot("03-studio-video");
    await go("library");
    await waitFor(`document.querySelectorAll("#lib-list .video-card").length >= 1`, "library lists the video");
  }
});

test("opening a feature shows its plugins, connectors, guidelines and settings", async () => {
  await click('.feature-card[data-feature="notes"]');
  await waitFor(`!document.getElementById("view-feature").hidden && document.getElementById("f-name").textContent === "Notes"`, "notes page");
  const caps = await js(`return [...document.querySelectorAll("#f-caps .cap")].map((c) => c.textContent);`);
  assert(caps[0].includes("Notion") && caps[0].includes("WEEKEND_NOTION_TOKEN") && caps[0].includes("Connector"), "Notion connector off with reason: " + caps[0]);
  eq(await js(`return document.querySelector("#f-caps .cap").classList.contains("off");`), true, "shown as off");
  assert(await count("#f-rules li") >= 3, "guidelines");
  eq(await count("#f-examples .chip"), 3, "example prompts");
  await route("feature/image");
  await waitFor(`document.getElementById("f-name").textContent === "Image"`, "image page");
  const imageCaps = await js(`return [...document.querySelectorAll("#f-caps .cap strong")].map((s) => s.textContent);`);
  eq(imageCaps.join(","), "Image analysis,Art studio,Memory", "image plugins");
  await shot("02-feature-image");
});

test("customise a feature: mode, sliders and my own instructions", async () => {
  await route("feature/coding");
  await waitFor(`document.getElementById("tune-name").textContent === "Coding" && document.querySelectorAll("#tune-modes .mode").length === 4`, "customise panel");
  eq(await js(`return document.getElementById("s-efficiency").value;`), "4", "coding defaults to High");
  await js(`document.querySelector('#tune-modes .mode[data-mode="FUNNY"]').click();`);
  eq(await js(`return document.getElementById("s-humor").value;`), "9", "mode sets sliders");
  assert(await js(`return document.getElementById("tune-preview").classList.contains("mood-happy");`), "happy preview");
  await type("#s-instructions", "Use pytest and type hints.");
  await click("#tune-save");
  await waitFor(`[...document.querySelectorAll("#toasts .toast")].some((t) => t.textContent.includes("Coding: Funny mode"))`, "saved");
  await click("#tune-reset");
  await waitFor(`document.getElementById("s-instructions").value === "" && document.getElementById("s-efficiency").value === "4"`, "reset");
  await shot("03-feature-customise");
});

test("an example prompt starts a chat in that feature", async () => {
  await route("feature/hard");
  await waitFor(`document.querySelectorAll("#f-examples .chip").length === 3`, "examples");
  await click("#f-examples .chip");
  await waitFor(`!document.getElementById("view-chat").hidden && document.getElementById("h-chat").textContent === "Hard"`, "chat in Hard");
  await waitFor(reply("x = 1, 2, 3"), "Hard solves the cubic");
  assert(await js(`return document.getElementById("companion").classList.contains("docked") && document.getElementById("companion").classList.contains("mood-serious");`), "Hard = focus mode: helmet docked, serious");
  await sleep(1200);
  const dock = await js(`const m = new DOMMatrix(getComputedStyle(document.getElementById("companion")).transform);
    const r = document.getElementById("composer").getBoundingClientRect(); return [m.m41 - r.right, m.m42 - r.bottom];`);
  assert(Math.abs(dock[0] - 14) < 20 && Math.abs(dock[1] + 92) < 20, "beside the chat box on wide screens: " + dock);
  await shot("04-chat-hard-focus");
});

test("switch feature from the chat header", async () => {
  await click("#feature-pill");
  await waitFor(`!document.getElementById("feature-menu").hidden`, "menu open");
  eq(await count("#feature-menu li"), 12, "all features in the menu");
  await js(`document.querySelector('#feature-menu li[data-id="optimal"]').click();`);
  await waitFor(`document.getElementById("h-chat").textContent === "Optimal" && document.getElementById("feature-menu").hidden`, "switched");
  eq(await js(`return document.getElementById("companion").classList.contains("docked");`), false, "Optimal is not focus mode");
  await click("#new-chat");
  await type("#input", "add task E2E task from chat");
  await submit("#composer");
  await waitFor(`document.querySelector("#messages .confirm-card")`, "task confirmation");
  await js(`document.querySelector("#messages .confirm-card .btn.primary").click();`);
  await waitFor(`document.querySelector("#messages .confirm-card.done")`, "approved");
});

test("image feature: attach a picture and Claude describes it", async () => {
  await route("feature/image");
  await waitFor(`document.getElementById("f-name").textContent === "Image"`, "image page");
  await click("#f-start");
  await waitFor(`!document.getElementById("view-chat").hidden && document.getElementById("h-chat").textContent === "Image"`, "image chat");
  eq(await js(`return document.getElementById("attach").hidden;`), false, "attach button shown");
  await js(`const c = document.createElement("canvas"); c.width = 40; c.height = 20; const g = c.getContext("2d"); g.fillStyle = "#ffffff"; g.fillRect(0, 0, 40, 20);
    const blob = await new Promise((r) => c.toBlob(r, "image/png"));
    const dt = new DataTransfer(); dt.items.add(new File([blob], "white.png", { type: "image/png" }));
    const input = document.getElementById("file"); input.files = dt.files; input.dispatchEvent(new Event("change"));`);
  await waitFor(`document.querySelectorAll("#thumbs .thumb").length === 1`, "thumbnail");
  await type("#input", "What is in this picture?");
  await submit("#composer");
  await waitFor(reply("PNG 40×20"), "pixel description");
  assert(await js(`return document.querySelectorAll("#messages .msg.user .msg-images img").length === 1;`), "image shown in my message");
  eq(await js(`return document.getElementById("thumbs").hidden;`), true, "attachments cleared after sending");
  await shot("05-chat-image");
});

test("drawing feature: original art renders and exports at 4K", async () => {
  await click("#feature-pill");
  await js(`document.querySelector('#feature-menu li[data-id="drawing"]').click();`);
  await click("#new-chat");
  await type("#input", "draw a sunset over the Western Ghats");
  await submit("#composer");
  await waitFor(`document.querySelector("#messages figure.art svg")`, "artwork card");
  eq(await js(`return document.querySelector("#messages figure.art svg").getAttribute("viewBox");`), "0 0 3840 2160", "4K viewBox");
  eq(await js(`return document.querySelectorAll("#messages figure.art script").length;`), 0, "sanitised");
  const size = await js(`const c = await WeekendArt.toCanvas(document.querySelector("#messages figure.art svg")); return [c.width, c.height];`);
  eq(size.join("x"), "3840x2160", "4K PNG canvas");
  await shot("06-chat-drawing-art");
});

test("research: web search connector stays off until approved", async () => {
  await route("feature/research");
  await waitFor(`document.getElementById("f-name").textContent === "Research"`, "research page");
  const web = await js(`return [...document.querySelectorAll("#f-caps .cap")].find((c) => c.textContent.includes("Web research")).textContent;`);
  assert(web.includes("WEEKEND_WEB_ALLOWED_HOSTS"), "web off reason: " + web);
  await click("#f-start");
  await type("#input", "search compound interest");
  await submit("#composer");
  await waitFor(reply("not available right now: web_search"), "refused while off");
});

test("your day: tiles, folders and up next", async () => {
  await route("day");
  await waitFor(`document.querySelectorAll("#folders .folder").length >= 6`, "folders");
  eq(await text('[data-tile="reminders"] b'), "5", "reminders tile");
  eq(await count("#tiles .tile"), 6, "tiles");
  eq(await count("#folders .folder-icon .mini"), 20, "four previews in each of the 5 folders");
  eq(await count("#up-next .row-card"), 5, "up next");
  await shot("07-your-day");
});

test("a folder opens with its items, and items can be added to it", async () => {
  await clickText("#folders .folder a", "Work");
  await waitFor(`!document.getElementById("view-folder").hidden && document.getElementById("h-folder").textContent === "Work"`, "work folder");
  await waitFor(`document.querySelectorAll("#folder-tasks .row-card").length === 3`, "work tasks");
  await type("#folder-task-title", "E2E folder task");
  await submit("#folder-task-form");
  await waitFor(`document.querySelectorAll("#folder-tasks .row-card").length === 4`, "added task");
});

test("a new folder is created from the dialog with a chosen icon", async () => {
  await route("day");
  await click("#new-folder");
  await waitFor(`document.getElementById("sheet").open`, "folder dialog");
  await type("#folder-name", "E2E Books");
  await js(`document.querySelector('#sheet .icon-picker button[aria-label="book"]').click();`);
  await dialogOk("sheet");
  await waitFor(`[...document.querySelectorAll("#folders strong")].some((s) => s.textContent === "E2E Books")`, "new folder tile");
});

test("tasks: add, complete, see it under Done, delete", async () => {
  await route("tasks");
  await waitFor(`document.querySelectorAll("#task-folder option").length > 1`, "folder options");
  await type("#task-title", "E2E task to finish");
  await submit("#task-form");
  await waitFor(`[...document.querySelectorAll("#task-groups .row-card strong")].some((s) => s.textContent === "E2E task to finish")`, "task row");
  await js(`[...document.querySelectorAll("#task-groups .row-card")].find((r) => r.textContent.includes("E2E task to finish")).querySelector(".check").click();`);
  await clickText("#task-filter button", "Done");
  await waitFor(`[...document.querySelectorAll("#task-groups .row-card.done strong")].some((s) => s.textContent === "E2E task to finish")`, "task under Done");
  await js(`[...document.querySelectorAll("#task-groups .row-card")].find((r) => r.textContent.includes("E2E task to finish")).querySelector(".icon-btn.danger").click();`);
  await dialogOk();
  await clickText("#task-filter button", "Open");
});

test("reminders: add for the future, cancel; past times are refused", async () => {
  await route("reminders");
  await type("#reminder-text", "E2E reminder");
  await js(`document.getElementById("reminder-due").value = "2099-05-01T09:30";`);
  await submit("#reminder-form");
  await waitFor(`[...document.querySelectorAll("#reminders-upcoming strong")].some((s) => s.textContent === "E2E reminder")`, "upcoming");
  await js(`[...document.querySelectorAll("#reminders-upcoming .rem")].find((r) => r.textContent.includes("E2E reminder")).querySelector("button").click();`);
  await dialogOk();
  await waitFor(`[...document.querySelectorAll("#reminders-past strong")].some((s) => s.textContent === "E2E reminder")`, "cancelled");
  await type("#reminder-text", "In the past");
  await js(`document.getElementById("reminder-due").value = "2000-01-01T09:00";`);
  await submit("#reminder-form");
  await waitFor(`[...document.querySelectorAll("#toasts .toast.error")].some((t) => t.textContent.includes("future"))`, "past-time error");
});

test("approvals and payments: approve, pay, refuse card numbers", async () => {
  await route("approvals");
  await waitFor(`document.querySelectorAll("#approval-list .row-card").length >= 2`, "approvals");
  await js(`[...document.querySelectorAll("#approval-list .row-card")].find((r) => r.textContent.includes("Payment to")).querySelector(".btn.primary").click();`);
  await waitFor(`[...document.querySelectorAll("#toasts .toast")].some((t) => t.textContent.includes("does not pay"))`, "never pays");
  await route("payments");
  await type("#payment-payee", "E2E Internet");
  await type("#payment-amount", "999.50");
  await submit("#payment-form");
  const row = `[...document.querySelectorAll("#payment-list .row-card")].find((r) => r.textContent.includes("E2E Internet"))`;
  await waitFor(`${row} && ${row}.textContent.includes("Needs approval")`, "pending payment");
  await js(`${row}.querySelector(".btn.primary").click();`);
  await waitFor(`${row}.textContent.includes("Mark paid")`, "approved");
  await type("#payment-payee", "Card test");
  await type("#payment-amount", "10");
  await type("#payment-note", "card 4111 1111 1111 1111");
  await submit("#payment-form");
  await waitFor(`[...document.querySelectorAll("#toasts .toast.error")].some((t) => t.textContent.includes("card numbers"))`, "card refused");
});

test("messages and notifications", async () => {
  await route("messages");
  await waitFor(`document.querySelectorAll("#message-list .row-card").length >= 2`, "messages");
  await route("notifications");
  await waitFor(`document.querySelectorAll("#notification-list .row-card").length > 0`, "notifications");
  await click("#read-all");
  await waitFor(`document.querySelector('[data-count="notifications"]').hidden`, "badge cleared");
});

test("helmet companion follows the mouse away from the landing page", async () => {
  await route("tasks");
  await cdp("Input.dispatchMouseEvent", { type: "mouseMoved", x: 500, y: 400 });
  await sleep(600);
  eq(await js(`return document.getElementById("companion").classList.contains("away");`), false, "visible");
  await cdp("Input.dispatchMouseEvent", { type: "mouseMoved", x: 1000, y: 600 });
  await sleep(900);
  const pos = await js(`const m = new DOMMatrix(getComputedStyle(document.getElementById("companion")).transform); return [m.m41, m.m42];`);
  assert(Math.abs(pos[0] - 1022) < 30 && Math.abs(pos[1] - 618) < 30, "next to the cursor: " + pos);
  await route("home");
  eq(await js(`return document.getElementById("companion").classList.contains("away");`), true, "landing has its own big helmet");
});

test("untrusted text is rendered as text, never as HTML", async () => {
  await route("tasks");
  await type("#task-title", '<img src=x onerror="window.__xss=1">');
  await submit("#task-form");
  await waitFor(`[...document.querySelectorAll("#task-groups strong")].some((s) => s.textContent.startsWith("<img"))`, "escaped task");
  eq(await js(`return document.querySelectorAll("#task-groups img").length;`), 0, "no injected <img>");
  eq(await js(`return window.__xss === undefined;`), true, "no script ran");
});

test("settings: theme, motion switch, delete-all needs the exact phrase", async () => {
  await route("settings");
  await waitFor(`document.getElementById("info-model").textContent !== "—"`, "info loaded");
  await clickText("#theme button", "Dark");
  eq(await js(`return document.documentElement.dataset.theme;`), "dark", "dark theme");
  await shot("08-settings-dark");
  await clickText("#theme button", "Auto");
  await js(`const t = document.getElementById("motion-toggle"); t.checked = false; t.dispatchEvent(new Event("change"));`);
  eq(await js(`return document.documentElement.classList.contains("no-motion");`), true, "animations off");
  await js(`const t = document.getElementById("motion-toggle"); t.checked = true; t.dispatchEvent(new Event("change"));`);
  await type("#delete-phrase", "delete all my data");
  eq(await js(`return document.getElementById("delete-btn").disabled;`), true, "wrong phrase keeps delete disabled");
  await type("#delete-phrase", "");
});

test("accessibility basics on every main screen", async () => {
  for (const v of ["home", "feature/coding", "chat", "day"]) {
    await route(v);
    await sleep(300);
    const unnamed = await js(`return [...document.querySelectorAll(".view:not([hidden]) :is(button, a[href], input:not([type=hidden]), select, textarea)")]
      .filter((e) => !(e.getAttribute("aria-label") || e.textContent.trim() || e.labels?.length || e.getAttribute("placeholder") || e.title || e.closest("label")))
      .map((e) => e.outerHTML.slice(0, 80));`);
    eq(unnamed.length, 0, v + ": unnamed controls " + JSON.stringify(unnamed));
  }
  eq(await js(`return [...document.images].filter((i) => !i.hasAttribute("alt")).length;`), 0, "images without alt");
  const dupes = await js(`const ids = [...document.querySelectorAll("[id]")].map((e) => e.id); return ids.filter((x, i) => ids.indexOf(x) !== i);`);
  eq(dupes.length, 0, "duplicate ids " + dupes);
});

test("phone layout: bottom tab bar and no sideways scrolling on any screen", async () => {
  await viewport(390, 844, true, false);
  await go("home");
  await sleep(800);
  eq(await js(`return [...document.querySelectorAll(".nav button")].filter((b) => getComputedStyle(b).display !== "none").length;`), 6, "visible tabs");
  await shot("09-phone-landing");
  for (const v of ["home", "feature/drawing", "feature/studio", "chat", "library", "day", "tasks", "reminders", "approvals", "payments", "messages", "notifications", "memories", "settings"]) {
    await route(v);
    const over = await js(`return document.documentElement.scrollWidth - window.innerWidth;`);
    assert(over <= 1, v + " overflows sideways by " + over + "px");
  }
  await viewport(390, 844, true, true);
  await route("feature/drawing");
  await sleep(500);
  await shot("10-phone-feature-dark");
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
