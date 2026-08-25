package com.bgr3108.kilonom.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

object ExternalLinks {
    const val PRIVACY_POLICY_URL = "https://bgr3108.github.io/CocheHibrido/privacy/"
    const val INSTAGRAM_PROFILE_URL = "https://www.instagram.com/kilonom.app/"
    const val INSTAGRAM_REEL_1_1_URL = "https://www.instagram.com/p/DcdvMd4qsnt/"
}

fun Context.openExternalUrl(url: String): Boolean = runCatching {
    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}.isSuccess
