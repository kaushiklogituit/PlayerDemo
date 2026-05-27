package com.example.exoplayerdummy

import android.app.Application
import com.example.exoplayerdummy.AppLogger as Log
import com.example.exoplayerdummy.di.dataModule
import com.example.exoplayerdummy.di.domainModule
import com.example.exoplayerdummy.di.playerModule
import com.example.exoplayerdummy.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PlayerApplication : Application() {

    companion object {
        private const val TAG = "PlayerApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Application created — starting dependency graph")
        startKoin {
            androidContext(this@PlayerApplication)
            modules(
                dataModule,
                domainModule,
                playerModule,
                presentationModule
            )
        }
        Log.i(TAG, "Dependency graph started")
    }
}
