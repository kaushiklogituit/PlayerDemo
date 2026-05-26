package com.example.exoplayerdummy

import android.app.Application
import com.example.exoplayerdummy.di.dataModule
import com.example.exoplayerdummy.di.domainModule
import com.example.exoplayerdummy.di.playerModule
import com.example.exoplayerdummy.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PlayerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PlayerApplication)
            modules(
                dataModule,
                domainModule,
                playerModule,
                presentationModule
            )
        }
    }
}
