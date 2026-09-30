package com.vishnu.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vishnu.assistant.core.speech.VoiceGender
import com.vishnu.assistant.data.ChatMessage
import com.vishnu.assistant.data.ChatRole
import com.vishnu.assistant.ui.theme.Gold
import com.vishnu.assistant.ui.theme.GoldSoft
import com.vishnu.assistant.ui.theme.ListeningBlue
import com.vishnu.assistant.ui.theme.MidnightBase
import com.vishnu.assistant.ui.theme.MidnightBorder
import com.vishnu.assistant.ui.theme.MidnightSurface
import com.vishnu.assistant.ui.theme.MidnightSurfaceHigh
import com.vishnu.assistant.ui.theme.OnDark
import com.vishnu.assistant.ui.theme.OnDarkMuted
import com.vishnu.assistant.ui.theme.Teal
import com.vishnu.assistant.ui.theme.ThinkingPurple

/**
 * EnodaAI premium assistant screen.
 *
 * Deep midnight gradient, a glowing voice orb that breathes with
 * the assistant's state, chat bubbles for the conversation, and
 * two ways to talk to the assistant:
 *
 * 1. Voice  - the big golden mic (speak request -> search -> spoken reply)
 * 2. Text   - the Indus-style input box below the chat
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel
) {

    val context =
        LocalContext.current

    val uiState by
        viewModel.uiState.collectAsState()

    var inputText by
        remember { mutableStateOf("") }

    var showSettings by
        remember { mutableStateOf(false) }

    /* ---------------------------------------------------------
     * Permission state
     * --------------------------------------------------------- */

    var hasMicPermission by
        remember {

            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

    var hasContactsPermission by
        remember {

            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

    var hasCallPermission by
        remember {

            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

    var hasCallLogPermission by
        remember {

            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CALL_LOG
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

    var permissionDenied by
        remember { mutableStateOf(false) }

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            hasMicPermission = granted

            permissionDenied = !granted

            if (granted) {
                viewModel.startListening()
            }
        }

    val contactsPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            hasContactsPermission = granted

            viewModel.onContactsPermissionResult(
                granted
            )
        }

    val callLogPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            hasCallLogPermission = granted

            viewModel.onCallLogPermissionResult(
                granted
            )
        }

    val callPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            hasCallPermission = granted

            viewModel.onCallPermissionResult(
                granted
            )
        }

    LaunchedEffect(
        uiState.contactsPermissionRequired
    ) {

        if (
            uiState.contactsPermissionRequired &&
            !hasContactsPermission
        ) {

            contactsPermissionLauncher.launch(
                Manifest.permission.READ_CONTACTS
            )
        }
    }

    LaunchedEffect(
        uiState.callLogPermissionRequired
    ) {

        if (
            uiState.callLogPermissionRequired &&
            !hasCallLogPermission
        ) {

            callLogPermissionLauncher.launch(
                Manifest.permission.READ_CALL_LOG
            )
        }
    }

    LaunchedEffect(
        uiState.callPermissionRequired
    ) {

        if (
            uiState.callPermissionRequired &&
            !hasCallPermission
        ) {

            callPermissionLauncher.launch(
                Manifest.permission.CALL_PHONE
            )
        }
    }

    val conversationListState =
        rememberLazyListState()

    LaunchedEffect(
        uiState.messages.size
    ) {

        if (
            uiState.messages.isNotEmpty()
        ) {

            conversationListState.animateScrollToItem(
                uiState.messages.lastIndex
            )
        }
    }

    /* ---------------------------------------------------------
     * Premium screen
     * --------------------------------------------------------- */

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E1428),
                        MidnightBase,
                        Color(0xFF0C1220)
                    )
                )
            )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 20.dp,
                    vertical = 12.dp
                )
        ) {

            /* ---------------- Header ---------------- */

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Gold, Teal)
                            )
                        )
                )

                Spacer(
                    Modifier.width(10.dp)
                )

                Column {

                    Text(
                        text = "EnodaAI",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnDark
                    )

                    Text(
                        text = when (uiState.mode) {
                            AssistantMode.ONLINE ->
                                "Online - AI + Web Search"
                            AssistantMode.OFFLINE ->
                                "Offline - On-device Assistant"
                        },
                        fontSize = 12.sp,
                        color = OnDarkMuted
                    )
                }

                Spacer(
                    Modifier.weight(1f)
                )

                IconButton(
                    onClick = { showSettings = true }
                ) {

                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = Gold
                    )
                }
            }

            Spacer(
                Modifier.height(8.dp)
            )

            /* ---------------- Mode + language bar ---------------- */

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.weight(1f)
                ) {

                    AssistantMode.entries
                        .forEachIndexed { index, mode ->

                            SegmentedButton(
                                selected = uiState.mode == mode,
                                onClick = { viewModel.setMode(mode) },
                                shape = SegmentedButtonDefaults
                                    .itemShape(
                                        index = index,
                                        count = AssistantMode.entries.size
                                    ),
                                icon = {

                                    Icon(
                                        imageVector = when (mode) {
                                            AssistantMode.ONLINE ->
                                                Icons.Filled.Wifi
                                            AssistantMode.OFFLINE ->
                                                Icons.Filled.WifiOff
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(mode.displayName)
                                }
                            )
                        }
                }

                SingleChoiceSegmentedButtonRow {

                    AssistantLanguage.entries
                        .forEachIndexed { index, language ->

                            SegmentedButton(
                                selected = uiState.language == language,
                                onClick = {
                                    viewModel.setLanguage(language)
                                },
                                shape = SegmentedButtonDefaults
                                    .itemShape(
                                        index = index,
                                        count = AssistantLanguage.entries.size
                                    ),
                                label = {
                                    Text(
                                        if (language ==
                                            AssistantLanguage.TAMIL
                                        ) {
                                            "தமிழ்"
                                        } else {
                                            "EN"
                                        }
                                    )
                                }
                            )
                        }
                }
            }

            Spacer(
                Modifier.height(10.dp)
            )

            /* ---------------- Voice orb ---------------- */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.34f),
                contentAlignment = Alignment.Center
            ) {

                VoiceOrb(
                    status = uiState.status
                )
            }

            /* ---------------- Status ---------------- */

            Text(
                text = uiState.statusMessage,
                fontSize = 14.sp,
                color = statusColor(uiState.status),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (
                uiState.status == AssistantStatus.LISTENING &&
                uiState.recognizedText.isNotBlank()
            ) {

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "“${uiState.recognizedText}”",
                    fontSize = 14.sp,
                    color = OnDarkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(
                Modifier.height(8.dp)
            )

            /* ---------------- Conversation ---------------- */

            if (
                uiState.messages.isNotEmpty()
            ) {

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = conversationListState,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    items(
                        items = uiState.messages,
                        key = { message ->

                            "${message.role}-" +
                                "${message.text.hashCode()}-" +
                                "${uiState.messages.indexOf(message)}"
                        }
                    ) { message ->

                        ConversationBubble(
                            message = message
                        )
                    }
                }

            } else {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = when (uiState.mode) {
                            AssistantMode.ONLINE ->
                                "Ask anything - I will search the web " +
                                    "and answer out loud"
                            AssistantMode.OFFLINE ->
                                "Offline mode: apps, calls, time, date, " +
                                    "battery and quick math"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnDarkMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }

            /* ---------------- Mic row ---------------- */

            Spacer(
                Modifier.height(10.dp)
            )

            if (!viewModel.isRecognizerAvailable) {

                Text(
                    text = "Speech recognition is not available on this " +
                        "device. Install or enable the Google app, then " +
                        "retry - or use the text box below.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                /* Voice / mic control */

                Box(
                    modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center
                ) {

                    MicButton(
                        enabled = viewModel.isRecognizerAvailable,
                        listening =
                            uiState.status == AssistantStatus.LISTENING,
                        onGrantPermission = {

                            microphonePermissionLauncher.launch(
                                Manifest.permission.RECORD_AUDIO
                            )
                        },
                        onMicTap = {

                            if (uiState.status ==
                                AssistantStatus.LISTENING
                            ) {
                                viewModel.stopListeningAndSubmit()
                            } else {
                                viewModel.startListening()
                            }
                        }
                    )
                }

                Spacer(
                    Modifier.width(14.dp)
                )

                /* Text input - Indus style */

                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MidnightSurfaceHigh,
                    border = androidx.compose.foundation
                        .BorderStroke(1.dp, MidnightBorder),
                    modifier = Modifier.weight(1f)
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            start = 18.dp,
                            end = 6.dp,
                            top = 6.dp,
                            bottom = 6.dp
                        )
                    ) {

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {

                                Text(
                                    "Type or tap the mic...",
                                    color = OnDarkMuted
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = OnDark,
                                unfocusedTextColor = OnDark,
                                cursorColor = Gold
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        if (
                            uiState.status == AssistantStatus.SPEAKING
                        ) {

                            IconButton(
                                onClick = { viewModel.stopSpeaking() }
                            ) {

                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = "Stop speaking",
                                    tint = DangerRedSoft
                                )
                            }
                        }

                        IconButton(
                            onClick = {

                                if (inputText.isNotBlank()) {

                                    viewModel.sendTextMessage(
                                        inputText
                                    )

                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank()
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) Gold
                                else OnDarkMuted
                            )
                        }
                    }
                }
            }

            if (permissionDenied) {

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Voice input needs microphone access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnDarkMuted
                )

                TextButton(
                    onClick = {
                        openAppSettings(context)
                    }
                ) {

                    Text("Open app settings")
                }
            }
        }

        /* -----------------------------------------------------
         * Settings sheet
         * ----------------------------------------------------- */

        if (showSettings) {

            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                containerColor = MidnightSurface,
                contentColor = OnDark
            ) {

                SettingsSheet(
                    uiState = uiState,
                    onModeChange = viewModel::setMode,
                    onLanguageChange = viewModel::setLanguage,
                    onVoiceGenderChange = viewModel::setVoiceGender,
                    onWebSearchChange = viewModel::setWebSearchEnabled,
                    onGroqKeyChange = viewModel::setGroqApiKey,
                    onDismiss = { showSettings = false }
                )
            }
        }
    }
}

