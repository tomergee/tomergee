package com.tomergee.mp3player.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tomergee.mp3player.data.Track
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Drag reordering is applied to a local copy for smooth feedback and persisted once, on drop,
 * as a single id-based move (one DB transaction per drag rather than per swap).
 */
@Composable
fun PlaylistDetailScreen(
    tracks: List<Track>,
    currentTrackId: Long?,
    onPlay: (index: Int) -> Unit,
    onRemove: (Track) -> Unit,
    onMove: (trackId: Long, targetTrackId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        CenteredMessage("This playlist is empty. Add songs from the Songs tab.", modifier)
        return
    }
    // Resynced whenever the persisted order changes.
    var items by remember(tracks) { mutableStateOf(tracks) }
    var dragStartOrder by remember { mutableStateOf<List<Track>?>(null) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    LazyColumn(modifier.fillMaxSize(), state = listState) {
        itemsIndexed(items, key = { _, t -> t.id }) { index, track ->
            ReorderableItem(reorderState, key = track.id) { isDragging ->
                Surface(shadowElevation = if (isDragging) 4.dp else 0.dp) {
                    TrackRow(
                        track = track,
                        isCurrent = track.id == currentTrackId,
                        onClick = { onPlay(index) },
                        leading = {
                            IconButton(
                                onClick = {},
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = { dragStartOrder = items },
                                    onDragStopped = {
                                        val start = dragStartOrder
                                        dragStartOrder = null
                                        val finalIndex = items.indexOfFirst { it.id == track.id }
                                        val target = start?.getOrNull(finalIndex)
                                        if (target != null && target.id != track.id) onMove(track.id, target.id)
                                    },
                                ),
                            ) { Icon(Icons.Filled.DragHandle, contentDescription = "Reorder ${track.title}") }
                        },
                        trailing = {
                            IconButton(onClick = { onRemove(track) }) {
                                Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remove ${track.title}")
                            }
                        },
                    )
                }
            }
        }
    }
}
