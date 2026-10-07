# Weekend 2.0 — Application (Java)

The assistant itself: agent core, tools (plugins), memory, reminders, retention, export/delete, the HTTP API and the installable web app (PWA). It runs on **Cloud Run** (`weekend2-dev-api` / `-worker`) and locally on your Mac.

> **Branch:** `Feature_code` → PR into `dev`. **Status:** application core done and tested with in-memory adapters. Firestore (`Feature_database`) and Cloud Tasks / entry-node proxy (`Feature_networking`) come next (see [docs/BRANCHING.md](../docs/BRANCHING.md)).

## Prerequisites
| Tool | Version | Check |
|---|---|---|
| JDK | 17+ (Temurin 17 in CI and the container) | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Docker (optional) | current | `docker version` |
| Google Cloud SDK (only for the `vertex` provider) | current | `gcloud --version` |

## Run locally (offline model, no cloud, no session)
```bash
cd app
mvn spring-boot:run -Dspring-boot.run.profiles=local
# open http://127.0.0.1:8080  (the local profile binds to loopback only)
```
Try "what time is it?", "Remember that I prefer filter coffee", "what do you know about coffee", "remind me to rotate the key".

Expected log line: `Started WeekendApplication in 0.7 seconds`.

## UI preview env (design review, offline)
```bash
cd app
mvn spring-boot:run -Dspring-boot.run.profiles=local,ui
# open http://127.0.0.1:8081  (loopback only; port 8081 so it can run beside `local` on 8080)
```
The `ui` profile seeds made-up demo memories and reminders (in memory only, gone on restart) so every screen can be reviewed populated. It refuses to start unless the offline model is active and sessions are off, so it can never run against real data. Deep links: `#chat`, `#memories`, `#reminders`, `#settings`. Screenshots: [`docs/ui/`](../docs/ui/).

**UI v3** (`resources/static/`), one brand colour (Electric Blue `#1D5BFF`, WCAG AA with white text; `#6E9BFF` in dark mode) and an original armoured-helmet mascot (`logo.svg`, `icon.svg`):
- **Home:** greeting, quick ask, six category tiles (Reminders, Tasks, Approvals, Payments, Messages, Notifications) with live counts, folder icons that preview up to four items each, and "Up next".
- **Folders** group tasks and reminders (`#folder/<id>`). Deleting a folder keeps its items.
- **Tasks, Reminders, Approvals, Payments, Messages, Notifications, Memories, Settings** screens; sidebar on desktop, five-tab bottom bar on phones; light/dark/auto.
- **Features:** pick what Weekend is for (Optimal, Research, Coding, Drawing, Image, Notes …); each feature page shows its plugins, connectors, guidelines and Customise panel.
- **Payments are tracking only.** Weekend never moves money and refuses card numbers (Luhn-checked).

The `ui` profile also loads the **Project agent** from `~/CLAUDE.md` (override with `WEEKEND_PROJECT_AGENT_INSTRUCTIONS`) and connects a loopback **Demo agent** (`/demo-agent`), so remote-agent chat can be tried without anything leaving the Mac. Screenshots: [`docs/ui/`](../docs/ui/).

## Personality, modes and focus mode
Every feature has a **persona**, tuned in its Customise panel or from the chat box:
| Setting | Range | What it changes |
|---|---|---|
| Humor | 0–10 | Tone guidance; raises the sampling temperature |
| Truth | 0–10 | High = facts only, says "I don't know"; lowers the temperature |
| Focus | 0–10 | High = shortest on-task answer, no tangents |
| Efficiency | Eco · Balanced · Standard · High · Max | Model (High/Max = strong), tool steps 2–10, answer length 512–4096 tokens, memories 4–16, history 8–40. High/Max = **focus mode**: the helmet flies to the chat box, turns serious and shows FOCUS. Costs more; the daily cap still applies |
| Search range | Off · Memory · Web · Wide | Which lookup tools exist (Wide = 6 results + summaries) |
| Approval range | All · Writes and external · Writes only | What waits for your yes. Writes always ask |

Modes are presets: **Funny** (happy face), **Disciplined** (serious, asks before every tool), **Work** (calm, default), **Browse** (curious, web searches pre-approved). Moving a slider makes it Custom.

