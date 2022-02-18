package com.example.codefunvideocallingtest.rtc.regular

import android.app.Application
import android.util.Log
import com.example.codefunvideocallingtest.model.IceCandidateModel
import com.example.codefunvideocallingtest.model.OfferModel
import com.example.codefunvideocallingtest.observer.DataChannelObserver
import com.example.codefunvideocallingtest.observer.SdpObserverImpl
import com.example.codefunvideocallingtest.repository.FirebaseRepository
import com.example.codefunvideocallingtest.util.Constants
import com.example.codefunvideocallingtest.util.Constants.CHUNK_SIZE
import com.example.codefunvideocallingtest.util.Constants.LOCAL_STREAM_ID
import com.example.codefunvideocallingtest.util.Constants.LOCAL_TRACK_ID
import com.example.codefunvideocallingtest.util.Constants.VIDEO_FPS
import com.example.codefunvideocallingtest.util.Constants.VIDEO_HEIGHT
import com.example.codefunvideocallingtest.util.Constants.VIDEO_WIDTH
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import org.webrtc.*
import java.io.*
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.nio.charset.Charset

private const val TAG = "WebRtcClient"
const val IMAGE_RELATED_TAG = "ImageRelated"
private const val WEB_RTC_DATA_CHANNEL_TAG = "WebRtcDataChannel"

class WebRtcClient(
    private val context: Application,
//    private val peerConnectionUtil: PeerConnectionUtil,
    private val eglBase: EglBase,
    firebaseFirestore: FirebaseFirestore,
    roomName: String,
    private val dataChannelObserver: DataChannelObserver,
    private val peerConnectionObserver: PeerConnection.Observer,
) {

    private val roomDocument =
        firebaseFirestore.collection(Constants.MAIN_COLLECTION_NAME).document(roomName)
    private val userCollection = roomDocument.collection(Constants.SUB_COLLECTION_NAME)

    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null

    private val peerConnectionUtil = PeerConnectionUtil(
        context,
        eglBase.eglBaseContext
    )

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

    // settingUp repository
    private val firebaseRepository by lazy { FirebaseRepository(firebaseFirestore, roomName) }

    private val localAudioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints()) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val peerConnection by lazy { buildPeerConnection(peerConnectionObserver) }
//    private val localDataChannel by lazy { createLocalDataChannel() }

    private var localDataChannel: DataChannel? = null

    // getting front camera
    private val videoCapturer by lazy { getFrontCameraCapturer() }

    // Creating peer connection
    private fun buildPeerConnection(observer: PeerConnection.Observer) =
        peerConnectionFactory.createPeerConnection(
            peerConnectionUtil.iceServer,
            observer
        )

    fun createLocalDataChannel() {
        Log.d(WEB_RTC_DATA_CHANNEL_TAG, "createLocalDataChannel: called")

        Log.d(
            WEB_RTC_DATA_CHANNEL_TAG,
            "createLocalDataChannel: before initializing - $localDataChannel"
        )
        localDataChannel = peerConnection?.createDataChannel(
            Constants.DATA_CHANNEL_NAME,
            DataChannel.Init()
        )

        Log.d(
            WEB_RTC_DATA_CHANNEL_TAG,
            "createLocalDataChannel: after initializing - $localDataChannel"
        )

        localDataChannel?.let {
            Log.d(WEB_RTC_DATA_CHANNEL_TAG, "createLocalDataChannel: observer registered")
            it.registerObserver(dataChannelObserver)
        }
    }


    // Making SurfaceViewRenderer ready to show data
    fun initSurfaceView(view: SurfaceViewRenderer) = view.run {
        setMirror(true)
        setEnableHardwareScaler(true)
        init(eglBase.eglBaseContext, null)
    }

    // Checking is Front camera available or not is yes then return VideoCapture
//    private fun getFrontCameraCapturer(): VideoCapturer {
//        val cameraEnumerator = Camera2Enumerator(context)
//        cameraEnumerator.deviceNames.forEach { cameraName ->
//            if (cameraEnumerator.isFrontFacing(cameraName)) {
//                return cameraEnumerator.createCapturer(cameraName, null)
//            }
//        }
//        error("Front camera does not exists")
//    }

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
        (videoCapturer as VideoCapturer).initialize(
            surfaceTextureHelper,
            localSurfaceView.context,
            localVideoSource.capturerObserver
        )
        videoCapturer.startCapture(VIDEO_HEIGHT, VIDEO_WIDTH, VIDEO_FPS)

        localAudioTrack =
            peerConnectionFactory.createAudioTrack(LOCAL_TRACK_ID + "_audio", localAudioSource)
        localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource)

        localVideoTrack?.addSink(localSurfaceView)

        val localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)
        localStream.addTrack(localVideoTrack)
        localStream.addTrack(localAudioTrack)

        peerConnection?.addStream(localStream)

//        peerConnection?.addTrack(localVideoTrack)
//        peerConnection?.addTrack(localAudioTrack)

