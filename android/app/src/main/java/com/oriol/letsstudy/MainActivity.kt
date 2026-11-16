package com.oriol.letsstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.oriol.letsstudy.ui.StudyWorkspaceApp
import com.oriol.letsstudy.ui.LetsStudyTheme
import com.oriol.letsstudy.ui.LetsStudyViewModel
import com.oriol.letsstudy.ui.AgeSelectionScreen
import com.oriol.letsstudy.ui.YouthStudyApp

class MainActivity : ComponentActivity() {
    private val viewModel: LetsStudyViewModel by viewModels { LetsStudyViewModel.Factory(application) }
    private var adultServicesReady = false

    private fun prepareAdultServices() {
        if (adultServicesReady) return
        Firebase.initialize(application)
        AppCheckProviderInstaller.install()
        adultServicesReady = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ageStore = LearnerAgeStore(this)
        if (ageStore.read() == LearnerAgeGroup.ADULT) prepareAdultServices()
        setContent {
            LetsStudyTheme {
                var ageGroup by remember { mutableStateOf(ageStore.read()) }
                when (val group = ageGroup) {
                    null -> AgeSelectionScreen { selected ->
                        if (selected == LearnerAgeGroup.ADULT) prepareAdultServices()
                        ageStore.save(selected)
                        ageGroup = selected
                    }
                    LearnerAgeGroup.ADULT -> StudyWorkspaceApp(viewModel)
                    else -> YouthStudyApp(group)
                }
            }
        }
    }
}
