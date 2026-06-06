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
- [ ] Speaker rename UI

## M3 — Summary

- [ ] `summarize` Edge Function wired to DeepSeek
- [ ] Summary tab UI (rendered summary + key points)
- [ ] Share sheet: Markdown, PDF, Word, Plain text

## M4 — Chat

- [ ] `chat` Edge Function wired to DeepSeek
- [ ] Chat tab UI (message list, user input)
- [ ] Chat scoped to current meeting transcript only

## M5 — Sync

- [ ] Local-first sync engine (last write wins, `updated_at` comparison)
- [ ] Bidirectional sync for all tables: `meetings`, `speakers`, `transcript_segments`, `summaries`, `chat_messages`, `profiles`
- [ ] Soft delete propagation
- [ ] Conflict resolution tie-breaker
- [ ] Sync worker with retry backoff

## V1 — MVP Complete

- [ ] Offline recording + online processing queue
- [ ] Error states, retries, loading states
- [ ] Audio file import (MP3, WAV, M4A)
- [ ] Delete meeting + cascade soft delete sync

## V1.1 — Polish

- [ ] Push notifications (processing complete)
- [ ] Search meetings
- [ ] Edit meeting title
- [ ] Pull-to-refresh
- [ ] Empty states

## V2 — iOS

- [ ] Swift + GRDB project setup
- [ ] Feature parity with Android V1
- [ ] Shared Supabase backend (no backend changes)

## Future

- [ ] YouTube video import
- [ ] Google Sign-In / Apple Sign-In
- [ ] Global search across transcripts
- [ ] Real-time transcription (Assembly AI streaming)
