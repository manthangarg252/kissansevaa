package com.kissansevaai

import android.app.Application
import com.kissansevaai.core.sync.DataSyncWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KissanSevaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DataSyncWorker.schedule(this)
    }
}
