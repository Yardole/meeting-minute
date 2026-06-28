# Progress Log

## Known TODOs / Follow-ups

- [ ] **Resumable (TUS) uploads — DEFERRED, must revisit.** Current audio upload is a single streamed PUT to Supabase Storage. If it drops mid-upload it restarts from zero. Deferred because audio is now compressed to ~32kbps mono (uploads are small, so single PUT is reliable enough). Come back and add TUS resumable uploads before a wider launch, or sooner if large uploads fail in practice. Context: 2026-06-28 audio-compression work (see `.claude/memory/CHU-audio-compression-50mb-cap.md`).

## 2026-06-28

### Done

- [x] **Fixed OOM crash-loop on large audio upload.** A 1h57m (~110MB) imported file crashed the app on every launch via `OutOfMemoryError` — `SupabaseStorageClient` base64-encoded the whole file in memory and the upload auto-retried on launch. Fix: (1) stream uploads directly to Supabase Storage REST (no base64, flat memory); (2) compress audio to 16kHz mono ~32kbps AAC so files fit Supabase free-tier's hard 50MB cap — recorder set low at source + imports transcoded via MediaCodec when over threshold.

## 2026-06-09

### Done since 2026-06-08

- [x] Search expanded: now searches across meeting titles, transcript text, and summary content
- [x] Search bar polish: animated focus (background + border fade 400ms, placeholder darkens), tap-outside-to-dismiss, "No results" vs "No meetings" empty state, higher empty text position
- [x] Search bar outline: subtle animated border (25% → 70% on focus)
- [x] Search bar thickness: taller vertical padding + larger icon
- [x] Error handling + retry: pipeline failures set ERROR status (including upload), retryProcessing() resumes from current stage, recording screen shows ⚠ + red progress + Retry/View buttons, Minutes tab has Retry button on error, chat errors show "Failed to send" with tap-to-retry
- [x] Package rename: com.meetingminute.app → com.oliva.notes.app
- [x] Kotlin/Hilt upgrade to support Firebase BOM (Kotlin 2.0.21 + Hilt 2.50 + version force for stdlib)
- [x] Push notifications: FCM v1 API via service account OAuth2, token stored in local profile, sync propagates to Supabase, transcribe + summarize Edge Functions send push on completion, notification channel + foreground handling on Android
- [x] Supabase: fcm_token migration, FCM service account secret, Edge Functions redeployed with --no-verify-jwt, profiles FK dropped for placeholder user
- [x] Uninstalled old package names from device (com.meetingminute.app, com.olive.notes.app)

### Next

- [ ] Test push notifications end-to-end: sync profile → record meeting → verify notification
- [ ] Offline recording + processing queue (last V1 feature)

### Commits (2026-06-08 → 2026-06-09)

```
f213d62 feat: share sheet, search, empty states, delete cascade, docs polish
6bd02a7 feat: search bar polish — focus animation, tap-to-dismiss, no-results state
(not yet committed: error handling, FCM push notifications, package rename)
```

---

## 2026-06-07

### Done

**M3 — Summary [x]**
- [x] `summarize` Edge Function wired to DeepSeek (was stub)
- [x] Structured JSON response: title, content, key_points
- [x] AI-generated meeting titles (e.g. "Q3 Marketing Budget Reallocation")
- [x] Summary saved to Room + Supabase
- [x] Summary auto-replaces speaker labels on rename (string replace, zero API cost)
- [x] Minutes tab shows summary with Fraunces titles

**M4 — Chat [x]**
- [x] `chat` Edge Function wired to DeepSeek (was stub)
- [x] Transcript context + chat history passed as system prompt
- [x] Oliva persona in prompts ("You are Oliva, a helpful meeting assistant")
- [x] Chat tab: floating input bar, inline send button as trailing icon
- [x] Chat auto-scrolls to new messages (only when near bottom)
- [x] Chat bubble styling: user olive bubble with tail, Oliva plain text
- [x] Chat message area blends into page (no card background)

**M5 — Sync [x]**
- [x] `sync` Edge Function: bidirectional sync with updated_at comparison
- [x] Last-write-wins conflict resolution, soft-delete propagation
- [x] SyncManager: collects local data, calls sync, applies merged data
- [x] Exponential backoff retry (3 attempts)
- [x] Sync triggers on app launch

