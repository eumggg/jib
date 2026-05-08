package com.jib.app

import android.app.Application
import android.content.pm.PackageManager
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale

@HiltAndroidApp
class JibApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Places SDK shares the Maps API key (Places API must be enabled on the same key in GCP).
        // Skip init when the key is the placeholder so debug builds without a key still launch.
        val key = readMapsApiKey()
        if (!key.isNullOrBlank() && !key.contains("YOUR_MAPS_API_KEY", ignoreCase = true)) {
            if (!Places.isInitialized()) {
                Places.initialize(applicationContext, key, Locale.getDefault())
            }
        }
    }

    private fun readMapsApiKey(): String? = try {
        val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        info.metaData?.getString("com.google.android.geo.API_KEY")
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
