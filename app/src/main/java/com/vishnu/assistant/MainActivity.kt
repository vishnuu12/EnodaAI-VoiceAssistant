package com.vishnu.assistant

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vishnu.assistant.core.speech.AndroidSpeaker
import com.vishnu.assistant.core.speech.AndroidSpeechRecognizer
import com.vishnu.assistant.data.ChatRepository
import com.vishnu.assistant.data.SettingsStore
import com.vishnu.assistant.ui.AssistantScreen
import com.vishnu.assistant.ui.AssistantViewModel
import com.vishnu.assistant.ui.theme.EnodaAITheme

class MainActivity : ComponentActivity() {

    private val viewModel: AssistantViewModel by viewModels {
        AssistantViewModelFactory(
            applicationContext
        )
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {

            EnodaAITheme {

                AssistantScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}

class AssistantViewModelFactory(
    context: Context
) : ViewModelProvider.Factory {

    private val appContext =
        context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (
            modelClass.isAssignableFrom(
                AssistantViewModel::class.java
            )
        ) {
            return AssistantViewModel(
                voiceRecognizer =
                    AndroidSpeechRecognizer(
                        appContext
                    ),

                chatRepository =
                    ChatRepository(),

                speaker =
                    AndroidSpeaker(
                        appContext
                    ),

                settingsStore =
                    SettingsStore(
                        appContext
                    ),

                context =
                    appContext
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}
