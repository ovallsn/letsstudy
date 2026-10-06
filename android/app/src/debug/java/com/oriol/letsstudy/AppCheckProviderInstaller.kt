package com.oriol.letsstudy

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal object AppCheckProviderInstaller {
    fun install() {
        Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
