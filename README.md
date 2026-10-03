# Weekend 2.0 — Personal AI Assistant

A private, single-owner AI assistant. Chat or talk to it from your phone or Mac; it remembers what you tell it and reads your calendar and email when you allow it. Built for **one person only**, with data kept in India wherever possible.

> **Status:** Phase 0: requirements and architecture. No application code yet.
> Design: [docs/DESIGN.md](docs/DESIGN.md) · Status tracker: [tracking/Weekend2.0_Tracker.xlsx](tracking/Weekend2.0_Tracker.xlsx)

---

## What it will do

| Feature | Description | Phase | Status |
|---|---|---|---|
| Text chat | Ask questions and get streamed answers | 1 | ⚪ Planned |
| Voice | Hold to talk; the answer is spoken back | 2 | ⚪ Planned |
| Phone app | Installable web app (PWA) with passkey login | 2 | ⚪ Planned |
| Reminders | Push notifications to your phone | 2 | ⚪ Planned |
| Memory | Remembers facts; you can view, edit, export and delete them | 3 | ⚪ Planned |
| Connectors | Read-only Google Calendar, then Gmail | 3+ | ⚪ Planned |

## Design at a glance (provisional)

```mermaid
flowchart LR
  you([You: phone / Mac]) -- private tailnet + passkey --> node[Entry node e2-micro<br/>GCP, no public IP]
  node -- ID token, internal ingress --> api[Cloud Run API<br/>asia-south1]
  api --> db[(Firestore<br/>CMEK, Mumbai)]
  api -- "🔓 global endpoint" --> llm[Claude on Vertex AI]
  api -- read-only --> conn[Calendar / Gmail]
```

- **Cloud: Google Cloud** (decided 2026-10-03; AWS dropped). **No public endpoint:** the API accepts only internal, IAM-signed calls, and the entry node has no public IP. It is reachable only over your tailnet.
- **Privacy rules P1–P10:** see [DESIGN.md §4](docs/DESIGN.md#4-privacy-rules-in-plain-words-p1p10).
- **Budget:** ≤ ₹5,000 a month (about USD 52). The current GCP estimate for text-only prod is about ₹2,784 incl. 18% GST, plus ≈ ₹243 for dev ([infra/README.md](infra/README.md#cost-expected-use-asia-south1-fx-9612-on-2026-10-02)).
- Decisions: **D4 cloud = GCP (decided)**. Hosting, model, storage, auth and mobile are still open: [DESIGN.md §6](docs/DESIGN.md#6-decisions-register-d1d6).

## Repository layout

```
weekend2.0/
├── README.md            # this file
├── tracking/           # Weekend2.0_Tracker.xlsx — phases, tasks, decisions, issues, cost
├── infra/              # Terraform (GCP): bootstrap, modules/, stack/ (one root for all envs), values/ (per env branch)
├── .github/workflows/  # branch-guard, infra-ci/cd, app-ci/cd (keyless, Workload Identity Federation)
└── docs/
    ├── README.md        # how the docs are organised and updated
    ├── DESIGN.md        # main design document (architecture, components, cost, roadmap)
    ├── adr/             # one Architecture Decision Record per decision (D1–D6)
    └── diagrams/        # exported diagrams (Mermaid lives inline in DESIGN.md)
```

Planned, added in Phase 1 and later: `src/weekend/` (Python app), `tests/`, `ansible/`. Branching: [docs/BRANCHING.md](docs/BRANCHING.md).

## Prerequisites (planned for Phase 1)

| Tool | Version | Why |
|---|---|---|
| Python | 3.12+ | Application |
| Docker | current | Container image for Cloud Run; Firestore emulator for tests |
| Terraform | 1.16.x (Google provider 8.5) | Infrastructure |
| Ansible | pinned in Phase 5 | Server configuration |
| Google Cloud SDK (`gcloud`) | current | Deployment (browser login; never commit or paste keys) |
| Tailscale | current | Private access |

## Getting started

Nothing to install yet. To follow the project:
1. Read [docs/DESIGN.md](docs/DESIGN.md), Part A, for the plain-English overview.
2. Check [tracking/Weekend2.0_Tracker.xlsx](tracking/Weekend2.0_Tracker.xlsx) for the current phase and the next step. (Detailed project memory is kept privately, outside this public repo.)

From Phase 1 onwards, this section will hold:

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env        # placeholders only; real secrets live in Google Secret Manager
pytest
```

## Configuration

To be defined in Phase 1. Rules decided now:
- No secrets in code, `.env` files in git, Terraform outputs or logs (P4).
- `.env.example` holds placeholders only.

## Testing and CI/CD

Planned: `ruff` → `mypy` → `pytest` → Docker build → Trivy, gitleaks, pip-audit, checkov → gated deploy, using GitHub Actions with Workload Identity Federation (no stored keys). See [DESIGN.md §18](docs/DESIGN.md#18-cicd).

## Troubleshooting

Not applicable yet.

## Release notes

- Document versions: [DESIGN.md → Document release notes](docs/DESIGN.md#document-release-notes)
- Feature releases: [DESIGN.md §23](docs/DESIGN.md#23-feature-release-notes)

## Author and licence

Nikhil (owner). Private project — all rights reserved, not for redistribution.
