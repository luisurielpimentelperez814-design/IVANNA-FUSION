package com.ivannafusion

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

object DSPState {
    var presets: SnapshotStateList<Preset> = mutableStateListOf()
    var mu: Int = 500
        get() = field
        set(value) { field = value.coerceIn(0, 1000) }
    var spatialEnabled: Boolean = true
    var posX: Int = 10
    var posY: Int = 0
    var posZ: Int = 5
}
