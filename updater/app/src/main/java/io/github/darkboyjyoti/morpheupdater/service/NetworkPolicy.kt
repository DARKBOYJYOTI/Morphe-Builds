package io.github.darkboyjyoti.morpheupdater.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import io.github.darkboyjyoti.morpheupdater.data.Prefs

object NetworkPolicy {

    fun canDownload(context: Context, prefs: Prefs): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
            if (prefs.wifiOnly &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                return false
            }
            true
        } else {
            @Suppress("DEPRECATION")
            val info = manager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            val connected = info.isConnected
            @Suppress("DEPRECATION")
            val wifi = info.type == ConnectivityManager.TYPE_WIFI ||
                info.type == ConnectivityManager.TYPE_ETHERNET
            connected && (!prefs.wifiOnly || wifi)
        }
    }
}
