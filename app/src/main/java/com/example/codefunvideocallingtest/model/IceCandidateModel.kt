package com.example.codefunvideocallingtest.model

/**
 * Created by Agam on 03-02-2022.
 */

data class IceCandidateModel(
    val serverUrl: String = "",
    val sdpMid: String = "",
    val sdpMLineIndex: Int? = null,
    val sdp: String = "",
    val type: String = ""
)
