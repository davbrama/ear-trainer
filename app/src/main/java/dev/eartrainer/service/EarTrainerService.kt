package dev.eartrainer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.eartrainer.MainActivity
import dev.eartrainer.PauseLog
import dev.eartrainer.R
import dev.eartrainer.audio.AndroidAudioFocus
import dev.eartrainer.audio.AndroidSpeechSynthesizer
import dev.eartrainer.audio.AndroidSpeechTranscriber
import dev.eartrainer.audio.PcmMicCapture
import dev.eartrainer.audio.SwitchingAudioInput
import dev.eartrainer.audio.TonePlayer
import dev.eartrainer.core.audio.StubPitchDetector
import dev.eartrainer.core.model.Phase
import dev.eartrainer.core.model.ResolvedLanguage
import dev.eartrainer.core.model.VoiceAction
import dev.eartrainer.core.model.resolve
import dev.eartrainer.core.port.Coaching
import dev.eartrainer.core.port.LocalOnlyFeedbackChannel
import dev.eartrainer.data.JsonStores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import android.Manifest
import android.content.pm.PackageManager

@OptIn(UnstableApi::class)
class EarTrainerService : MediaLibraryService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val binder = LocalBinder()
    private lateinit var player: ExoPlayer
    private lateinit var tonePlayer: TonePlayer
    private lateinit var speechIn: AndroidSpeechTranscriber
    private lateinit var speechOut: AndroidSpeechSynthesizer
    private lateinit var runtime: SessionRuntime
    private lateinit var session: MediaLibrarySession

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            .build()
        tonePlayer = TonePlayer(player, cacheDir)
        speechIn = AndroidSpeechTranscriber(this)
        speechOut = AndroidSpeechSynthesizer(this)
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openApp())
            .setCustomLayout(commandButtons())
            .build()
        session.setMediaButtonPreferences(commandButtons())
        enterForeground()
        runtime = SessionRuntime(
            context = this,
            speechOut = speechOut,
            input = SwitchingAudioInput(speechIn, PcmMicCapture(), StubPitchDetector()),
            focus = AndroidAudioFocus(this, PauseLog::log),
            player = tonePlayer,
            stores = JsonStores(filesDir),
            coach = Coaching(),
            feedbackChannel = LocalOnlyFeedbackChannel(),
            scope = scope,
            systemTag = { resources.configuration.locales[0]?.toLanguageTag() ?: "en" },
            micGranted = {
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            },
            mediaController = { session.controllerForCurrentRequest?.packageName },
        )
        runtime.state // touch
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onBind(intent: Intent?): android.os.IBinder? {
        if (intent?.action == ACTION_BIND_RUNTIME) return binder
        return super.onBind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enterForeground()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        enterForeground()
    }

    override fun onDestroy() {
        if (::runtime.isInitialized) runtime.release()
        if (::speechIn.isInitialized) speechIn.release()
        if (::speechOut.isInitialized) speechOut.release()
        if (::session.isInitialized) session.release()
        if (::player.isInitialized) player.release()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    fun micPermissionChanged() {
        if (::runtime.isInitialized) runtime.refreshMic()
        enterForeground()
    }

    private fun enterForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, foregroundTypes(includeMic = micAllowed()))
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            }
        }
    }

    private fun foregroundTypes(includeMic: Boolean): Int {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        if (includeMic && Build.VERSION.SDK_INT >= 30) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return types
    }

    private fun micAllowed(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun buildNotification(): Notification {
        val phase = if (::runtime.isInitialized) runtime.state.value.session.phase else Phase.IDLE
        val language = if (::runtime.isInitialized) {
            runtime.state.value.session.settings.language.resolve(
                resources.configuration.locales[0]?.toLanguageTag() ?: "en",
            )
        } else {
            ResolvedLanguage.ENGLISH
        }
        val text = phaseLabel(phase, language)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
            .setContentIntent(openApp())
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun phaseLabel(phase: Phase, language: ResolvedLanguage): String {
        val hebrew = language == ResolvedLanguage.HEBREW
        return when (phase) {
            Phase.PLAYING -> if (hebrew) "מנגן מרווח" else "Playing an interval"
            Phase.AWAITING_ANSWER -> if (hebrew) "מקשיב לתשובה" else "Listening for your answer"
            Phase.PAUSED -> if (hebrew) "מושהה" else "Paused"
            Phase.SPEAKING -> if (hebrew) "מדבר" else "Speaking"
            else -> getString(R.string.notification_listening)
        }
    }

    private fun openApp(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun commandButtons(): List<CommandButton> = listOf(
        button(CommandButton.ICON_REPEAT_ONE, R.string.action_repeat, CMD_REPEAT, CommandButton.SLOT_BACK),
        button(CommandButton.ICON_SKIP_FORWARD, R.string.action_skip, CMD_SKIP, CommandButton.SLOT_FORWARD),
        button(CommandButton.ICON_FLAG_UNFILLED, R.string.action_reveal, CMD_REVEAL, CommandButton.SLOT_OVERFLOW),
        button(CommandButton.ICON_MINUS, R.string.action_easier, CMD_EASIER, CommandButton.SLOT_OVERFLOW),
    )

    private fun button(icon: Int, label: Int, action: String, slot: Int): CommandButton =
        CommandButton.Builder(icon)
            .setDisplayName(getString(label))
            .setSessionCommand(SessionCommand(action, Bundle.EMPTY))
            .setSlots(slot)
            .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    inner class LocalBinder : Binder() {
        fun runtime(): SessionRuntime = runtime
        fun service(): EarTrainerService = this@EarTrainerService
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(CMD_REVEAL, Bundle.EMPTY))
                .add(SessionCommand(CMD_SKIP, Bundle.EMPTY))
                .add(SessionCommand(CMD_EASIER, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (::runtime.isInitialized) {
                when (customCommand.customAction) {
                    CMD_REPEAT -> runtime.submit(VoiceAction.Repeat)
                    CMD_REVEAL -> runtime.submit(VoiceAction.Reveal)
                    CMD_SKIP -> runtime.submit(VoiceAction.Skip)
                    CMD_EASIER -> runtime.submit(VoiceAction.AdjustDifficulty(-1))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val extras = Bundle().apply {
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
            }
            val hinted = LibraryParams.Builder().setExtras(extras).build()
            return Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, titleRoot()), hinted))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = if (page == 0) childrenOf(parentId) else emptyList()
            return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = childrenOf(ROOT).plus(childrenOf(PRACTICE)).plus(childrenOf(DIRECTIONS))
                .firstOrNull { it.mediaId == mediaId }
                ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> =
            Futures.immediateFuture(LibraryResult.ofVoid(params))

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val item = leaf("search", query.ifBlank { titleRoot() }, "Ear Trainer")
            return Futures.immediateFuture(LibraryResult.ofItemList(listOf(item), params))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            val first = mediaItems.firstOrNull()
            val query = first?.requestMetadata?.searchQuery?.toString()
            if (!::runtime.isInitialized) {
                return Futures.immediateFuture(mediaItems)
            }
            val item = runtime.libraryItem(first?.mediaId, query)
            return Futures.immediateFuture(listOf(item))
        }
    }

    private fun childrenOf(parentId: String): List<MediaItem> = when (parentId) {
        ROOT -> listOf(
            folder(PRACTICE, if (hebrew()) "תרגול" else "Practice"),
            folder(DIRECTIONS, if (hebrew()) "כיוון" else "Direction"),
            leaf(START, if (hebrew()) "התחל עכשיו" else "Start practice", if (hebrew()) "מרווחים" else "Intervals"),
        )
        PRACTICE -> listOf(
            leaf("set_beginner", if (hebrew()) "מתחילים" else "Beginner", "P4 · P5 · 3rds · 8ve"),
            leaf("set_intermediate", if (hebrew()) "בינוני" else "Intermediate", "+ 2nds · 6ths"),
            leaf("set_advanced", if (hebrew()) "מתקדם" else "Advanced", if (hebrew()) "כל המרווחים" else "All twelve"),
        )
        DIRECTIONS -> listOf(
            leaf("dir_ascending", if (hebrew()) "עולה" else "Ascending", ""),
            leaf("dir_descending", if (hebrew()) "יורד" else "Descending", ""),
            leaf("dir_harmonic", if (hebrew()) "הרמוני" else "Harmonic", ""),
            leaf("dir_mixed", if (hebrew()) "מעורב" else "Mixed", ""),
        )
        else -> emptyList()
    }

    private fun folder(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false).build(),
        )
        .build()

    private fun leaf(id: String, title: String, subtitle: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(subtitle)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )
        .build()

    private fun hebrew(): Boolean {
        if (!::runtime.isInitialized) {
            val tag = resources.configuration.locales[0]?.toLanguageTag().orEmpty()
            return tag.startsWith("he") || tag.startsWith("iw")
        }
        return runtime.state.value.session.settings.language.resolve(
            resources.configuration.locales[0]?.toLanguageTag() ?: "en",
        ) == ResolvedLanguage.HEBREW
    }

    private fun titleRoot(): String = if (hebrew()) "מאמן שמיעה" else "Ear Trainer"

    companion object {
        const val ACTION_BIND_RUNTIME = "dev.eartrainer.BIND_RUNTIME"
        const val CHANNEL_ID = "ear_trainer_practice"
        private const val NOTIFICATION_ID = 1001
        private const val ROOT = "root"
        private const val PRACTICE = "practice"
        private const val DIRECTIONS = "directions"
        private const val START = "start"
        const val CMD_REPEAT = "dev.eartrainer.REPEAT"
        const val CMD_REVEAL = "dev.eartrainer.REVEAL"
        const val CMD_SKIP = "dev.eartrainer.SKIP"
        const val CMD_EASIER = "dev.eartrainer.EASIER"

        fun start(context: android.content.Context) {
            val intent = Intent(context, EarTrainerService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
