package io.github.darkboyjyoti.morpheupdater.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

enum class DownloadPhase {
    IDLE,
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
    val states: LiveData<Map<String, DownloadState>> = _states

    fun get(packageName: String): DownloadState =
        _states.value?.get(packageName) ?: DownloadState()

    fun update(packageName: String, state: DownloadState) {
        synchronized(this) {
            val current = _states.value.orEmpty()
            _states.postValue(current + (packageName to state))
        }
    }

    fun clear(packageName: String) {
        synchronized(this) {
            val current = _states.value.orEmpty().toMutableMap()
            current.remove(packageName)
            _states.postValue(current)
        }
    }
}
