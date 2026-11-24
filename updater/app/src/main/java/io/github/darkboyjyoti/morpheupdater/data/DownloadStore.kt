package io.github.darkboyjyoti.morpheupdater.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

enum class DownloadPhase {
    IDLE,
    QUEUED,
    DOWNLOADING,
    VERIFYING,
    DOWNLOADED,
    FAILED,
}

data class DownloadState(
    val phase: DownloadPhase = DownloadPhase.IDLE,
    val progress: Int = 0,
    val bytes: Long = 0L,
    val total: Long = 0L,
    val message: String? = null,
)

object DownloadStore {

    private val _states = MutableLiveData<Map<String, DownloadState>>(emptyMap())
    private val currentStates = mutableMapOf<String, DownloadState>()
    val states: LiveData<Map<String, DownloadState>> = _states

    @Synchronized
    fun get(packageName: String): DownloadState =
        currentStates[packageName] ?: DownloadState()

    @Synchronized
    fun update(packageName: String, state: DownloadState) {
        currentStates[packageName] = state
        _states.postValue(currentStates.toMap())
    }

    @Synchronized
    fun clear(packageName: String) {
        currentStates.remove(packageName)
        _states.postValue(currentStates.toMap())
    }
}
