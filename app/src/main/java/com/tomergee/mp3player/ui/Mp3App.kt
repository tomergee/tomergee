package com.tomergee.mp3player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tomergee.mp3player.data.Track
import com.tomergee.mp3player.data.db.PlaylistSummary

private object Routes {
    const val SONGS = "songs"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST = "playlist/{id}"
    fun playlist(id: Long) = "playlist/$id"
}

private sealed interface DialogState {
    data class Create(val thenAddTrackId: Long? = null) : DialogState
    data class Rename(val playlist: PlaylistSummary) : DialogState
    data class Delete(val playlist: PlaylistSummary) : DialogState
    data class AddTo(val track: Track) : DialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Mp3App(library: LibraryViewModel, player: PlayerViewModel, onRequestPermission: () -> Unit) {
    val libraryState by library.state.collectAsStateWithLifecycle()
    val playlists by library.playlists.collectAsStateWithLifecycle()
    val playerState by player.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<DialogState?>(null) }
    var detailPlayAll by remember { mutableStateOf<(() -> Unit)?>(null) }
    var detailTitle by remember { mutableStateOf("") }

    LaunchedEffect(library) { library.messages.collect { snackbar.showSnackbar(it) } }

    val playerActions = remember(player) {
        PlayerActions(
            onPlayPause = player::togglePlayPause,
            onNext = player::next,
            onPrevious = player::previous,
            onSeek = player::seekTo,
            onToggleShuffle = player::toggleShuffle,
            onCycleRepeat = player::cycleRepeatMode,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (route) {
                            Routes.PLAYLISTS -> "Playlists"
                            Routes.PLAYLIST -> detailTitle
                            else -> "Songs"
                        },
                    )
                },
                navigationIcon = {
                    if (route == Routes.PLAYLIST) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    when (route) {
                        Routes.SONGS -> IconButton(onClick = library::refresh, enabled = libraryState.hasPermission) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Rescan")
                        }
                        Routes.PLAYLIST -> detailPlayAll?.let { playAll ->
                            IconButton(onClick = playAll) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play all") }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (route == Routes.PLAYLISTS) {
                FloatingActionButton(onClick = { dialog = DialogState.Create() }) {
                    Icon(Icons.Filled.Add, contentDescription = "New playlist")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                PlayerBar(playerState, playerActions)
                NavigationBar {
                    listOf(
                        Triple(Routes.SONGS, "Songs", Icons.Filled.LibraryMusic),
                        Triple(Routes.PLAYLISTS, "Playlists", Icons.AutoMirrored.Filled.QueueMusic),
                    ).forEach { (dest, label, icon) ->
                        val selected = route == dest || (dest == Routes.PLAYLISTS && route == Routes.PLAYLIST)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Routes.SONGS, modifier = Modifier.padding(padding)) {
            composable(Routes.SONGS) {
                SongsScreen(
                    state = libraryState,
                    currentTrackId = playerState.currentTrack?.id,
                    onRequestPermission = onRequestPermission,
                    onRetry = library::refresh,
                    onPlay = { index -> player.playTracks(libraryState.tracks, index) },
                    onAddToPlaylist = { dialog = DialogState.AddTo(it) },
                )
            }
            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(
                    playlists = playlists,
                    onOpen = { navController.navigate(Routes.playlist(it.id)) },
                    onRename = { dialog = DialogState.Rename(it) },
                    onDelete = { dialog = DialogState.Delete(it) },
                )
            }
            composable(Routes.PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments!!.getLong("id")
                val detailFlow = remember(id) { library.observePlaylistDetail(id) }
                val detail by detailFlow.collectAsStateWithLifecycle(initialValue = null)
                val tracks = detail?.tracks.orEmpty()
                LaunchedEffect(detail) {
                    detailTitle = detail?.name.orEmpty()
                    detailPlayAll = if (tracks.isEmpty()) null else ({ player.playTracks(tracks, 0) })
                }
                PlaylistDetailScreen(
                    tracks = tracks,
                    currentTrackId = playerState.currentTrack?.id,
                    onPlay = { index -> player.playTracks(tracks, index) },
                    onRemove = { library.removeFromPlaylist(id, it.id) },
                    onMove = { trackId, targetId -> library.moveInPlaylist(id, trackId, targetId) },
                )
            }
        }
    }

    when (val d = dialog) {
        is DialogState.Create -> PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { library.createPlaylist(it, d.thenAddTrackId); dialog = null },
            onDismiss = { dialog = null },
        )
        is DialogState.Rename -> PlaylistNameDialog(
            title = "Rename playlist",
            initialName = d.playlist.name,
            confirmLabel = "Rename",
            onConfirm = { library.renamePlaylist(d.playlist.id, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is DialogState.Delete -> ConfirmDialog(
            title = "Delete playlist?",
            message = "\"${d.playlist.name}\" will be deleted. Your music files are not affected.",
            confirmLabel = "Delete",
            onConfirm = { library.deletePlaylist(d.playlist.id); dialog = null },
            onDismiss = { dialog = null },
        )
        is DialogState.AddTo -> AddToPlaylistDialog(
            playlists = playlists,
            onPick = { library.addToPlaylist(it, d.track.id); dialog = null },
            onCreateNew = { dialog = DialogState.Create(thenAddTrackId = d.track.id) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}
