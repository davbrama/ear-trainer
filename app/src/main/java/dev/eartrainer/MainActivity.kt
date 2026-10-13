package dev.eartrainer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.eartrainer.service.EarTrainerService
import dev.eartrainer.ui.EarTrainerRoot
import dev.eartrainer.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private var bound = false
    private var viewModel: MainViewModel? = null
    private var bypassMic = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as EarTrainerService.LocalBinder
            viewModel?.attach(binder.runtime())
            binder.service().micPermissionChanged()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            viewModel?.attach(null)
        }
    }

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startAndBind()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (
            intent?.action == ACTION_DEBUG_CRASH &&
            applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        ) {
            throw RuntimeException("Ear Trainer debug crash probe")
        }
        enableEdgeToEdge()
        bypassMic = savedInstanceState?.getBoolean(KEY_BYPASS) == true
        setContent {
            val model: MainViewModel = viewModel()
            viewModel = model
            EarTrainerRoot(
                viewModel = model,
                systemTag = resources.configuration.locales[0]?.toLanguageTag() ?: "en",
                micGranted = micGranted(),
                bypassMic = bypassMic,
                onRequestMic = {
                    val needed = buildList {
                        add(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }.filter { ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED }
                    if (needed.isEmpty()) startAndBind() else permissions.launch(needed.toTypedArray())
                },
                onBypassMic = {
                    bypassMic = true
                    startAndBind()
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        if (micGranted() || bypassMic) startAndBind()
    }

    override fun onStop() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_BYPASS, bypassMic)
        super.onSaveInstanceState(outState)
    }

    private fun startAndBind() {
        EarTrainerService.start(this)
        if (!bound) {
            val intent = Intent(this, EarTrainerService::class.java).setAction(EarTrainerService.ACTION_BIND_RUNTIME)
            bound = bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }

    private fun micGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val KEY_BYPASS = "bypass_mic"
        const val ACTION_DEBUG_CRASH = "dev.eartrainer.DEBUG_CRASH"
    }
}
