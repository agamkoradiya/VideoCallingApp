package com.example.codefunvideocallingtest.ui.fragment.ui_call_fragment

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UiCallViewModel @Inject constructor() : ViewModel() {

    private val _receivedTextMessages = MutableLiveData(mutableListOf<String>())
    val receivedTextMessages: LiveData<MutableList<String>> = _receivedTextMessages

    fun addTextMessage(message: String) {
        Log.d("viewmodel", "Add text message called")
        val list = _receivedTextMessages.value
        list?.add(message)
        _receivedTextMessages.postValue(list)
    }
}