private val DangerRedSoft = Color(0xFFFF8A80)

/* -------------------------------------------------------------
 * Voice orb
 * ------------------------------------------------------------- */

@Composable
private fun VoiceOrb(
    status: AssistantStatus
) {

    val transition =
        rememberInfiniteTransition(label = "orb")

    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val fastPulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fastPulse"
    )

    val active = status in listOf(
        AssistantStatus.LISTENING,
        AssistantStatus.THINKING,
        AssistantStatus.SPEAKING
    )

    val scale =
        if (active) {
            when (status) {
                AssistantStatus.LISTENING -> fastPulse
                else -> pulse
            }
        } else {
            1f
        }

    val color = statusColor(status)

    Box(
        modifier = Modifier
            .size(190.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {

        /* Outer glow */

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        /* Ring */

        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    color = color.copy(alpha = 0.6f),
                    shape = CircleShape
                )
        )

        /* Core */

        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            color.copy(alpha = 0.9f),
                            MidnightSurfaceHigh
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {

            when (status) {

                AssistantStatus.THINKING -> {

                    CircularProgressIndicator(
                        color = OnDark,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                }

                AssistantStatus.LISTENING -> {

                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Listening",
                        tint = OnDark,
                        modifier = Modifier.size(46.dp)
                    )
                }

                AssistantStatus.SPEAKING -> {

                    Text(
                        text = "🔊",
                        fontSize = 40.sp
                    )
                }

                else -> {

                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "EnodaAI",
                        tint = OnDark,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------
 * Mic button
 * ------------------------------------------------------------- */

@Composable
private fun MicButton(
    enabled: Boolean,
    listening: Boolean,
    onGrantPermission: () -> Unit,
    onMicTap: () -> Unit
) {

    val transition =
        rememberInfiniteTransition(label = "mic")

    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulse"
    )

    val hasMicPermission =
        ContextCompat.checkSelfPermission(
            LocalContext.current,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    Box(
        modifier = Modifier
            .size(64.dp)
            .scale(if (listening) pulse else 1f)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = if (listening) {
                        listOf(Teal, ListeningBlue)
                    } else {
                        listOf(Gold, GoldSoft)
                    }
                )
            )
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {

        IconButton(
            onClick = {

                if (!enabled) {
                    return@IconButton
                }

                if (!hasMicPermission) {
                    onGrantPermission()
                } else {
                    onMicTap()
                }
            }
        ) {

            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Speak",
                tint = if (listening) OnDark else MidnightBase,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/* -------------------------------------------------------------
 * Chat bubble
 * ------------------------------------------------------------- */

@Composable
private fun ConversationBubble(
    message: ChatMessage
) {

    val fromUser =
        message.role == ChatRole.USER

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment =
            if (fromUser) {
                Alignment.CenterEnd
            } else {
                Alignment.CenterStart
            }
    ) {

        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (fromUser) 18.dp else 4.dp,
                bottomEnd = if (fromUser) 4.dp else 18.dp
            ),
            color = if (fromUser) {
                Color.Transparent
            } else {
                MidnightSurfaceHigh
            },
            modifier = Modifier
                .widthIn(max = 320.dp)
                .then(

                    if (fromUser) {

                        Modifier.background(
                            Brush.linearGradient(
                                listOf(
                                    Gold.copy(alpha = 0.95f),
                                    Teal.copy(alpha = 0.85f)
                                )
                            ),
                            RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = 18.dp,
                                bottomEnd = 4.dp
                            )
                        )
                    } else {
                        Modifier.border(
                            1.dp,
                            MidnightBorder,
                            RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = 4.dp,
                                bottomEnd = 18.dp
                            )
                        )
                    }
                )
        ) {

            Text(
                text = message.text,
                color = if (fromUser) MidnightBase else OnDark,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 10.dp
                )
            )
        }
    }
}

