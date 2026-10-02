# Weekend 2.0 — Documentation Guide

How the project documents are organised, and how to keep them up to date.

## Documents

| File | What it is | Who updates it | When |
|---|---|---|---|
| [DESIGN.md](DESIGN.md) | The main design: what the assistant is, architecture, each component, cost, roadmap, release notes | Owner (drafted with the project agent) | Every design change or feature release |
| [../PROJECT_MEMORY.md](../PROJECT_MEMORY.md) | Project state: phase, decisions, approvals, issues, session log (M1–M11) | Owner, after `save project` | End of every working session |
| [adr/](adr/) | One Architecture Decision Record per decision | Owner | When a decision (D1–D6 or later) is made |
| [diagrams/](diagrams/) | Exported diagrams (PNG/SVG) when Mermaid isn't enough | Owner | As needed |

## Reading order for a beginner

1. **DESIGN.md Part A** (§1–4): what Weekend is, the glossary, sizing and the privacy rules.
2. **DESIGN.md §5**: the diagrams showing how a message flows.
3. Any component in **Part C** you're interested in. Each one starts with a plain-English summary.
4. **§21** for cost and **§22** for the roadmap.

## How to add a new feature to DESIGN.md

1. Copy the component template from **DESIGN.md Appendix B** into Part C, or update the existing component section.
2. Fill in every heading. Write "Not applicable — reason" rather than deleting one.
3. Flag every place data leaves your control with a 🔓 block, and state the licence of every library, model or voice.
4. Add a row to the component's **Change history** table.
5. When it ships, add an entry to **§23 Feature release notes** using the template there.
6. Bump the document version and add a row to **Document release notes** at the top:
   - **major**: an architecture change or a new decision
   - **minor**: a new feature section or major content
   - **patch**: corrections and price refreshes
7. Record the session in PROJECT_MEMORY.md (M11) via `save project`.

## How to record a decision (ADR)

Create `adr/ADR-<NNNN>-<short-title>.md`:

```markdown
# ADR-0001 — <D#>: <title>
Status: Proposed / Decided / SUPERSEDED (by ADR-XXXX)
Date: YYYY-MM-DD (IST)
Context: <why a decision is needed>
Options: <table: option · pros · cons · P1–P10 impact · cost INR/USD · effort>
Decision: <what was chosen>
Reasons: <why>
Consequences: <what changes; new 🔓 exits; cost>
Sources: <links with access dates>
```

Then update DESIGN.md §6 and PROJECT_MEMORY.md M3/M4. Never delete an ADR; mark it SUPERSEDED.

## Rules for all documents

- **No secrets, account IDs, real IPs or personal data of others.** Use placeholders such as `<AWS_ACCOUNT_ID>` and `<HOME_IP>/32`.
- **Dated facts:** every price, version or limit carries a "checked on" date and a source link. Mark anything unconfirmed as `Not verified`.
- **Costs** are given in INR and USD, with the exchange rate and its date.
- **Append-only history:** don't rewrite old release notes or decisions; add new entries.
- **Diagrams:** prefer Mermaid inside the Markdown so they are diffable; GitHub renders them.
