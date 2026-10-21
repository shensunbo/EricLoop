package com.example.ericloop.friends.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.math.BigInteger

fun genderIcon(gender: FriendGender): Int? =
    when (gender) {
        FriendGender.BOY -> R.drawable.ic_gender_male
        FriendGender.GIRL -> R.drawable.ic_gender_female
        FriendGender.UNSPECIFIED -> null // Preserve existing data without guessing its gender.
    }

@Composable
fun GenderIcon(gender: FriendGender) {
    genderIcon(gender)?.let { resource ->
        Icon(
            painterResource(resource),
            contentDescription = gender.label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun eventScoreColor(score: BigInteger): Color =
    when {
        score.signum() > 0 -> if (isSystemInDarkTheme()) Color(0xFFA0D8B2) else Color(0xFF236B40)
        score.signum() < 0 -> if (isSystemInDarkTheme()) Color(0xFFFFB4AB) else Color(0xFFB3261E)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
fun EventScoreBadge(score: BigInteger) {
    val color = eventScoreColor(score)
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(8.dp),
    ) {
        Icon(
            painterResource(if (score.signum() > 0) R.drawable.ic_add else R.drawable.ic_remove),
            contentDescription =
                if (score.signum() > 0) "加分事件" else if (score.signum() < 0) "减分事件" else "零分事件",
            modifier = Modifier.padding(6.dp).size(20.dp),
        )
    }
}

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        summary.friend.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    GenderIcon(summary.friend.gender)
                }
                if (summary.friend.intro.isNotBlank())
                    Text(
                        summary.friend.intro,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
