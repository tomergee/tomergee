package com.tomergee.mp3player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tomergee.mp3player.data.Track

@Composable
fun SongsScreen(
    state: LibraryUiState,
    currentTrackId: Long?,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (index: Int) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        !state.hasPermission -> CenteredMessage(
            "Allow access to audio files to see your MP3s.",
            modifier,
        ) { Button(onClick = onRequestPermission) { Text("Grant access") } }

        state.isLoading && state.tracks.isEmpty() -> Box(modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }

        state.error != null && state.tracks.isEmpty() -> CenteredMessage(state.error, modifier) {
            Button(onClick = onRetry) { Text("Retry") }
        }

        state.tracks.isEmpty() -> CenteredMessage("No MP3 files found on this device.", modifier)

        else -> LazyColumn(modifier.fillMaxSize()) {
            itemsIndexed(state.tracks, key = { _, t -> t.id }) { index, track ->
                TrackRow(
                    track = track,
                    isCurrent = track.id == currentTrackId,
                    onClick = { onPlay(index) },
                    trailing = {
                        IconButton(onClick = { onAddToPlaylist(track) }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add ${track.title} to playlist")
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(track.title, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                "${track.artist} • ${formatDuration(track.durationMs)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = leading,
        trailingContent = trailing,
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center)
        action()
    }
}
