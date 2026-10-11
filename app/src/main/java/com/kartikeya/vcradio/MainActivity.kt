package com.kartikeya.vcradio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kartikeya.vcradio.ui.RadioScreen
import com.kartikeya.vcradio.ui.RadioViewModel
import com.kartikeya.vcradio.ui.theme.ViceCityRadioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ViceCityRadioTheme {
                val radioViewModel: RadioViewModel = viewModel()
                val state = radioViewModel.uiState.collectAsStateWithLifecycle()
                RadioScreen(
                    state = state.value,
                    onPrevious = radioViewModel::previousStation,
                    onNext = radioViewModel::nextStation,
                    onTogglePower = radioViewModel::togglePower,
                )
            }
        }
    }
}
