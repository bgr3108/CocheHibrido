package com.bgr3108.kilonom.util

import java.text.SimpleDateFormat
import java.util.*

fun Long.toDateTimeString(): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(this))
}
