package com.example.ericloop.friends.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ericloop.R
import com.example.ericloop.friends.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendActionIcon(
    icon: Int,
    label: String,
    enabled: Boolean = true,
    filled: Boolean = false,
    rotation: Float = 0f,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Icon(
            painterResource(icon),
            contentDescription = label,
            tint = if (tint == Color.Unspecified || !enabled) LocalContentColor.current else tint,
            modifier = Modifier.size(24.dp).rotate(rotation),
        )
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        if (filled) FilledIconButton(onClick = onClick, enabled = enabled, content = content)
        else IconButton(onClick = onClick, enabled = enabled, content = content)
    }
}

@Composable
fun FriendAvatar(friend: Friend) {
    val color = Color(friend.colorArgb)
    Box(
        Modifier.size(48.dp).background(color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(
                when (friend.gender) {
                    FriendGender.BOY -> R.drawable.ic_friend_boy
                    FriendGender.GIRL -> R.drawable.ic_friend_girl
                    FriendGender.UNSPECIFIED -> R.drawable.ic_friends
                }
            ),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
fun friendCardColors(friend: Friend): CardColors =
    CardDefaults.cardColors(
        containerColor =
            Color(friend.colorArgb)
                .copy(alpha = 0.18f)
                .compositeOver(MaterialTheme.colorScheme.surface),
        contentColor = MaterialTheme.colorScheme.onSurface,
    )

@Composable
fun FriendCard(summary: FriendSummary, onClick: () -> Unit, rank: Int? = null) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = friendCardColors(summary.friend),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (rank != null) Text("$rank", style = MaterialTheme.typography.titleMedium)
            FriendAvatar(summary.friend)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    summary.friend.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${summary.friend.gender.label} · ${summary.friend.intro.ifBlank { "暂无简介" }}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                summary.score.toString(),
                modifier = Modifier.widthIn(max = 104.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
