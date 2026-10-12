package com.reeflive.app.ui.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "View in Live Tank" hand-off (DESIGN §5.10): livestock detail sets the organism to follow, then switches to
 * the Live tab. The Live Tank screen reads [followId] and calls [consume] once its camera has picked it up.
 */
object LiveFocus {
    private val _followId = MutableStateFlow<String?>(null)
    val followId: StateFlow<String?> = _followId.asStateFlow()

    fun follow(livestockId: String) { _followId.value = livestockId }

    fun consume(): String? = _followId.value.also { _followId.value = null }
}
