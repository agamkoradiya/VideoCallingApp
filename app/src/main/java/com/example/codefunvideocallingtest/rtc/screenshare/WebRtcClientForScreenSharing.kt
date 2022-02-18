package com.example.codefunvideocallingtest.rtc.screenshare

import android.app.Application
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import android.widget.VideoView
import com.example.codefunvideocallingtest.model.IceCandidateModel
import com.example.codefunvideocallingtest.model.OfferModel
import com.example.codefunvideocallingtest.observer.SdpObserverImpl
import com.example.codefunvideocallingtest.repository.FirebaseRepository
import com.example.codefunvideocallingtest.util.Constants
import com.example.codefunvideocallingtest.util.Constants.SCREEN_SHARE_LOCAL_STREAM_ID
import com.example.codefunvideocallingtest.util.Constants.SCREEN_SHARE_TRACK_ID
import com.example.codefunvideocallingtest.util.Constants.VIDEO_FPS
import com.example.codefunvideocallingtest.util.Constants.VIDEO_HEIGHT
import com.example.codefunvideocallingtest.util.Constants.VIDEO_WIDTH
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.*

private const val TAG = "screen_WebRtcClient"
private const val TAG_SCREEN_SHARE = "screen_ScreenShare"

class WebRtcClientForScreenSharing(
    private val context: Application,
    private val peerConnectionUtil: PeerConnectionUtilForScreenSharing,
    private val eglBase: EglBase,
    firebaseFirestore: FirebaseFirestore,
    roomName: String,
    private val peerConnectionObserver: PeerConnection.Observer,
) {

    private val roomDocument =
        firebaseFirestore.collection(Constants.MAIN_COLLECTION_NAME).document(roomName)
    private val userCollection = roomDocument.collection(Constants.SUB_COLLECTION_NAME)

    // settingUp repository
    private val firebaseRepository by lazy { FirebaseRepository(firebaseFirestore, roomName) }

    // getting peerConnection Factory
    private val peerConnectionFactory = peerConnectionUtil.peerConnectionFactory

    private val _mediaConstraints = MediaConstraints().apply {
        mandatory.add(
            MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo", "true"
            )
        )
        mandatory.add(
            MediaConstraints.KeyValuePair(
                "RtpDataChannels", "true"
            )
        )
        mandatory.add(
            MediaConstraints.KeyValuePair(
                "DtlsSrtpkeyAgreement", "true"
            )
        )
        mandatory.add(
            MediaConstraints.KeyValuePair(
                "internalSctpDataChannels", "true"
            )
        )
    }

    private val peerConnection by lazy { buildPeerConnection(peerConnectionObserver) }

    // For local camera video sharing :
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null

    private val localAudioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints()) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }

    private val localVideoCapturer by lazy { getFrontCameraCapturer() }

    // For screen sharing :
    private var screenSharingAudioTrack: AudioTrack? = null
    private var screenSharingVideoTrack: VideoTrack? = null

    private val screenSharingAudioSource by lazy {
        peerConnectionFactory.createAudioSource(
            MediaConstraints()
        )
    }
    private lateinit var screenSharingVideoSource: VideoSource

    // Creating peer connection
    private fun buildPeerConnection(observer: PeerConnection.Observer) =
        peerConnectionFactory.createPeerConnection(
            peerConnectionUtil.iceServer,
            observer
        )

    fun initSurfaceView(view: SurfaceViewRenderer) = view.run {
//        setMirror(true)
        setEnableHardwareScaler(true)
        init(eglBase.eglBaseContext, null)
    }

    private fun getFrontCameraCapturer() = Camera2Enumerator(context).run {
        deviceNames.find {
            isFrontFacing(it)
        }?.let {
            createCapturer(it, null)
        } ?: throw IllegalStateException()
    }

    // Starting local video capture
    fun startLocalVideoCapture(localSurfaceView: SurfaceViewRenderer) {
        val surfaceTextureHelper =
            SurfaceTextureHelper.create(Thread.currentThread().name, eglBase.eglBaseContext)

        (localVideoCapturer as VideoCapturer).initialize(
            surfaceTextureHelper,
            localSurfaceView.context,
            localVideoSource.capturerObserver
        )

        localVideoCapturer.startCapture(
            VIDEO_HEIGHT,
            VIDEO_WIDTH,
            VIDEO_FPS
        )

        localAudioTrack =
            peerConnectionFactory.createAudioTrack(
                Constants.LOCAL_TRACK_ID + "_audio",
                localAudioSource
            )
        localVideoTrack =
            peerConnectionFactory.createVideoTrack(Constants.LOCAL_TRACK_ID, localVideoSource)

        localVideoTrack?.addSink(localSurfaceView)

        val localStream = peerConnectionFactory.createLocalMediaStream(Constants.LOCAL_STREAM_ID)
        localStream.addTrack(localVideoTrack)
        localStream.addTrack(localAudioTrack)

        peerConnection?.addStream(localStream)
    }

    // Starting screen sharing
    fun startScreenSharing(
        localView: SurfaceViewRenderer,
        data: Intent?,
        deviceWidth: Int,
        deviceHeight: Int
    ) {
        val screenSharingVideoCapturer =
            ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.d(TAG_SCREEN_SHARE, "onStop: video capturer android on stop called")
                }
            })

        val surfaceTextureHelper =
            SurfaceTextureHelper.create("ScreenThread", eglBase.eglBaseContext)

        screenSharingVideoSource =
            peerConnectionFactory.createVideoSource(screenSharingVideoCapturer.isScreencast)

        screenSharingVideoCapturer.initialize(
            surfaceTextureHelper,
            context,
            screenSharingVideoSource.capturerObserver
        )

