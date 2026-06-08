# Milestones

## M0 — Foundation [x]

- [x] Supabase schema migrations (`profiles`, `meetings`, `speakers`, `transcript_segments`, `summaries`, `chat_messages`)
- [x] Row Level Security (RLS) policies
- [x] Supabase email auth
- [x] Edge Function stubs (`transcribe`, `summarize`, `chat`)
- [x] Android project skeleton (Gradle, Compose, Room, DI, Supabase client)

## M1 — Record & Store [x]

- [x] In-app audio recording (MediaRecorder / AudioRecord)
- [x] Save audio to local device storage
- [x] Upload audio to Supabase Storage (via `upload-audio` edge function)
- [x] Meeting list screen (Compose)
- [x] Meeting detail shell with 3 tabs
- [x] Basic bottom audio player

## M2 — Transcribe [x]

- [x] `transcribe` Edge Function wired to Assembly AI (diarization + speaker detection)
- [x] Transcript tab UI (scrollable segments, speaker labels, timestamps)
- [x] Tap timestamp to seek player
- [x] Speaker rename UI

## M3 — Summary [x]

- [x] `summarize` Edge Function wired to DeepSeek
- [x] Summary tab UI (rendered summary + key points)
- [x] AI-generated meeting titles
- [ ] Share sheet: Markdown, PDF, Word, Plain text

## M4 — Chat [x]

- [x] `chat` Edge Function wired to DeepSeek
- [x] Chat tab UI (message list, user input)
- [x] Chat scoped to current meeting transcript only

## M5 — Sync [x]

- [x] Local-first sync engine (last write wins, `updated_at` comparison)
- [x] Bidirectional sync for all tables: `meetings`, `speakers`, `transcript_segments`, `summaries`, `chat_messages`, `profiles`
- [x] Soft delete propagation
- [x] Conflict resolution tie-breaker
- [x] Sync worker with retry backoff

## V1 — MVP Complete

- [ ] Offline recording + online processing queue
- [ ] Error states, retries, loading states
- [x] Audio file import (MP3, WAV, M4A)
- [x] Delete meeting + cascade soft delete sync (child entities)
- [x] Empty states

## V1.1 — Polish

- [ ] Push notifications (processing complete)
- [x] Search meetings
- [x] Edit meeting title
- [x] Pull-to-refresh
- [x] Share sheet: Markdown, PDF, Word, Plain text (moved from M3)

## V2 — iOS

- [ ] Swift + GRDB project setup
- [ ] Feature parity with Android V1
- [ ] Shared Supabase backend (no backend changes)

## Future

- [ ] YouTube video import
- [ ] Google Sign-In / Apple Sign-In
- [ ] Global search across transcripts
- [ ] Real-time transcription (Assembly AI streaming)
