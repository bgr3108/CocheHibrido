package com.example.cochehibrido.util

import java.text.SimpleDateFormat
import java.util.*

fun Long.toDateTimeString(): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(this))
}
