# ADR-0003 — Purpose: Weekend becomes a YouTube video studio (Weekend Studio)

Status: **Decided** (owner, 2026-10-07 IST). Providers below are **built but off** until each passes its own security checkpoint.
Date: 2026-10-07 (IST)

## Context
The owner changed the assistant's main purpose: a personal assistant for **YouTube video creation**. From one chat message
("create me a video on Kubernetes", "15 × 45 sec Shorts covering the whole topic", "2 min about the arctic fox") it must ask
only what is missing (length/series size, voice and captions), research the topic on the web, write the script, add a voice-over
and visuals, and drop **upload-ready MP4s** in the chat. The home page becomes an "editing-room entrance".

## Decision
| Part | Choice | Why |
|---|---|---|
| Brief | Deterministic parser (`BriefParser`) + one question at a time, tap-able answers; plan shown, nothing renders before **Start** | Fast, testable, offline; owner stays in control (approval before work) |
| Research + script | **Claude on Vertex** writes plan and scripts as JSON; optional **Claude web search** (`web_search_20250305`, the version available on Google Cloud) with citations put in the video description | Keeps research inside the existing Claude/GCP bill; no new vendor. Google Custom Search is closed to new customers (ends 2027-01-01) |
| Voice | **Google Cloud Text-to-Speech** (`en-IN` Neural2, LINEAR16 24 kHz); macOS `say` for **drafts only** (Apple voice licence is non-commercial, videos are flagged "draft voice") | Commercial use allowed; India English voices; free tier covers a 15-part series (~10k characters) |
| Visuals | Java2D **motion graphics** (title, points, image, outro scenes, progress bar, captions) + **Wikimedia Commons** photos limited to CC0 / public domain / CC BY (BY-SA, NC, ND excluded), credited in the description | No generative-image licence questions; reusable on a monetised channel |
| Music | A pad synthesised in-house (no samples) | No copyright claims |
| Encoding | ffmpeg: H.264 High, yuv420p, 30 fps, AAC 48 kHz stereo, `+faststart`; 1080×1920 Shorts (≤ 60 s), 1920×1080 videos; SRT captions, thumbnail, `metadata.json` (title, description, sources, credits, chapters) | YouTube's recommended upload settings |
| Delivery | Render queue (2 threads) with live progress; files behind **HMAC-signed links that expire in 6 h**, ranges supported, `no-store` | The `<video>` element needs a URL; the session token never goes in URLs |
| Data (P5/P6) | Media folder per project; purged after 30 days; included in export and delete-all | Same rules as the rest of the owner's data |

## Data exits (P7) — each needs its own checkpoint before it is switched on
| # | Exit | Switch | Default |
|---|---|---|---|
| S1 | Script text → Google Cloud TTS (global API) | `WEEKEND_TTS=google` + API enabled + IAM `roles/serviceusage.serviceUsageConsumer` for the runtime SA | off (`none`; `say` in the local UI env) |
| S2 | Search queries → Claude web search (Anthropic search provider via Vertex), paid per search | `WEEKEND_STUDIO_WEB_SEARCHES=1..10` | off (0) |
| S3 | Photo search words → commons.wikimedia.org / upload.wikimedia.org | add both to `WEEKEND_WEB_ALLOWED_HOSTS` | off |

Without them the studio still works end to end: offline draft scripts (labelled), captions, graphics and music.

## Consequences
- New feature **Studio** (first in the catalog) with capabilities RENDER, RESEARCH, VOICE, PHOTOS; any feature's chat also hands
  video requests to the studio.
- CI installs ffmpeg; the runtime image ships ffmpeg and fonts. Real MP4s are rendered and checked with ffprobe in tests.
- Cloud Run: renders use CPU and memory and the local disk is in-memory; storing media in GCS (CMEK) belongs to `Feature_database`.
- Not built yet: AI-generated images/video clips (licence and cost review needed), YouTube upload (OAuth scope = new checkpoint).
