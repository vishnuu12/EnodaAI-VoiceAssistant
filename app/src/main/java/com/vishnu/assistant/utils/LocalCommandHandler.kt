package com.vishnu.assistant.utils

import android.content.Context

class LocalCommandHandler(
    context: Context
) {

    private val appLauncher =
        AppLauncher(context)

    sealed class CommandResult {

        data class AppOpened(
            val appName: String
        ) : CommandResult()

        data class AppNotFound(
            val requestedName: String
        ) : CommandResult()

        data object NotHandled : CommandResult()
    }

    fun handle(
        message: String
    ): CommandResult {

        if (!isOpenAppCommand(message)) {
            return CommandResult.NotHandled
        }

        val appName =
            appLauncher.extractAppName(message)
                ?: return CommandResult.NotHandled

        val app =
            appLauncher.findApp(appName)

        if (app == null) {
            return CommandResult.AppNotFound(
                requestedName = appName
            )
        }

        val launched =
            appLauncher.launchApp(app)

        return if (launched) {

            CommandResult.AppOpened(
                appName = app.name
            )

        } else {

            CommandResult.AppNotFound(
                requestedName = app.name
            )
        }
    }

    private fun isOpenAppCommand(
        message: String
    ): Boolean {

        val text =
            message.lowercase()

        val openKeywords = listOf(
            "open",
            "launch",
            "start",
            "run",
            "pannu",
            "pannunga",
            "pannidu",
            "pannidunga",
            "thira",
            "thirandhu",
            "thirakkavum",
            "திற",
            "திறக்க",
            "திறந்து",
            "திறக்கவும்",
            "திறந்துவிடு",
            "திறந்துவிடுங்கள்"
        )

        return openKeywords.any {
            text.contains(it)
        }
    }
}