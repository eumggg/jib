package com.jib.app

import android.app.Application
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JibApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Places SDK is shared across the app (used by SearchViewModel for autocomplete).
        // Initialize once with the same key the Maps SDK uses.
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }
    }
}
