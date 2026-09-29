package com.example.ericloop.data

import android.icu.lang.UCharacter
import android.icu.lang.UProperty

/** Android's Unicode emoji set also recognizes composed emoji, flags and skin tones. */
fun isRecordEmoji(value: String): Boolean =
    value.isNotEmpty() && value.length <= 64 && UCharacter.hasBinaryProperty(value, UProperty.RGI_EMOJI)
