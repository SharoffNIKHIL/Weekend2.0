# ADR-0001 — D4: Cloud provider = Google Cloud

Status: **Decided** (owner, 2026-10-03 IST). Supersedes the provisional AWS choice (DESIGN.md §16.1, v0.1.x).
Date: 2026-10-03 (IST)

## Context
Through 2026-10-02 the design and Terraform targeted AWS (Mumbai). On 2026-10-03 the owner decided: *"I am not into AWS … I have a GCP account … I want to use GCP only."* The owner already has a Google Cloud account and the Google Cloud SDK installed. D4 is normally decided after D1 and D2; the owner chose to settle it now, so D1 and D2 continue on GCP and stay **PROVISIONAL**.

## Options
| Option | Pros | Cons | P1–P10 impact | Cost (prod, incl. GST) | Effort |
|---|---|---|---|---|---|
| AWS (previous draft) | Bedrock **India** inference profiles keep Claude in India; Terraform reviewed | Owner not comfortable with AWS; credentials invalid; ≈ 97% of budget | Strong residency | ≈ ₹4,855 / USD 50.5 | Done (draft) |
| **GCP (chosen)** | Owner's account and skills; Cloud Run + Firestore scale to zero with free tiers; keyless CI via WIF; ≈ 56% of budget | Current Claude models on Vertex AI have **no India region** (global or US/EU only) | P7: LLM prompts may leave India; everything else in asia-south1 | ≈ ₹2,784 / USD 29.0 | Rewrite Terraform (done 2026-10-03) |

## Decision
Use **Google Cloud only**. Primary region **asia-south1 (Mumbai)**. AWS is dropped; no AWS resources were ever created.

## Service mapping
| Need | AWS (old) | GCP (new) |
|---|---|---|
| API compute | Lambda + IAM Function URL | Cloud Run v2, internal ingress + IAM invoker |
| Database | Aurora Serverless v2 + pgvector | Firestore (Native) `(default)`, CMEK, vector search |
| LLM | Bedrock `in.` profiles | Vertex AI Claude (global endpoint) — 🔓 P7 |
| Entry server | EC2 t4g.nano + public IPv4 | GCE e2-micro, no external IP, Cloud NAT, IAP SSH |
| Keys / secrets | KMS / Secrets Manager | Cloud KMS (bootstrap) / Secret Manager (single region, CMEK) |
| Backups | S3 Object Lock | GCS retention policy + CMEK |
| Schedules / reminders | EventBridge Scheduler | Cloud Scheduler + Cloud Tasks |
| CI access | GitHub OIDC → IAM role | GitHub OIDC → Workload Identity Federation |
| State | S3 native lock | GCS backend (built-in locking) |

## Consequences
- 🔓 **New data exit:** Claude via the Vertex AI global endpoint. The in-India alternative is Gemini in asia-south1; to be settled under D2.
- Dev uses GCP's always-free e2-micro in us-central1 (no real personal data in dev); prod stays in India.
- Tracker, DESIGN.md, diagrams, Terraform, CI and GitHub secrets moved to GCP on branch `feature_infra` (PR #2).
- AWS keys on the owner's Mac are dead; remove them from `~/.aws/credentials`.

## Sources (accessed 2026-10-03)
- Anthropic, Claude on Google Cloud (endpoints, model IDs): https://platform.claude.com/docs/en/build-with-claude/claude-on-vertex-ai
- Google Cloud, Cloud Run pricing: https://cloud.google.com/run/pricing
- Google Cloud, Firestore pricing / CMEK: https://cloud.google.com/firestore/pricing · https://docs.cloud.google.com/firestore/native/docs/cmek
- e2-micro regional prices (data 2026-09-27): https://gcloud-compute.com/e2-micro.html
- Cloud NAT pricing: https://cloud.google.com/nat
