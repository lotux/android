package org.blackcandy.android.viewmodels

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.blackcandy.android.R
import org.blackcandy.android.data.CurrentPlaylistRepository
import org.blackcandy.android.data.ServerAddressRepository
import org.blackcandy.android.media.MusicServiceController
import org.blackcandy.android.models.AlertMessage
import org.blackcandy.android.utils.PlayableResource
import org.blackcandy.android.utils.TaskResult
import org.blackcandy.android.utils.Theme

data class NavHostUiState(
    val alertMessage: AlertMessage? = null,
)

class NavHostViewModel(
    private val serverAddressRepository: ServerAddressRepository,
    private val currentPlaylistRepository: CurrentPlaylistRepository,
    private val musicServiceController: MusicServiceController,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NavHostUiState())

    val uiState = _uiState.asStateFlow()

    val serverAddress =
        runBlocking {
            serverAddressRepository.getServerAddress()
        }

    fun alertMessageShown() {
        _uiState.update { it.copy(alertMessage = null) }
    }

    fun updateTheme(theme: Theme) {
        viewModelScope.launch {
            when (theme) {
                Theme.DARK -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }
                Theme.LIGHT -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
                Theme.AUTO -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
            }
        }
    }

    fun playAll(
        resourceType: PlayableResource,
        resourceId: Int,
    ) {
        viewModelScope.launch {
            when (val result = currentPlaylistRepository.replaceWith(resourceType, resourceId)) {
                is TaskResult.Success -> {
                    musicServiceController.updatePlaylist(result.data)
                    musicServiceController.playOn(0)
                }
                is TaskResult.Failure -> {
                    _uiState.update { it.copy(alertMessage = AlertMessage.String(result.message)) }
                }
            }
        }
    }

    fun playSong(songId: Int) {
        viewModelScope.launch {
            val index = musicServiceController.getSongIndex(songId)

            if (index != -1) {
                musicServiceController.playOn(index)
            } else {
                val currentSong = musicServiceController.musicState.value.currentSong ?: return@launch

                when (
                    val result =
                        currentPlaylistRepository.addSongToNext(songId, currentSong.id)
                ) {
                    is TaskResult.Success -> {
                        val songIndex = musicServiceController.addSongToNext(result.data)
                        musicServiceController.playOn(songIndex)

                        _uiState.update { it.copy(alertMessage = AlertMessage.StringResource(R.string.added_to_playlist)) }
                    }

                    is TaskResult.Failure -> {
                        _uiState.update { it.copy(alertMessage = AlertMessage.String(result.message)) }
                    }
                }
            }
        }
    }

    fun playNext(songId: Int) {
        viewModelScope.launch {
            val currentSong = musicServiceController.musicState.value.currentSong ?: return@launch

            when (val result = currentPlaylistRepository.addSongToNext(songId, currentSong.id)) {
                is TaskResult.Success -> {
                    musicServiceController.addSongToNext(result.data)
                    _uiState.update { it.copy(alertMessage = AlertMessage.StringResource(R.string.added_to_playlist)) }
                }

                is TaskResult.Failure -> {
                    _uiState.update { it.copy(alertMessage = AlertMessage.String(result.message)) }
                }
            }
        }
    }

    fun playLast(songId: Int) {
        viewModelScope.launch {
            when (val result = currentPlaylistRepository.addSongToLast(songId)) {
                is TaskResult.Success -> {
                    musicServiceController.addSongToLast(result.data)
                    _uiState.update { it.copy(alertMessage = AlertMessage.StringResource(R.string.added_to_playlist)) }
                }

                is TaskResult.Failure -> {
                    _uiState.update { it.copy(alertMessage = AlertMessage.String(result.message)) }
                }
            }
        }
    }

    fun showFlashMessage(message: String) {
        _uiState.update { it.copy(alertMessage = AlertMessage.String(message)) }
    }
}
