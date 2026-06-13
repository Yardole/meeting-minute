# Meeting Minute

Native meeting transcription, summary, and chat app.

## Repo Structure

```
meeting-minute/
├── android/              # Kotlin + Jetpack Compose + Room
├── ios/                  # Swift + GRDB (future)
├── supabase/
│   ├── migrations/       # Postgres schema migrations
│   └── functions/        # Edge Functions (upload-audio, transcribe, summarize, chat)
├── docs/                 # Architecture docs and decisions
└── README.md
```

## Stack

| Layer | Tech |
|---|---|
| Mobile (Android) | Kotlin + Jetpack Compose + Room |
| Mobile (iOS) | Swift + GRDB (future) |
| Backend | Supabase (Postgres + Auth + Storage) |
| Transcription & Diarization | Assembly AI |
| Summary & Chat | DeepSeek API via Supabase Edge Function |
| Audio Storage | Supabase Storage (S3-backed) |
| Sync | Local-first, last-write-wins |

See [`docs/architecture.md`](docs/architecture.md) for the full data model, sync rules, and build order.

## Setup Checklist

- [x] Supabase project created
- [x] Migrations pushed
- [x] Edge Functions deployed (`upload-audio`, `transcribe`, `summarize`, `chat`) **Always use `--no-verify-jwt`** — the Android client sends `apikey` not a Bearer token
- [x] Android app wired with Supabase URL + publishable key
- [x] Edge Function secrets set (`ASSEMBLY_AI_API_KEY`, `DEEPSEEK_API_KEY`)
- [ ] Create first user via Supabase Auth (email signup)
- [ ] Test recording + playback on device
