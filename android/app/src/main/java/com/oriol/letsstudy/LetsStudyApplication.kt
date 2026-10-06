package com.oriol.letsstudy

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.initialize

class LetsStudyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(this)
        AppCheckProviderInstaller.install()
    }
}
