package com.example.guardianangel.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.guardianangel.domain.model.CodewordTier
import com.example.guardianangel.domain.model.GuardianMode
import com.example.guardianangel.domain.model.GuardianSnapshot
import com.example.guardianangel.domain.repository.GuardianRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the home screen renders.
 *
 * [Loading] exists so the screen has something honest to show before the first snapshot
 * arrives — once the repository is backed by a database or the network that gap is real.
 */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(val snapshot: GuardianSnapshot) : HomeUiState
}

/** Every action the home screen can take, named for intent rather than for a widget. */
sealed interface HomeAction {
    data object ArmGuardian : HomeAction
    data object DisarmGuardian : HomeAction
    data object StopRecording : HomeAction
    data object RescanArea : HomeAction
    data class DispatchAlert(val tier: CodewordTier) : HomeAction
    data class StartRecording(val tier: CodewordTier?) : HomeAction
}

class HomeViewModel(
    private val repository: GuardianRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = repository.observeSnapshot()
        .map<GuardianSnapshot, HomeUiState> { HomeUiState.Ready(it) }
        .stateIn(
            scope = viewModelScope,
            // Keep collecting briefly across configuration changes so a rotation does not
            // tear down and restart the location/safety-score stream.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    fun onAction(action: HomeAction) {
        viewModelScope.launch {
            when (action) {
                HomeAction.ArmGuardian -> repository.armGuardian()
                HomeAction.DisarmGuardian -> repository.disarmGuardian()
                HomeAction.StopRecording -> repository.stopRecording()
                HomeAction.RescanArea -> repository.rescanArea()
                is HomeAction.DispatchAlert -> repository.dispatchAlert(action.tier)
                is HomeAction.StartRecording -> repository.startRecording(action.tier)
            }
        }
    }

    /**
     * Minimal factory. Swapped for Hilt/Koin injection once a DI container is introduced;
     * until then this keeps the repository a constructor parameter rather than a
     * hard-coded singleton, so tests can supply their own.
     */
    class Factory(private val repository: GuardianRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repository) as T
    }
}

/** True when the guardian is doing anything other than sitting in standby. */
val GuardianMode.isArmed: Boolean
    get() = this != GuardianMode.Standby
