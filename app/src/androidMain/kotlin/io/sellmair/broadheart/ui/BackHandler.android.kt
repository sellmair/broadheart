package io.sellmair.broadheart.ui

import androidx.compose.runtime.Composable

@Composable
actual fun HCBackHandler(onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
}