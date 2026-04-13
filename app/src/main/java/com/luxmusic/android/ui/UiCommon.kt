package com.luxmusic.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luxmusic.android.data.RepeatMode

@Composable
internal fun LuxInfoCard(title: String, body: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = luxCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun LuxStatChip(icon: ImageVector, value: String, label: String) {
    ElevatedCard(colors = luxCardColors()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun luxCardColors() = CardDefaults.elevatedCardColors(
    containerColor = MaterialTheme.colorScheme.surface,
)

internal fun pagePadding(contentPadding: PaddingValues): PaddingValues {
    return PaddingValues(
        start = 16.dp,
        top = contentPadding.calculateTopPadding() + 16.dp,
        end = 16.dp,
        bottom = contentPadding.calculateBottomPadding() + 16.dp,
    )
}

internal fun repeatLabel(mode: RepeatMode): String = when (mode) {
    RepeatMode.NONE -> "Без повтора"
    RepeatMode.ALL -> "Повтор списка"
    RepeatMode.ONE -> "Повтор трека"
}