## Math and web
- `math_evaluate`, `math_solve`, `math_stats`: local and exact (34-digit decimals, big-integer factorials, numeric root finding, statistics). No network. Limits keep hostile input cheap.
- `web_search`: MediaWiki API (Wikipedia by default). **Off until `WEEKEND_WEB_ALLOWED_HOSTS` lists a host** (security checkpoint; 🔓 queries leave Weekend). Secrets are redacted from queries, no redirects, 10 s timeout, 256 KiB cap; results are DATA.

## Your profile and private brand pack (local only, never committed)
- `WEEKEND_OWNER_PROFILE` (ui profile: `~/.weekend/owner-profile.md`): lines `- fact: …`, `- pref: …`, `- task: …` become **pinned memories** at start-up and are always in the agent's context. 🔓 With Vertex AI they are sent with each prompt.
- `WEEKEND_BRAND_DIR` (ui profile: `~/.weekend/brand/`): `logo.svg`, `icon.svg`, `companion.svg` (with `data-mood` groups happy/calm/serious/curious) replace the public assets; `/brand.json` tells the UI what exists. SVGs are sanitised before use.

## High pressure
At most `WEEKEND_MAX_CONCURRENT_CHATS` (default 4) chat turns run at once; others wait up to 10 s, then get HTTP 503 with `Retry-After`. Model errors are retried twice with exponential backoff (400 ms, 800 ms). The daily cost cap still applies.

## Features (one agent, many behaviours)
Weekend is **one agent: yours**. Home greets you by name ("Hi <name>." from the `- name:` line of your private profile) and asks which feature to explore:
| Group | Features | What changes |
|---|---|---|
| How I think | Optimal · Hard · Smooth · Focused | Persona (humor, truth, focus, efficiency), guidelines, model and budget |
| Workspaces | Research · Coding · Financial · Designing · Drawing · Image · Notes | Guidelines **plus the attached plugins and connectors** |

Plugins run inside Weekend (Clock, Memory, Tasks, Math, Payments, **Image analysis**, **Art studio**); connectors talk to outside services (🔓 **Web research**, **Notion**) and stay off until configured after a security checkpoint. Each feature page shows what is attached, why something is off, its guidelines, example prompts and a **Customise** panel (mode, sliders, ranges, your own extra instructions). Your settings are exported and deleted with your data (P6).

- **Images (Claude vision):** attach up to 4 PNG/JPEG/WebP/GIF images (≤ 5 MB; large ones are scaled to 1568 px in the browser). They go to Claude for that turn only and are never stored (P5); 🔓 with Vertex they leave Weekend like the prompt.
- **Art (Claude only):** Claude cannot paint pixels; Drawing/Designing/Image ask it for **original SVG**, which the app sanitises and renders, with **4K PNG** (3840 px canvas) and SVG download.
- **Notion connector:** `WEEKEND_NOTION_TOKEN` (internal integration secret, Secret Manager) + `WEEKEND_NOTION_PARENT_PAGE`; search pages and save notes, both asking first. Notion-Version 2022-06-28 (verify on first enable).

## Weekend Studio (YouTube videos)
Ask in any chat, or from the home page's editing room: *"create me a video on Kubernetes"*, *"15 × 45 sec Shorts covering the
whole of Kubernetes"*, *"build me a 2 min video about the arctic fox"*. Weekend asks only what is missing (length or series size,
voice and captions), shows the plan, and renders only after you tap **Start**. Each finished video appears in the chat with a
player, **MP4** and **captions (.srt)** downloads, and a ready title and description (sources and photo credits included).
The **Studio** tab lists every project (kept 30 days).

| Part | Default | Turn on (after its security checkpoint, ADR-0003) |
|---|---|---|
| Renderer (ffmpeg) | on if `ffmpeg` is installed (`brew install ffmpeg`) | `WEEKEND_FFMPEG` for another path |
| Scripts | offline drafts (labelled) | Claude: `WEEKEND_LLM_PROVIDER=vertex` |
| Web research | off | `WEEKEND_STUDIO_WEB_SEARCHES=3` (Claude web search, paid per search, 🔓 S2) |
| Voice | none (`say` in the UI env: drafts only, not for monetised uploads) | `WEEKEND_TTS=google` (Cloud TTS, 🔓 S1) |
| Photos | off | add `commons.wikimedia.org,upload.wikimedia.org` to `WEEKEND_WEB_ALLOWED_HOSTS` (🔓 S3) |

