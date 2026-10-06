# ADR-0002 — D2: AI model = Claude on Vertex AI (global endpoint), with a copy in our database

Status: **Decided** (owner, 2026-10-03 IST). Taken before D1 by owner choice; D1 stays PROVISIONAL.
Date: 2026-10-03 (IST)

## Context
Current Claude models on Vertex AI have no India region (global or US/EU only; I-19). The design offered Gemini in asia-south1 as the in-India alternative (DESIGN §7.3, option A3). The owner decided: *"Model D2 – Vertex AI, no worries in data leaving India, just make a copy of the data in our database."*

## Decision
- **Claude on Vertex AI, `global` location:** Haiku 4.5 (`claude-haiku-4-5@20251001`) as the default model, Sonnet 5 (`claude-sonnet-5`) for hard tasks. Dev uses Haiku only.
- **The data exit is accepted** (🔓 X1, P7): prompts and replies may be processed outside India.
- **Copy requirement:** every LLM exchange (model, the redacted request we sent, the reply, tokens, cost, time) is also stored in **our Firestore in asia-south1**, so the owner always holds a complete copy. The copy follows P5 (retention, default 365 days like messages) and P6 (included in export and delete-all). It is built in `Feature_database` (T-040).

## Options considered
| Option | Pros | Cons | P7 | Cost (expected, text) |
|---|---|---|---|---|
| **A2 Claude on Vertex, global (chosen)** | Best tool use; newest models; service-account auth (P4); one Google bill; no global-endpoint premium | Data may leave India | 🔓 to Google (global) — accepted | ≈ $14.5 / ₹1,390 per month |
| A3 Gemini on Vertex, asia-south1 | Prompts stay in India; cheaper | Different model family; re-test prompts and tools | 🔓 to Google (India) | lower (`Not verified`) |
| D Open-weights, local | Maximum privacy | Weaker; needs hardware | None | hardware |

## Consequences
- I-19 closed (accepted). The app keeps the `LlmProvider` interface, so A3 or D can be swapped in later through `vertex_location` / configuration.
- Owner action: enable Claude Haiku 4.5 (and Sonnet 5 for prod) in Model Garden. Enabling is free; billing is per token.
- Sources: [Claude on Vertex AI](https://platform.claude.com/docs/en/build-with-claude/claude-on-vertex-ai) (checked 2026-10-03); DESIGN §7.3, §21.1.
