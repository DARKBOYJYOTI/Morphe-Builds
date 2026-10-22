package io.github.darkboyjyoti.morpheupdater.install

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object InstallTracker {

    private val _packages = MutableLiveData<Set<String>>(emptySet())
    val packages: LiveData<Set<String>> = _packages

    fun begin(packageName: String) {
        _packages.value = (_packages.value ?: emptySet()) + packageName
    }

    fun finish(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        _packages.value = (_packages.value ?: emptySet()) - packageName
    }

    fun clear() {
        _packages.value = emptySet()
    }
}
