package com.example.ericloop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ericloop.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Light = lightColorScheme(
    primary = Color(0xFF4656AD), onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E9FA), onPrimaryContainer = Color(0xFF263571),
    secondary = Color(0xFF547A6C), secondaryContainer = Color(0xFFE0F1E8),
    background = Color(0xFFF6F7FB), surface = Color(0xFFFCFCFF),
    surfaceContainer = Color(0xFFEEF0F7), surfaceContainerLow = Color(0xFFF2F3F9),
    onSurface = Color(0xFF202539), onSurfaceVariant = Color(0xFF676D82), outlineVariant = Color(0xFFDFE2EC),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFBDC6FF), primaryContainer = Color(0xFF35458E),
    secondary = Color(0xFFAAD2BD), background = Color(0xFF121521), surface = Color(0xFF1A1E2D),
    surfaceContainer = Color(0xFF242939), surfaceContainerLow = Color(0xFF1B2030),
    onSurface = Color(0xFFE7E9F5), onSurfaceVariant = Color(0xFFB4B9CF),
)

@Composable fun LoopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
            headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
            titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 27.sp),
        ),
        shapes = Shapes(medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
}

@Composable fun LoopIcon(resource: Int, description: String? = null, modifier: Modifier = Modifier) {
    Icon(painterResource(resource), contentDescription = description, modifier = modifier.size(24.dp))
}
fun displayTime(time: Long, pattern: String = "MM月dd日 HH:mm"): String =
    Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))

@Composable fun SectionHeading(kicker: String, title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(kicker, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable fun EmptyPanel(title: String, description: String, icon: Int = R.drawable.ic_lightbulb) {
    Card(Modifier.fillMaxWidth().padding(vertical = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 38.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LoopIcon(icon, modifier = Modifier.size(34.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
