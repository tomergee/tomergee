package com.tomergee.mp3player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.tomergee.mp3player.ui.LibraryViewModel
import com.tomergee.mp3player.ui.Mp3App
import com.tomergee.mp3player.ui.PlayerViewModel
import com.tomergee.mp3player.ui.theme.Mp3PlayerTheme

class MainActivity : ComponentActivity() {

    private val library: LibraryViewModel by viewModels { LibraryViewModel.Factory }
    private val player: PlayerViewModel by viewModels { PlayerViewModel.Factory }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { library.onPermissionResult(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Mp3PlayerTheme {
                Mp3App(library, player, onRequestPermission = { permissionLauncher.launch(AUDIO_PERMISSION) })
            }
        }
        if (!hasAudioPermission() && savedInstanceState == null) permissionLauncher.launch(AUDIO_PERMISSION)
    }

    override fun onStart() {
        super.onStart()
        // Picks up permission granted from system settings and files added while away.
        val granted = hasAudioPermission()
        if (granted && library.state.value.hasPermission) library.refresh() else library.onPermissionResult(granted)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) player.onAppBackgrounded()
    }

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(this, AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private companion object {
        val AUDIO_PERMISSION =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
