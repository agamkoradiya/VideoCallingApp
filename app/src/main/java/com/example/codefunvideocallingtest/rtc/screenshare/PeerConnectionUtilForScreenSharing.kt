package com.example.codefunvideocallingtest.rtc.screenshare

import android.app.Application
import android.content.Context
import org.webrtc.*

/**
 * Provides base WebRTC instances [PeerConnectionFactory] and [PeerConnection.RTCConfiguration]
 * NOTE: This class is not mandatory but simplifies work with WebRTC.
 */
class PeerConnectionUtilForScreenSharing(
    context: Application,
    eglBaseContext: EglBase.Context
) {

    init {
        PeerConnectionFactory.InitializationOptions
            .builder(context)
            // Enable tracing behind the hood
            .setEnableInternalTracer(true)
            //H.264 video provides better picture quality
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions().also { initializationOptions ->
                PeerConnectionFactory.initialize(initializationOptions)
            }
    }

    private val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)

    //    private val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(eglBaseContext, false, true)
    private val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(eglBaseContext)
    private val defaultAudioEncoderFactoryFactory = BuiltinAudioEncoderFactoryFactory()
    private val defaultAudioDecoderFactoryFactory = BuiltinAudioDecoderFactoryFactory()

    // Creating peer connection factory. We need it to create "PeerConnections"
    val peerConnectionFactory: PeerConnectionFactory = PeerConnectionFactory
        .builder()
        .setVideoDecoderFactory(defaultVideoDecoderFactory)
        .setVideoEncoderFactory(defaultVideoEncoderFactory)
//        .setAudioDecoderFactoryFactory(defaultAudioDecoderFactoryFactory)
//        .setAudioEncoderFactoryFactory(defaultAudioEncoderFactoryFactory)
        .setOptions(PeerConnectionFactory.Options().apply {
            disableEncryption = false
            disableNetworkMonitor = true
        })
        .createPeerConnectionFactory()

    // rtcConfig contains STUN and TURN servers list
    val rtcConfig = PeerConnection.RTCConfiguration(
        arrayListOf(
            // adding google's standard server
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
    ).apply {
        // it's very important to use new unified sdp semantics PLAN_B is deprecated
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
    }

    val iceServer = listOf(

        PeerConnection.IceServer.builder("stun:bn-turn1.xirsys.com")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:bn-turn1.xirsys.com:80?transport=udp")
            .setUsername("_peW66NaEweYT4d_whNLNCN9Gh_KOtMPsHC5nrUYrIYAzaNADm_ShGFqcTejal10AAAAAGIGATVhYmN0ZWNoYWJj")
            .setPassword("52b3ec90-8b03-11ec-897f-0242ac140004").createIceServer(),

//        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
//            .createIceServer(),
//            .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
//        PeerConnection.IceServer.builder("stun:23.21.150.121").createIceServer(),
//        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
//        PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
//        PeerConnection.IceServer.builder("stun:stun.voip.blackberry.com:3478").createIceServer(),
//        PeerConnection.IceServer.builder("stun:stun.talkho.com:3478").createIceServer(),
//        PeerConnection.IceServer.builder("stun:stun.labs.net:3478").createIceServer(),
//        PeerConnection.IceServer.builder("stun:stun.eoni.com:3478").createIceServer(),
//        PeerConnection.IceServer.builder("stun:stun.bau-ha.us:3478").createIceServer(),
//        PeerConnection.IceServer.builder("stun:stun.fwdnet.net").createIceServer(),
//        PeerConnection.IceServer.builder("stun:stun.ru-brides.com:3478").createIceServer(),
//        PeerConnection.IceServer.builder("stun:stunserver.org").createIceServer(),
//        PeerConnection.IceServer.builder("stun:stun.softjoys.com").createIceServer(),

//        PeerConnection.IceServer.builder("turn:turn.bistri.com:80").setUsername("homeo")
//            .setPassword("homeo").createIceServer(),
//        PeerConnection.IceServer.builder("turn:turn.anyfirewall.com:443?transport=tcp").setUsername("webrtc")
//            .setPassword("webrtc").createIceServer(),
//        PeerConnection.IceServer.builder("turn:numb.viagenie.ca")
//            .setUsername("webrtc@live.com")
//            .setPassword("muazkh").createIceServer(),
//        PeerConnection.IceServer.builder("turn:13.250.13.83:3478?transport=udp")
//            .setUsername("YzYNCouZM1mhqhmseWk6").setPassword("YzYNCouZM1mhqhmseWk6")
//            .createIceServer(),
//        PeerConnection.IceServer.builder("turn:192.158.29.39:3478?transport=udp")
//            .setUsername("28224511:1379330808").setPassword("JZEOEt2V3Qb0y27GRntt2u2PAYA=")
//            .createIceServer(),
//        PeerConnection.IceServer.builder("turn:192.158.29.39:3478?transport=tcp")
//            .setUsername("28224511:1379330808").setPassword("JZEOEt2V3Qb0y27GRntt2u2PAYA=")
//            .createIceServer()
    )


//    PeerConnection.IceServer.builder("stun:stun.lovense.com:3478").createIceServer(),
//    PeerConnection.IceServer.builder("stun:stun.iptel.org").createIceServer(),
//    PeerConnection.IceServer.builder("stun:stun.rixtelecom.se").createIceServer(),
//    PeerConnection.IceServer.builder("stun:stun01.sipphone.com").createIceServer(),
//    PeerConnection.IceServer.builder("stun:stun.ekiga.net").createIceServer(),
//    PeerConnection.IceServer.builder("stun:stun.fwdnet.net").createIceServer(),
//    PeerConnection.IceServer.builder("stun:stun.ru-brides.com:3478").createIceServer(),

//    listOf(
//    "stun:stun01.sipphone.com",
//    "stun:stun.ekiga.net",
//    "stun:stun.fwdnet.net",
//    "stun:stun.lovense.com:3478",
//    "stun:stun.iptel.org",
//    "stun:stun.rixtelecom.se",
//    "stun:stun.ru-brides.com:3478",
//    "stun:stun.l.google.com:19302",
//    "stun:stun1.l.google.com:19302",
//    "stun:stun2.l.google.com:19302",
//    "stun:stun3.l.google.com:19302",
//    "stun:stun4.l.google.com:19302",
//    "stun:stunserver.org",
//    "stun:stun.softjoys.com",
//    "stun:stun.voiparound.com",
//    "stun.imp.ch:3478",
//    "stun:stun.godatenow.com:3478",
//    "stun:stun.voxgratia.org",
//    "stun:stun.xten.com"
//    )

}