//        screenSharingAudioTrack =udioTrack(
//            peerConnectionFactory.createA
//                SCREEN_SHARE_TRACK_ID + "_audio",
//                screenSharingAudioSource
//            )
        screenSharingVideoCapturer.startCapture(deviceWidth, deviceHeight, VIDEO_FPS)

        screenSharingVideoTrack =
            peerConnectionFactory.createVideoTrack(SCREEN_SHARE_TRACK_ID, screenSharingVideoSource)

        (screenSharingVideoTrack as VideoTrack).setEnabled(true)

//        screenSharingVideoCapturer.startCapture(deviceWidth, deviceHeight, VIDEO_FPS)

        screenSharingVideoTrack?.addSink(localView)

        val localStream =
            peerConnectionFactory.createLocalMediaStream(SCREEN_SHARE_LOCAL_STREAM_ID)
        localStream.addTrack(screenSharingVideoTrack)

        peerConnection?.addStream(localStream)
        
    }

    fun call() {
        Log.d(TAG, "call: called")

        peerConnection?.createOffer(
            SdpObserverImpl(
                onCreateSuccessCallback = { sdp ->
                    Log.d(TAG, "call: onCreateSuccessCallback called")
                    peerConnection?.setLocalDescription(SdpObserverImpl(
                        onSetSuccessCallback = {
                            Log.d(TAG, "call: onSetSuccess called")
                            val offerModel = OfferModel(sdp.description, sdp.type.name)

                            CoroutineScope(Dispatchers.IO).launch {
                                firebaseRepository.setOfferAndAnswer(offerModel)
                            }
                        }
                    ), sdp)
                }
            ), _mediaConstraints
        )
    }

    fun answer() {
        Log.d(TAG, "answer: called")

        peerConnection?.createAnswer(
            SdpObserverImpl(
                onCreateSuccessCallback = { sdp ->
                    Log.d(TAG, "answer: onCreateSuccessCallback called")

                    val answerModel = OfferModel(sdp.description, sdp.type.name)
                    CoroutineScope(Dispatchers.IO).launch {
                        firebaseRepository.setOfferAndAnswer(answerModel)
                    }

                    peerConnection?.setLocalDescription(SdpObserverImpl(
                        onSetSuccessCallback = {
                            Log.d(TAG, "answer: onSetSuccessCallback called")
                        }
                    ), sdp)
                }
            ), _mediaConstraints
        )
    }

    fun setRemoteDescription(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(SdpObserverImpl(), sessionDescription)
    }

    fun addIceCandidate(iceCandidate: IceCandidate) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun endCall() {
        userCollection.get().addOnSuccessListener {
            val iceCandidateList = mutableListOf<IceCandidate>()

            for (dataSnapshot in it) {
                val data = dataSnapshot.toObject(IceCandidateModel::class.java)

                if (data.type == Constants.USERTYPE.OFFER_USER.name) {

                    iceCandidateList.add(
                        IceCandidate(
                            data.sdpMid,
                            data.sdpMLineIndex!!,
                            data.sdp
                        )
                    )

                } else if (data.type == Constants.USERTYPE.ANSWER_USER.name) {
                    iceCandidateList.add(
                        IceCandidate(
                            data.sdpMid,
                            data.sdpMLineIndex!!,
                            data.sdp
                        )
                    )
                }
            }
            peerConnection?.removeIceCandidates(iceCandidateList.toTypedArray())
        }

        CoroutineScope(Dispatchers.IO).launch {
            firebaseRepository.setOfferAndAnswer(OfferModel(type = Constants.TYPE.END.name))
        }
        peerConnection?.close()
    }

    fun enableVideo(isVideoEnabled: Boolean) {
        localVideoTrack?.setEnabled(isVideoEnabled)
    }

    fun enableAudio(isAudioEnable: Boolean) {
        localAudioTrack?.setEnabled(isAudioEnable)
    }

    fun switchCamera() {
        localVideoCapturer.switchCamera(null)
    }
}