Output: H.264 High + AAC 48 kHz, `+faststart`; Shorts 1080×1920, videos 1920×1080; media in `WEEKEND_STUDIO_MEDIA`
(UI env: `~/.weekend/studio`). API: `GET /api/studio/projects`, `GET|DELETE /api/studio/projects/{id}`, `GET /api/studio/status`;
files via signed `/media/{job}/{file}?exp=…&sig=…` links (6 h).

## Configuration (environment variables)
| Variable | Default | Meaning |
|---|---|---|
| `WEEKEND_LLM_PROVIDER` | `local` (`vertex` in the container) | `local` = offline scripted model; `vertex` = Claude on Vertex AI |
| `GCP_PROJECT` | — | Project for Vertex AI (set by Terraform on Cloud Run) |
| `VERTEX_LOCATION` | `global` | 🔓 `global` may process prompts outside India (DESIGN §7.4) |
| `MODEL_DEFAULT` / `MODEL_STRONG` | `claude-haiku-4-5@20251001` / `claude-sonnet-5` | Vertex model IDs |
| `WEEKEND_SESSION_KEY` | — | ≥ 32 chars, from Secret Manager `<prefix>-app-session-signing-key`. **Required**: the app refuses to start without it unless sessions are disabled |
| `WEEKEND_REQUIRE_SESSION` | `true` | Only the `local` profile turns it off |
| `LOG_LEVEL` | `INFO` | Message content is never logged |
| `PORT` | `8080` | Set by Cloud Run |

There's no `.env` file: real secrets live in Secret Manager and reach Cloud Run as environment variables.

## Project layout
```
app/
├── pom.xml                         # Spring Boot 4.1.1, Java 17, Anthropic Java SDK 2.68.0
├── Dockerfile                      # multi-stage, pinned digests, non-root
└── src/main/java/com/weekend/assistant/
    ├── WeekendApplication.java
    ├── config/      WeekendProperties (all settings), LlmConfig (local|vertex), ClockConfig
    ├── domain/      Conversation, Message, Memory, Reminder, ToolCallRecord, AuditEntry (records)
    ├── port/        interfaces: LlmProvider, *Repository, ReminderScheduler, AuditLog
    ├── adapter/
    │   ├── memory/  in-memory repositories, hash-chained audit log, logging scheduler (dev/tests)
    │   └── llm/     ScriptedLlmProvider (offline), VertexClaudeProvider (Claude on Vertex AI)
    ├── agent/       AgentService (tool loop + confirmation gate + cost cap), ModelRouter, ContextBuilder, CostCalculator
    ├── tools/       Tool (plugin contract), ToolRegistry (allow-list), current_time, memory_search, memory_save, reminder_create
    ├── memory/      MemoryService (no secrets, retention, pin, delete)
    ├── reminder/    ReminderService
    ├── retention/   RetentionService (P5), DataService (P6 export / delete-all)
    ├── security/    SecretFilter (P4), SessionTokenService + OwnerSessionFilter (P1 layer 2)
    └── web/         Chat, Memory, Reminder, Data, Jobs, Health controllers + JSON error handler
    resources/static/  PWA: index.html, app.js, styles.css, manifest, service worker (shell only)
```

## How it works
1. `POST /api/chat` → `OwnerSessionFilter` checks the signed session token.
2. `AgentService` redacts secrets, saves the user turn, extracts "remember that …" memories, retrieves relevant memories, and picks a model (`ModelRouter`).
3. Agent loop (max 5 tool steps). **Read tools** run at once, and their output is wrapped in `<data>` tags so the model treats it as data, not instructions. **Write tools** (`reminder_create`) stop and return a `pendingConfirmation`; nothing changes until the owner calls `POST /api/confirm/{id}`.
4. Reply, tokens and cost are stored. A daily cost cap (default USD 0.62 ≈ ₹60) returns HTTP 429 when reached. Every change writes a hash-chained audit entry (P8).

### Adding a tool (plugin)
Implement `tools.Tool` as a Spring `@Component`: give it a `name()` (`^[a-z][a-z0-9_]{2,63}$`), a JSON-Schema `inputSchema()`, `writes()` (true = needs confirmation) and `execute()`. The registry picks it up automatically and rejects duplicate or badly formed names. Add a unit test.

