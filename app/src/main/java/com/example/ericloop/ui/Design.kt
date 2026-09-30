package com.example.ericloop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import com.example.ericloop.R
import com.example.ericloop.data.LoopRecord
import com.example.ericloop.data.RecordKind
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

data class UiAccent(val container: Color, val content: Color)

@Composable fun accentColors(section: String): UiAccent {
    val dark = isSystemInDarkTheme()
    return when (section) {
        "plans" -> if (dark) UiAccent(Color(0xFF20342A), Color(0xFFC3DFCD)) else UiAccent(Color(0xFFEDF7EE), Color(0xFF386047))
        "ideas" -> if (dark) UiAccent(Color(0xFF21303F), Color(0xFFC7DFF4)) else UiAccent(Color(0xFFEFF6FD), Color(0xFF395B7A))
        "completed" -> if (dark) UiAccent(Color(0xFF4F432A), Color(0xFFEDDAA8)) else UiAccent(Color(0xFFE3CC93), Color(0xFF604A1D))
        "tags" -> if (dark) UiAccent(Color(0xFF223D34), Color(0xFFB6DFCD)) else UiAccent(Color(0xFFDFF0E8), Color(0xFF326B52))
        "history" -> if (dark) UiAccent(Color(0xFF3B2E4C), Color(0xFFDEC5F6)) else UiAccent(Color(0xFFEDE4F6), Color(0xFF704A95))
        "settings" -> if (dark) UiAccent(Color(0xFF293A49), Color(0xFFC0D8EC)) else UiAccent(Color(0xFFE5ECF3), Color(0xFF436276))
        else -> UiAccent(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable fun CompletionMark() {
    Text("🏅", fontSize = 24.sp, fontFamily = FontFamily.Default,
        modifier = Modifier.semantics { contentDescription = "已完成" })
}

@Composable fun LoopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
            headlineSmall = androidx.compose.ui.text.TextStyle(fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold),
            titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        ),
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp)),
        content = content,
    )
}

@Composable fun LoopIcon(resource: Int, description: String? = null, modifier: Modifier = Modifier) {
    Icon(painterResource(resource), contentDescription = description, modifier = modifier.size(24.dp))
}

@Composable fun RecordIcon(record: LoopRecord, compact: Boolean = false) {
    record.emoji?.let { Text(it, fontSize = if (compact) 20.sp else 26.sp, fontFamily = FontFamily.Default) }
        ?: Icon(painterResource(if (record.kind == RecordKind.IDEA) R.drawable.ic_lightbulb else R.drawable.ic_task_alt),
            contentDescription = null, modifier = Modifier.size(if (compact) 20.dp else 24.dp))
}
fun displayTime(time: Long, pattern: String = "MM月dd日 HH:mm"): String =
    Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))

@Composable fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: Int? = null,
    isError: Boolean = false,
    prefixLabel: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused = interactionSource.collectIsFocusedAsState().value
    val borderColor = when {
        isError -> colors.error
        focused -> colors.primary
        else -> colors.outline
    }
    val shape = MaterialTheme.shapes.medium
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(48.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        singleLine = true,
        visualTransformation = visualTransformation,
        cursorBrush = SolidColor(colors.primary),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Row(
                Modifier.fillMaxSize().padding(vertical = 1.dp).border(1.dp, borderColor, shape).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                leadingIcon?.let { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.onSurfaceVariant) }
                prefixLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant, maxLines = 1)
                    innerTextField()
                }
            }
        },
    )
}

@Composable fun SectionHeading(title: String, subtitle: String? = null, prominent: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = if (prominent) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall)
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