/* -------------------------------------------------------------
 * Settings sheet
 * ------------------------------------------------------------- */

@Composable
private fun SettingsSheet(
    uiState: AssistantUiState,
    onModeChange: (AssistantMode) -> Unit,
    onLanguageChange: (AssistantLanguage) -> Unit,
    onVoiceGenderChange: (VoiceGender) -> Unit,
    onWebSearchChange: (Boolean) -> Unit,
    onGroqKeyChange: (String) -> Unit,
    onDismiss: () -> Unit
) {

    var groqKey by
        remember(uiState.groqApiKey) {
            mutableStateOf(uiState.groqApiKey)
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 24.dp,
                end = 24.dp,
                bottom = 32.dp
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {

        Text(
            text = "Assistant Settings",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = OnDark
        )

        /* ----- Mode ----- */

        SettingLabel("Mode")

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {

            AssistantMode.entries
                .forEachIndexed { index, mode ->

                    SegmentedButton(
                        selected = uiState.mode == mode,
                        onClick = { onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AssistantMode.entries.size
                        ),
                        label = { Text(mode.displayName) }
                    )
                }
        }

        Text(
            text = "Online uses AI with web search. Offline answers " +
                "from the device - no internet needed.",
            fontSize = 12.sp,
            color = OnDarkMuted
        )

        /* ----- Language ----- */

        SettingLabel("Language")

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {

            AssistantLanguage.entries
                .forEachIndexed { index, language ->

                    SegmentedButton(
                        selected = uiState.language == language,
                        onClick = { onLanguageChange(language) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AssistantLanguage.entries.size
                        ),
                        label = {
                            Text(
                                if (language == AssistantLanguage.TAMIL) {
                                    "தமிழ்"
                                } else {
                                    "English"
                                }
                            )
                        }
                    )
                }
        }

        /* ----- Voice gender ----- */

        SettingLabel("Voice (Tamil Nadu style)")

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {

            VoiceGender.entries
                .forEachIndexed { index, gender ->

                    SegmentedButton(
                        selected = uiState.voiceGender == gender,
                        onClick = { onVoiceGenderChange(gender) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = VoiceGender.entries.size
                        ),
                        label = { Text("${gender.displayName} voice") }
                    )
                }
        }

        /* ----- Web search ----- */

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Web search",
                    color = OnDark,
                    fontSize = 15.sp
                )

                Text(
                    text = "Search the web before answering " +
                        "(Online mode)",
                    fontSize = 12.sp,
                    color = OnDarkMuted
                )
            }

            Switch(
                checked = uiState.webSearchEnabled,
                onCheckedChange = onWebSearchChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Teal,
                    checkedThumbColor = OnDark
                )
            )
        }

        /* ----- Groq API key ----- */

        SettingLabel("AI API key (Groq)")

        OutlinedTextField(
            value = groqKey,
            onValueChange = { groqKey = it },
            placeholder = {
                Text(
                    "gsk_...",
                    color = OnDarkMuted
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold,
                unfocusedBorderColor = MidnightBorder,
                focusedTextColor = OnDark,
                unfocusedTextColor = OnDark,
                cursorColor = Gold
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            TextButton(
                onClick = { onGroqKeyChange(groqKey) },
                enabled = groqKey != uiState.groqApiKey
            ) {

                Text(
                    text = "Save key",
                    color = if (groqKey != uiState.groqApiKey) {
                        Gold
                    } else {
                        OnDarkMuted
                    }
                )
            }

            if (uiState.groqApiKey.isNotEmpty()) {

                TextButton(
                    onClick = { onGroqKeyChange("") }
                ) {

                    Text(
                        text = "Clear",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            TextButton(
                onClick = onDismiss
            ) {

                Text(
                    text = "Done",
                    color = Teal
                )
            }
        }

        Text(
            text = "Add a free key from console.groq.com/keys for " +
                "direct AI answers even when the EnodaAI backend " +
                "is sleeping. Without a key, Online mode uses the " +
                "EnodaAI server.",
            fontSize = 12.sp,
            color = OnDarkMuted
        )
    }
}

@Composable
private fun SettingLabel(
    text: String
) {

    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = OnDarkMuted
    )
}

/* -------------------------------------------------------------
 * Helpers
 * ------------------------------------------------------------- */

private fun statusColor(
    status: AssistantStatus
): Color {

    return when (status) {

        AssistantStatus.IDLE -> Gold

        AssistantStatus.LISTENING -> ListeningBlue

        AssistantStatus.THINKING -> ThinkingPurple

        AssistantStatus.SPEAKING -> Teal

        AssistantStatus.RECOGNIZED -> Gold

        AssistantStatus.ERROR ->
            com.vishnu.assistant.ui.theme.DangerRed
    }
}

private fun openAppSettings(
    context: android.content.Context
) {

    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.parse(
            "package:${context.packageName}"
        )
    )

    context.startActivity(intent)
}