## API
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/chat` | `{message, conversationId?, thinkHarder?, featureId?, images?}` → reply, model, tools used, pending confirmation, cost |
| GET / POST | `/api/pending`, `/api/confirm/{id}` | List / answer write-tool confirmations (`{approved}`) |
| GET / POST / DELETE | `/api/memories`, `/api/memories/{id}/pin`, `/api/memories/{id}` | View, pin, delete memories (P6) |
| GET / DELETE | `/api/reminders`, `/api/reminders/{id}` | List / cancel reminders |
| GET / POST | `/api/export`, `/api/delete-all` | Export everything; delete all with `{"confirmation":"DELETE ALL MY DATA"}` |
| POST | `/jobs/retention`, `/jobs/export`, `/jobs/reminders/{id}/deliver` | Worker jobs (Cloud Scheduler / Cloud Tasks; IAM-protected on Cloud Run) |
| GET | `/api/home` | Category counts, folder summaries (with previews), next 5 due items |
| GET / POST / PUT / DELETE | `/api/folders`, `/api/folders/{id}`, `/api/folders/icons` | Folders for tasks and reminders |
| GET / POST / DELETE | `/api/tasks`, `/api/tasks/{id}/complete`, `/reopen`, `PUT /api/tasks/{id}/folder` | Tasks (`dueLocal` = IST local time) |
| POST / PUT | `/api/reminders`, `/api/reminders/{id}/folder` | Owner-created reminders, move between folders |
| GET / POST | `/api/approvals`, `/api/approvals/{tool\|payment}/{id}` | One queue for tool calls and payments waiting on you |
| GET / POST / DELETE | `/api/payments`, `/api/payments/{id}/decision`, `/paid` | Payment tracking (never pays) |
| GET / POST / DELETE | `/api/messages`, `/api/notifications`, `/read`, `/read-all` | Agent messages and in-app notifications |
| GET / PUT / POST | `/api/features`, `/{id}`, `/{id}/persona`, `/{id}/instructions`, `/{id}/reset` | Features with plugin/connector status; your per-feature settings |
| GET | `/api/me` | Your name and a few profile facts for the greeting |
| GET | `/brand.json` | Which private brand files exist (no content) |
| GET | `/api/info` | Non-secret settings for the Settings screen: models, processing location, retention, cost cap |
| GET | `/healthz` | Liveness |

## Testing
```bash
cd app && mvn -B verify     # 219 JUnit tests: unit, HTTP integration (sessions on), real-port end to end (offline, no cloud)

# Browser end-to-end (21 tests, headless Chrome, Node 22+, no npm packages):
java -jar target/weekend-assistant.jar --spring.profiles.active=local,ui &   # loopback preview env
node src/test/e2e/ui.e2e.mjs                                                 # SHOTS_DIR=… saves screenshots
```
Covered: secret detection and redaction, session tokens (tamper, expiry, fail-closed), audit-chain tamper detection, tool registry rules, routing and cost, the agent loop (read tool, write-tool confirmation, decline, step limit, unknown tool, cost cap), memory (secrets rejected, explicit extraction, pin, retention), retention, export/delete-all, the Vertex SDK message mapping, and the HTTP API (401 without a session, validation, UI served).

## Container
```bash
docker build -t weekend-assistant app/
docker run --rm -p 127.0.0.1:8080:8080 -e WEEKEND_LLM_PROVIDER=local -e WEEKEND_REQUIRE_SESSION=false weekend-assistant
```
Pushing to Artifact Registry and deploying to Cloud Run comes after the networking feature (CI/CD, Phase 5).

## CI/CD
`.github/workflows/app-ci.yml` runs `mvn -B verify` (Temurin 17) on every PR into `dev`/`main` that touches `app/`. `branch-guard` enforces the branch rules.

## Troubleshooting
| Symptom | Fix |
|---|---|
| `session-key must be at least 32 characters…` on start | Set `WEEKEND_SESSION_KEY`, or run with the `local` profile |
| 401 from `/api/*` | Paste a valid session token in the app (Data → Session); passkey login comes in Phase 2 |
| 429 "Daily LLM cost cap" | Wait until midnight IST or raise `weekend.agent.daily-cost-cap-usd` |
| `Application Default Credentials not available` with `vertex` | `gcloud auth application-default login`, or run on Cloud Run with its service account |

## Author and licence
Nikhil (owner). Private project — all rights reserved. Dependencies: Spring Boot (Apache-2.0), Anthropic Java SDK (MIT), JUnit (EPL-2.0).
