package com.kissansevaai

import androidx.lifecycle.ViewModel
import com.kissansevaai.core.data.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val dataStoreManager: DataStoreManager
) : ViewModel()

