package io.sellmair.broadheart.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.sellmair.broadheart.*

@Composable
fun MemberHeartRateIndicator(state: GroupMemberState, range: ClosedRange<HeartRate>) {
    val side = if (state.user == Me.user) ScaleSide.Right else ScaleSide.Left
    if (state.currentHeartRate == null) return

    val animatedHeartRate = remember { Animatable(state.currentHeartRate.value) }
    LaunchedEffect(state.currentHeartRate) {
        animatedHeartRate.animateTo(state.currentHeartRate.value)
    }

    /* Can be null for myself */
    OnHeartRateScalePosition(
        HeartRate(animatedHeartRate.value), range, side = side, modifier = Modifier.padding(
            start = if (side == ScaleSide.Right) 96.dp else 0.dp,
            end = if (side == ScaleSide.Left) 48.dp else 0.dp
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserHead(state.user, modifier = Modifier.padding(horizontal = 4.dp))
            if (state.currentHeartRate > state.upperLimitHeartRate) {
                Icon(
                    Icons.Default.Warning, "Too high",
                    modifier = Modifier.size(12.dp),
                    tint = Color.Red
                )
            } else {
                Icon(
                    Icons.Default.ThumbUp, "OK",
                    modifier = Modifier.size(12.dp),
                    tint = state.user.displayColorLight.toColor()
                )
            }
        }
    }
}
