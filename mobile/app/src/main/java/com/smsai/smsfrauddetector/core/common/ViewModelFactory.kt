package com.smsai.smsfrauddetector.core.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SimpleViewModelFactory<T : ViewModel>(
    private val creator: () -> T,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return creator() as T
    }
}

