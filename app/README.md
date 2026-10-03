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
| POST | `/api/chat` | `{message, conversationId?, thinkHarder?}` → reply, model, tools used, pending confirmation, cost |
| GET / POST | `/api/pending`, `/api/confirm/{id}` | List / answer write-tool confirmations (`{approved}`) |
| GET / POST / DELETE | `/api/memories`, `/api/memories/{id}/pin`, `/api/memories/{id}` | View, pin, delete memories (P6) |
| GET / DELETE | `/api/reminders`, `/api/reminders/{id}` | List / cancel reminders |
| GET / POST | `/api/export`, `/api/delete-all` | Export everything; delete all with `{"confirmation":"DELETE ALL MY DATA"}` |
| POST | `/jobs/retention`, `/jobs/export`, `/jobs/reminders/{id}/deliver` | Worker jobs (Cloud Scheduler / Cloud Tasks; IAM-protected on Cloud Run) |
| GET | `/healthz` | Liveness |

## Testing
```bash
cd app && mvn -B verify     # 40 tests: unit + full HTTP integration (offline, no cloud)
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
