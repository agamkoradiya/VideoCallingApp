package com.example.codefunvideocallingtest.listener

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface SignalingListener {
    fun onConnectionEstablished()
    fun onOfferReceived(description: SessionDescription)
    fun onAnswerReceived(description: SessionDescription)
    fun onIceCandidateReceived(iceCandidate: IceCandidate)
    fun onCallEnded()
}