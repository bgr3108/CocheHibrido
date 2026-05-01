package com.example.cochehibrido.util

import java.text.SimpleDateFormat
import java.util.*

fun Long.toDateString(): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return sdf.format(Date(this))
}