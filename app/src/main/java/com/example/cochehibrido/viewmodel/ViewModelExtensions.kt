package com.example.cochehibrido.viewmodel

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.cochehibrido.HybridCarApplication

fun CreationExtras.hybridCarApplication(): HybridCarApplication {
    return this[APPLICATION_KEY] as HybridCarApplication
}
