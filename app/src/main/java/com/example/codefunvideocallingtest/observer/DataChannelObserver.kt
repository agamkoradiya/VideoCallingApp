package com.example.codefunvideocallingtest.observer

import android.util.Log
import org.webrtc.DataChannel

/**
 * Created by Agam on 07-02-2022.
 */

open class DataChannelObserver(
    private val onBufferedAmountChangeCallback: (Long) -> Unit = {},
    private val onStateChangeCallback: () -> Unit = {},
    private val onMessageCallback: (DataChannel.Buffer) -> Unit = {},
) : DataChannel.Observer {
    override fun onBufferedAmountChange(p0: Long) {
        onBufferedAmountChangeCallback(p0)
    }

    override fun onStateChange() {
        onStateChangeCallback()
    }

    override fun onMessage(p0: DataChannel.Buffer?) {
        p0?.let {
            onMessageCallback(it)
        }
    }
}