package com.bgr3108.kilonom.viewmodel

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.bgr3108.kilonom.HybridCarApplication

fun CreationExtras.hybridCarApplication(): HybridCarApplication {
    return this[APPLICATION_KEY] as HybridCarApplication
}
