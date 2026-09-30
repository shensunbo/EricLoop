package com.example.ericloop.ui

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.example.ericloop.R

private val chineseHandwriting = FontFamily(Font(R.font.zhi_mang_xing))
private val englishHandwriting = FontFamily(Font(R.font.caveat))
private val englishConnected = FontFamily(Font(R.font.dancing_script))

internal const val NOTE_FONT_PREFERENCES = "note_fonts"
internal const val CHINESE_HANDWRITING_KEY = "chinese_handwriting"
internal const val ENGLISH_FONT_KEY = "english_font"

@Composable
fun HandwritingText(text: String, style: TextStyle, modifier: Modifier = Modifier, useNotePreferences: Boolean = false,
    maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(NOTE_FONT_PREFERENCES, Context.MODE_PRIVATE) }
    val chineseEnabled = !useNotePreferences || preferences.getBoolean(CHINESE_HANDWRITING_KEY, true)
    val englishOption = if (useNotePreferences) preferences.getString(ENGLISH_FONT_KEY, "connected") else "connected"
    val chineseFamily = if (chineseEnabled) chineseHandwriting else FontFamily.Default
    val englishFamily = when (englishOption) {
        "caveat" -> englishHandwriting
        "system" -> FontFamily.Default
        else -> englishConnected
    }
    val styled = remember(text, englishOption) {
        buildAnnotatedString {
            append(text)
            var runStart = -1
            var index = 0
            while (index < text.length) {
                val codePoint = Character.codePointAt(text, index)
                val latin = Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN || Character.isDigit(codePoint)
                if (latin && runStart < 0) runStart = index
                if (!latin && runStart >= 0) {
                    addStyle(SpanStyle(fontFamily = englishFamily), runStart, index)
                    runStart = -1
                }
                index += Character.charCount(codePoint)
            }
            if (runStart >= 0) addStyle(SpanStyle(fontFamily = englishFamily), runStart, text.length)
        }
    }
    Text(styled, modifier = modifier, style = style.copy(fontFamily = chineseFamily), maxLines = maxLines, overflow = overflow)
}
