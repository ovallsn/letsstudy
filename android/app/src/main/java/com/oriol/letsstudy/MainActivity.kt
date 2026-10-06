package com.oriol.letsstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.oriol.letsstudy.ui.LetsStudyApp
import com.oriol.letsstudy.ui.LetsStudyTheme
import com.oriol.letsstudy.ui.LetsStudyViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: LetsStudyViewModel by viewModels { LetsStudyViewModel.Factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LetsStudyTheme {
                LetsStudyApp(viewModel)
            }
        }
    }
}
