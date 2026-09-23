package com.bgr3108.kilonom.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

object ExternalLinks {
    const val PRIVACY_POLICY_URL = "https://bgr3108.github.io/CocheHibrido/privacy/"
    const val INSTAGRAM_PROFILE_URL = "https://www.instagram.com/kilonom.app/"
    const val MITECO_STATIONS_URL = "https://catalogo.datosabiertos.miteco.gob.es/catalogo/es/dataset/902a266d-5ba2-4735-a378-45818ba5a4f4"
    const val MITECO_CHARGERS_URL = "https://catalogo.datosabiertos.miteco.gob.es/catalogo/es/dataset/6ee8d46f-93bd-478f-8e29-3ba4f6d8405c"
}

fun Context.openExternalUrl(url: String): Boolean = runCatching {
    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}.isSuccess
