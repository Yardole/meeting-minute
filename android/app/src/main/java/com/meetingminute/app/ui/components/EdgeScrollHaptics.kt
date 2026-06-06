package com.meetingminute.app.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EdgeScrollHaptics(
    listState: LazyListState,
    haptic: HapticFeedback
) {
    LaunchedEffect(listState) {
        var prevCanScrollUp: Boolean? = null
        var prevCanScrollDown: Boolean? = null

        snapshotFlow {
            Pair(listState.canScrollBackward, listState.canScrollForward)
        }.collectLatest { (canScrollUp, canScrollDown) ->
            // Hit the top — was scrolling up, now can't
            if (prevCanScrollUp != null && !canScrollUp && prevCanScrollUp == true) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            // Hit the bottom — was scrolling down, now can't
            if (prevCanScrollDown != null && !canScrollDown && prevCanScrollDown == true) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            prevCanScrollUp = canScrollUp
            prevCanScrollDown = canScrollDown
            // Reset tracking after hitting edge so next scroll back into boundary retriggers
            if (!canScrollUp) prevCanScrollUp = false
            if (!canScrollDown) prevCanScrollDown = false
        }
    }
}
