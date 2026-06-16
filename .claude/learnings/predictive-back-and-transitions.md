# Predictive Back & Slide-Over Transitions — Learnings (CHU-12)

## Blank flash in AnimatedContent slide-over transitions

**Problem:** Using `ExitTransition.None` in a `ContentTransform` causes the old content to be removed immediately (zero-duration exit = "finished" on first frame), producing a blank flash before the entering content covers the screen.

**Fix:** Replace `ExitTransition.None` with `fadeOut(tween(400), targetAlpha = 0.99f)` — a near-invisible fade that matches the enter duration, keeping the old content alive for the full transition. Also add `SizeTransform(clip = false)` to prevent clipping artifacts.

## Predictive back gesture — smooth implementation

### Setup
- Add `android:enableOnBackInvokedCallback="true"` to the `<activity>` tag in AndroidManifest.xml.
- Use `PredictiveBackHandler` (not `BackHandler`) — it provides a `Flow<BackEventCompat>` with real-time gesture progress.
- Only enable on sub-screens. Intercepting back at the root activity disables the system back-to-home animation.

### Use `Animatable.snapTo()`, not `mutableStateOf`
- `mutableStateOf` triggers full recomposition on every gesture frame → jank.
- `Animatable.snapTo()` is optimized for gesture tracking (updates rendering directly).
- On cancellation, use `animateTo(0f)` for a smooth snap-back animation.
- This follows the official Google pattern (JetLagged sample).

### Pre-compose the destination screen
- Conditionally adding a composable when the gesture starts (e.g., `if (progress > 0)`) causes a first-frame composition hitch (ViewModel init, layout, data loading).
- Instead, always compose the destination with `graphicsLayer(alpha = 0f)` when on a slide-over screen. It's laid out but not drawn. When the gesture starts, flip alpha to 1f — no composition overhead.

### Match the animation to the forward transition
- If the forward transition is a slide-in from right, the predictive back should slide back to the right (`translationX = progress * screenWidthPx`), not scale down. Matching the inverse feels natural.

## References
- [Official PredictiveBackHandler docs](https://developer.android.com/develop/ui/compose/system/predictive-back-progress)
- [JetLagged drawer sample](https://github.com/android/compose-samples/blob/main/JetLagged/app/src/main/java/com/example/jetlagged/JetLaggedDrawer.kt)
- [Predictive back setup](https://developer.android.com/develop/ui/compose/system/predictive-back-setup)
