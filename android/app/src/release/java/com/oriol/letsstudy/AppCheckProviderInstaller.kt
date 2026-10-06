package com.oriol.letsstudy

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal object AppCheckProviderInstaller {
    fun install() {
        Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
