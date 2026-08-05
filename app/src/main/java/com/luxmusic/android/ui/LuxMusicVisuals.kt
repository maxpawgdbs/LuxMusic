package com.luxmusic.android.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.luxmusic.android.ui.theme.Coral
import com.luxmusic.android.ui.theme.Emerald
import com.luxmusic.android.ui.theme.Honey

@Composable
internal fun DecorativeBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp),
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopEnd)
                .background(Honey.copy(alpha = 0.10f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.CenterStart)
                .background(Emerald.copy(alpha = 0.08f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.BottomCenter)
                .background(Coral.copy(alpha = 0.08f), CircleShape),
        )
    }
}

@Composable
internal fun ArtworkThumb(artworkPath: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(artworkPath) { artworkPath?.let(::loadBitmapFromPath) }
    val shape = MaterialTheme.shapes.large

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

internal fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"

    val totalSeconds = durationMs / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun loadBitmapFromPath(path: String): ImageBitmap? {
    return runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
}
