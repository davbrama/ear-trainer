package dev.eartrainer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eartrainer.core.model.VoiceAction
import dev.eartrainer.service.SessionRuntime
import dev.eartrainer.service.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class MainViewModel : ViewModel() {
    private val runtime = MutableStateFlow<SessionRuntime?>(null)
    val ui: StateFlow<UiState> = runtime
        .flatMapLatest { current -> current?.state ?: flowOf(UiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun attach(runtime: SessionRuntime?) {
        this.runtime.value = runtime
    }

    fun act(action: VoiceAction) {
        runtime.value?.submit(action)
    }

    fun consumeExport() {
        runtime.value?.consumeExport()
    }
}