**Design System — Warm Editorial [x]**
- [x] Color palette: warm paper (#FBF7F0), olive accent (#6E7A45), warm ink (#2A241E)
- [x] Typography: Fraunces (serif titles) + DM Sans (body/UI) bundled as variable fonts
- [x] App renamed to "Oliva." (with period)
- [x] Speaker names in Fraunces, Oliva/You labels in Fraunces
- [x] Slide-left/right page transitions (450ms, FastOutSlowIn)
- [x] Animated tab indicator — olive pill slides between tabs with spring physics
- [x] Edge-to-edge layout: content behind system bars with subtle scrim gradients
- [x] Compose BOM 2026.05.01, AGP 8.7.3, Gradle 8.11.1, Kotlin 2.0.21

**Recording UX [x]**
- [x] Shared element transition: mic FAB morphs into recording button
- [x] Square stop icon (rounded corners)
- [x] Ripple waves emanating from recording button
- [x] Processing screen: stays on recording screen with live status + progress bar
- [x] "Process in background" button after 5 seconds
- [x] Auto-navigate to meeting detail when processing completes
- [x] Gate: recordings under 10s rejected with toast
- [x] Tri-pulse haptic rhythm on recording start (first 5s only)
- [x] Ripple-synced haptic pulse every 700ms (first 5s)

**Audio Player [x]**
- [x] Player moved from bottom bar to under title
- [x] Controls wrapped in rounded card
- [x] Play/pause dead center with equal-weight side groups
- [x] Tap + drag to seek on progress bar (24dp touch target)
- [x] Play/pause icon transparent (no filled circle)

**Navigation & Layout [x]**
- [x] Floating tab bar pill at bottom → moved tabs inline between player and content
- [x] Tabs centered, then moved to bottom → then moved back inline
- [x] Landscape mode: player left (vertically centered), tabs+content right
- [x] Compact header in landscape

**Haptics [x]**
- [x] Record start/stop, processing complete
- [x] Play/pause toggle, send chat message
- [x] Swipe to delete, tap timestamp to seek
- [x] Tab switch, speaker rename saved
- [x] Edge-scroll bump when hitting top/bottom of any list

**Infrastructure [x]**
- [x] `SupabaseEdgeFunctionClient`: shared HTTP client for all Edge Functions
- [x] SupabaseConfig injected via DI (no hardcoded keys)
- [x] processRecording(): repository-scoped pipeline that survives navigation
- [x] ProfileEntity gained deletedAt field
- [x] All DAOs: getAllForSync() + deleteAll() methods
- [x] Database fallbackToDestructiveMigration
- [x] All Edge Functions deployed with --no-verify-jwt

### In Progress

- [ ] `feat/morph-progress-ring`: circle morphs into progress bar (stashed, needs work)
- [ ] FAB speed-dial animation: bounce needs more polish, especially on rapid toggle

### 2026-06-07 (continued — session 2)

- [x] Speed-dial FAB: "+" expands to reveal Record (mic) + Import (file) mini-FABs
- [x] Mini-FABs emerge from behind main FAB with scale+slide spring animation
- [x] "+" rotates 45° to "×" with spring bounce
- [x] Audio file import via system file picker (audio/*)
- [x] Main FAB tap bounce with Animatable snapTo + animateTo
- [x] Pull-to-refresh on home screen triggers sync (with 800ms min spinner)
- [x] Editable meeting title with auto-focus (FocusRequester) and multiline support (maxLines=3)
- [x] Background blur + scrim when speed-dial FAB is open (16dp blur, API 31+)
- [x] Import FAB icon changed to FileUpload

## Status Summary (start of 2026-06-08)

**Branch:** `main` (uncommitted changes: error handling, FCM push, package rename, search polish)
**Last commit:** `6bd02a7` — "feat: search bar polish — focus animation, tap-to-dismiss, no-results state"

**What's solid:**
- Recording → upload → transcribe → summarize pipeline
- Chat with Oliva persona, floating input, scrollable bubbles
- Sync engine (bidirectional, last-write-wins)
- Warm editorial design system (Fraunces + DM Sans, olive on paper)
- Edge-to-edge with scrim gradients
- Haptic feedback throughout
- Player controls with seek bar, speed control
- Speaker rename with auto-summary update
- Slide-left/right page transitions
- Animated tab indicator
- Shared element FAB → recording button transition
- Pull-to-refresh with sync
- Audio file import
- Speed-dial FAB with blur backdrop
- Editable meeting title

**Still open (from milestones):**
- Push notifications (processing complete)
- Offline recording + processing queue
- Push notifications (processing complete)
- Search meetings
- Offline recording + processing queue
- V2 iOS port
- YouTube video import
- Google/Apple Sign-In
- Real-time transcription

### Commits

```
f282883 feat: subtle fade gradients at top and bottom for edge-to-edge
2a702e3 fix: chat list accounts for navigation bar in bottom padding
eb86fbc feat: edge-to-edge layout + latest Compose/AGP/Gradle
98ff970 fix: tiny bump in chat bottom padding (80→88dp)
81accda fix: increase chat bottom padding so last message clears input
230e48b fix: rename app to Oliva. (with period)
382a9e9 fix: nudge chat input field 4dp lower
3be7733 fix: revert bottom padding on chat input
520a404 fix: increase bottom padding on chat input for keyboard gap
ce34301 fix: remove imePadding, add small bottom padding instead
fe03f24 fix: add imePadding for gap between input field and keyboard
a8fc53a fix: revert to full-width field with trailing send button
329a01f feat: send button inline with text in a single pill
2013165 fix: narrow chat message column horizontally
2c6e7df feat: send button inside the input field as trailing icon
e783e05 fix: remove focus indicator from chat input field
c28dd5f fix: send button muted olive instead of grey
20c613b fix: chat input fully transparent — no background bar
1b5a60d fix: play/pause button transparent — icon floats clean
2bcceb7 feat: floating chat input bar over scrolling messages
7d979d8 feat: auto-replace speaker labels in summary on rename
d44b0d5 fix: speaker names in Fraunces font
93d1794 fix: use pointerInput instead of clickable to eliminate ripple
7e442f4 fix: remove ripple indication from tab clicks
cd5acc0 feat: animated tab indicator — pill slides between tabs
... and ~20 more before that
```
