package com.numbawang.leimaus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.numbawang.leimaus.achievements.Achievement
import kotlinx.coroutines.delay

private val BANNER_TEXT_COLOR = Color(0xFF78350F)
private const val AUTO_DISMISS_MILLIS = 4000L

/**
 * Festive top banner shown when a new achievement unlocks. Auto-dismisses after a few seconds,
 * or immediately on tap. [achievement] is null when nothing is queued, in which case nothing
 * is composed.
 */
@Composable
fun AchievementUnlockedBanner(
    achievement: Achievement?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = achievement != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        if (achievement == null) return@AnimatedVisibility

        LaunchedEffect(achievement.id) {
            delay(AUTO_DISMISS_MILLIS)
            onDismiss()
        }

        Card(
            onClick = onDismiss,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFACC15)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = achievement.emoji, fontSize = 32.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Saavutus avattu!",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = BANNER_TEXT_COLOR
                    )
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = BANNER_TEXT_COLOR
                    )
                    Text(
                        text = achievement.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = BANNER_TEXT_COLOR.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}