//        localStream.preservedVideoTracks.forEach {
//            peerConnection?.addTrack(it)
//            Log.d("addstream", "startLocalVideoCapture add track completed")
//        }

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

                            CoroutineScope(IO).launch {
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
                    CoroutineScope(IO).launch {
                        firebaseRepository.setOfferAndAnswer(answerModel)
                    }

                    peerConnection?.setLocalDescription(SdpObserverImpl(
                        onSetSuccessCallback = {
                            Log.d(TAG, "answer: onSetSuccessCallback called")
//                            val answerModel = OfferModel(sdp.description, sdp.type.name)
//
//                            setOfferAndAnswerJob = CoroutineScope(Dispatchers.IO).launch {
//                                firebaseRepository.setOfferAndAnswer(answerModel)
//                            }
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

        CoroutineScope(IO).launch {
            firebaseRepository.setOfferAndAnswer(OfferModel(type = Constants.TYPE.END.name))
        }
        peerConnection?.close()
    }


    fun checkDataChannelState() {
        if (localDataChannel?.state() == DataChannel.State.OPEN) {
            Log.d(WEB_RTC_DATA_CHANNEL_TAG, "checkDataChannelState: OPEN")
        } else {
            Log.d(WEB_RTC_DATA_CHANNEL_TAG, "checkDataChannelState: CLOSE")
        }
    }

    fun sendTextMessage(message: String) {
        Log.d(WEB_RTC_DATA_CHANNEL_TAG, "sendTextMessage: called")
        localDataChannel?.send(DataChannel.Buffer(stringToByteBuffer("-s$message"), false))
    }

    private fun stringToByteBuffer(
        message: String,
        charset: Charset = Charset.defaultCharset()
    ): ByteBuffer {
        return ByteBuffer.wrap(message.toByteArray(charset))
    }

    fun prepareForSendingImageFile(imageFile: File) {
        val size = imageFile.length().toInt()
        val imgBytes = readPickedFileAsBytes(imageFile, size)
        Log.d(IMAGE_RELATED_TAG, "prepareForSendingImageFile: imgBytes - $imgBytes")
        sendImageFile(imgBytes, imgBytes.size, size)
    }

    private fun readPickedFileAsBytes(imageFile: File, size: Int): ByteArray {
        Log.d(
            IMAGE_RELATED_TAG,
            "readPickedFileAsBytes: called and is file exist - ${imageFile.exists()}"
        )
        return imageFile.readBytes()
//        var bytes = ByteArray(size)
//        try {
//            bytes = imageFile.readBytes()
//
//            val bufferedInputStream = BufferedInputStream(FileInputStream(imageFile))
//            bufferedInputStream.read(bytes, 0, bytes.size)
//            bufferedInputStream.close()
//        } catch (e: FileNotFoundException) {
//            Log.d(IMAGE_TAG, "readPickedFileAsBytes: FileNotFoundException : ${e.localizedMessage}")
//        } catch (e: IOException) {
//            Log.d(IMAGE_TAG, "readPickedFileAsBytes: IOException : ${e.localizedMessage}")
//        }
//        return bytes
    }

    private fun sendImageFile(imgBytes: ByteArray, fileSizeInBytes: Int, fileSize: Int) {
        Log.d(IMAGE_RELATED_TAG, "sendImageFile: called")

        // First sending metadata
        val meta = stringToByteBuffer("-i$fileSizeInBytes", Charset.defaultCharset())
        localDataChannel?.send(DataChannel.Buffer(meta, false))

/*
        // Sending whole image,
        val wrap = ByteBuffer.wrap(imgBytes)
        Log.d(IMAGE_RELATED_TAG, "sendImageFile: Image wrap is ${wrap.toString()}")
        localDataChannel?.send(DataChannel.Buffer(wrap, false))
*/
        // Sending Chunks
        Log.d(
            "WEB_RTC_DATA_CHANNEL_TAG",
            "----------------fileSizeInBytes = $fileSizeInBytes -------------------"
        )

        // We can use fileSize instead of fileSizeInBytes
        val numberOfChunks = fileSizeInBytes / CHUNK_SIZE
        for (i in 0 until numberOfChunks) {
            val wrap = ByteBuffer.wrap(imgBytes, i * CHUNK_SIZE, CHUNK_SIZE)
            localDataChannel?.send(DataChannel.Buffer(wrap, false))
        }
        val remainder = fileSizeInBytes % CHUNK_SIZE
        if (remainder > 0) {
            val wrap = ByteBuffer.wrap(imgBytes, numberOfChunks * CHUNK_SIZE, remainder)
            localDataChannel?.send(DataChannel.Buffer(wrap, false))
        }
    }

    fun enableVideo(isVideoEnabled: Boolean) {
        localVideoTrack?.let {
            it.setEnabled(isVideoEnabled)
        }
    }

    fun enableAudio(isAudioEnable: Boolean) {
        localAudioTrack?.let {
            it.setEnabled(isAudioEnable)
        }
    }

    fun switchCamera() {
        videoCapturer.switchCamera(null)
    }


}