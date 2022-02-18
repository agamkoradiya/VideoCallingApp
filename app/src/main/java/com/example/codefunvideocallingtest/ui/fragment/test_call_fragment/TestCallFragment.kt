package com.example.codefunvideocallingtest.ui.fragment.test_call_fragment

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isGone
import androidx.navigation.fragment.navArgs
import com.developerspace.webrtcsample.RTCAudioManager
import com.example.codefunvideocallingtest.R
import com.example.codefunvideocallingtest.databinding.FragmentTestCallBinding
import com.example.codefunvideocallingtest.listener.SignalingListenerObserver
import com.example.codefunvideocallingtest.observer.DataChannelObserver
import com.example.codefunvideocallingtest.observer.PeerConnectionObserver
import com.example.codefunvideocallingtest.rtc.regular.PeerConnectionUtil
import com.example.codefunvideocallingtest.rtc.regular.WebRtcClient
import com.example.codefunvideocallingtest.signaling.SignalingMedium
import com.example.codefunvideocallingtest.util.URIPathHelper
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import org.webrtc.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import javax.inject.Inject
import kotlin.properties.Delegates

private const val TAG = "test_CallFragment"
private const val IMAGE_RELATED_TAG = "test_ImageRelated"
private const val WEB_RTC_DATA_CHANNEL_TAG = "test_WebRtcDataChannel"

@AndroidEntryPoint
class TestCallFragment : Fragment() {

    private var _binding: FragmentTestCallBinding? = null
    private val binding get() = _binding!!

    private val navArgs by navArgs<TestCallFragmentArgs>()

    private lateinit var roomName: String
    private var isJoin by Delegates.notNull<Boolean>()

    private lateinit var webRtcClient: WebRtcClient
    private lateinit var peerConnectionUtil: PeerConnectionUtil

    private lateinit var signalingMedium: SignalingMedium

    @Inject
    lateinit var eglBase: EglBase

    @Inject
    lateinit var firebaseFirestore: FirebaseFirestore

    // Control buttons
    private var isMute = false
    private var isVideoPaused = false
    private var inSpeakerMode = true

    private val audioManager by lazy { RTCAudioManager.create(requireContext()) }

    // Related sending or receiving Images
    var incomingFileSize by Delegates.notNull<Int>()

    //    var currentIndexPointer by Delegates.notNull<Int>()
    var currentIndexPointer = 0
    lateinit var imageFileInBytes: ByteArray
    private var isReceivingFile = false

    private val uriPathHelper = URIPathHelper()
    private var launchGalleryActivityForImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val imageUrl: Uri? = result.data?.data
                // your operation...
                imageUrl?.let {
                    Log.d(IMAGE_RELATED_TAG, "Image Uri from gallery is - :   ${it.path} ")
                    val filePath = uriPathHelper.getPath(requireContext(), it)
                    Log.d(IMAGE_RELATED_TAG, "Image File path from gallery is - :   $filePath ")

                    filePath?.let {
                        val imageFile = File(filePath)
                        webRtcClient.prepareForSendingImageFile(imageFile)
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTestCallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        receivingPreviousFragmentData()
        initializingClasses()

        audioManager.selectAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
        audioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)

        binding.sendBtn.setOnClickListener {
            val message = binding.chatEdt.text.toString()
            webRtcClient.sendTextMessage(message)
        }

        binding.sendImgBtn.setOnClickListener {
            binding.receivedImage.visibility = GONE
            val intent = Intent("android.intent.action.GET_CONTENT")
            intent.type = "image/*"
            launchGalleryActivityForImage.launch(intent)
        }

        binding.micBtn.setOnClickListener {
            if (isMute) {
                isMute = false
                binding.micBtn.setImageResource(R.drawable.ic_baseline_mic_off_24)
            } else {
                isMute = true
                binding.micBtn.setImageResource(R.drawable.ic_baseline_mic_24)
            }
            webRtcClient.enableAudio(isMute)
        }

        binding.videoBtn.setOnClickListener {
            if (isVideoPaused) {
                isVideoPaused = false
                binding.videoBtn.setImageResource(R.drawable.ic_baseline_videocam_off_24)
            } else {
                isVideoPaused = true
                binding.videoBtn.setImageResource(R.drawable.ic_baseline_videocam_24)
            }
            webRtcClient.enableVideo(isVideoPaused)
        }

        binding.switchCameraBtn.setOnClickListener {
            webRtcClient.switchCamera()
        }

        binding.audioOutputBtn.setOnClickListener {
            if (inSpeakerMode) {
                inSpeakerMode = false
                binding.audioOutputBtn.setImageResource(R.drawable.ic_baseline_hearing_24)
                audioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.EARPIECE)
            } else {
                inSpeakerMode = true
                binding.audioOutputBtn.setImageResource(R.drawable.ic_baseline_speaker_up_24)
                audioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
            }
        }

        binding.endCallBtn.setOnClickListener {
            webRtcClient.endCall()
            signalingMedium.destroy()
        }
    }


    private fun receivingPreviousFragmentData() {
        roomName = navArgs.roomId
        isJoin = navArgs.isJoin

        Log.d(TAG, "receivingPreviousFragmentData: roomName = $roomName & isJoin = $isJoin")
    }

    private fun initializingClasses() {
        peerConnectionUtil = PeerConnectionUtil(
            requireActivity().application,
            eglBase.eglBaseContext
        )

        webRtcClient = WebRtcClient(
            context = requireActivity().application,
//            peerConnectionUtil = peerConnectionUtil,
            eglBase = eglBase,
            firebaseFirestore = firebaseFirestore,
            roomName = roomName,
            dataChannelObserver = DataChannelObserver(
                onBufferedAmountChangeCallback = {
                    Log.d(WEB_RTC_DATA_CHANNEL_TAG, "onBufferedAmountChange: called")
                },
                onStateChangeCallback = {
                    Log.d(WEB_RTC_DATA_CHANNEL_TAG, "onStateChange: called")
                    webRtcClient.checkDataChannelState()
                },
                onMessageCallback = {
                    Log.d(WEB_RTC_DATA_CHANNEL_TAG, "onMessage: called")
                }
            ),
            peerConnectionObserver = PeerConnectionObserver(
                onIceCandidateCallback = {
                    signalingMedium.sendIceCandidateModelToUser(it, isJoin)
                    webRtcClient.addIceCandidate(it)
                },
                onTrackCallback = {
                    val videoTrack = it.receiver.track() as VideoTrack
                    videoTrack.addSink(binding.remoteView)
                },
                onAddStreamCallback = {
                    Log.d(
                        TAG,
                        "onAddStreamCallback: ${it.videoTracks.first()}"
                    )
                    Log.d(TAG, "onAddStreamCallback: ${it.videoTracks}")
                    Log.d(TAG, "onAddStreamCallback: ${it.toString()}")
                    it.videoTracks.first().addSink(binding.remoteView)
                },
                onDataChannelCallback = { dataChannel ->
                    Log.d(
                        WEB_RTC_DATA_CHANNEL_TAG,
                        "onDataChannelCallback: state -> ${dataChannel.state()}"
                    )
                    dataChannel.registerObserver(
                        DataChannelObserver(
                            onStateChangeCallback = {
                                Log.d(
                                    WEB_RTC_DATA_CHANNEL_TAG,
                                    "onDataChannelCallback - onStateChangeCallback - remote data channel state -> ${
                                        dataChannel.state()
                                    }"
                                )
                            },
                            onMessageCallback = {
                                Log.d(
                                    WEB_RTC_DATA_CHANNEL_TAG,
                                    "onDataChannelCallback - onMessageCallback -> got Message"
                                )
                                readIncomingMessage(it.data)
                            }
                        )
                    )
                }
            )
        )
        webRtcClient.createLocalDataChannel()
        gettingCameraPictureToShowInLocalView()
    }

    private fun readIncomingMessage(buffer: ByteBuffer?) {
        Log.d(WEB_RTC_DATA_CHANNEL_TAG, "readIncomingMessage: called")

        if (buffer != null) {
            val bytes: ByteArray

            if (buffer.hasArray()) {
                bytes = buffer.array()
            } else {
                bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
            }

            if (!isReceivingFile) {

                val firstMessage = String(bytes, Charset.defaultCharset())
                val type = firstMessage.substring(0, 2)

                if (type == "-i") {
                    incomingFileSize = firstMessage.substring(2, firstMessage.length).toInt()
                    imageFileInBytes = ByteArray(incomingFileSize)
                    Log.d(
                        WEB_RTC_DATA_CHANNEL_TAG,
                        "readIncomingMessage -i: incoming images file size = $incomingFileSize"
                    )
                    isReceivingFile = true
                } else if (type == "-s") {
                    // Setting text in text view
                    val msgString = firstMessage.substring(2, firstMessage.length)
                    Log.d(WEB_RTC_DATA_CHANNEL_TAG, "readIncomingMessage is: $msgString")
                    settingMessageInTextView(msgString)
                }

            } else {
                for (b in bytes) {
                    imageFileInBytes[currentIndexPointer++] = b
                }

                Log.d(
                    WEB_RTC_DATA_CHANNEL_TAG,
                    "readIncomingMessage: currentIndexPointer - $currentIndexPointer & inComingFileSize - $incomingFileSize"
                )

                if (currentIndexPointer == incomingFileSize) {
                    Log.d(WEB_RTC_DATA_CHANNEL_TAG, "readIncomingMessage: received all bytes")

                    val bitmap =
                        BitmapFactory.decodeByteArray(imageFileInBytes, 0, incomingFileSize)

                    isReceivingFile = false
                    currentIndexPointer = 0

                    Log.d(
                        WEB_RTC_DATA_CHANNEL_TAG,
                        "readIncomingMessage: bitmap  is ${bitmap.height}/${bitmap.width} = $bitmap"
                    )

                    activity?.runOnUiThread {
                        Log.d(WEB_RTC_DATA_CHANNEL_TAG, "readIncomingMessage: setting bitmap...")
                        binding.receivedImage.visibility = VISIBLE
                        binding.receivedImage.setImageBitmap(bitmap)
                    }
                }
//                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, incomingFileSize)

            }
        }
    }

    private fun settingMessageInTextView(message: String) {
        activity?.runOnUiThread {
            binding.receivedMessageTxt.apply {
                text = "${this.text} \n$message"
            }
        }
    }

    private fun gettingCameraPictureToShowInLocalView() {
        webRtcClient.initSurfaceView(binding.remoteView)
        webRtcClient.initSurfaceView(binding.localView)
        webRtcClient.startLocalVideoCapture(binding.localView)

        handlingSignalingClient()
    }

    private fun handlingSignalingClient() {
        signalingMedium = SignalingMedium(
            roomName = roomName,
            firebaseFirestore = firebaseFirestore,
            signalingListener = SignalingListenerObserver(
                onConnectionEstablishedCallback = {
                    Log.d(
                        "signalingListener",
                        "handlingSignalingClient: onConnectionEstablishedCallback called"
                    )
                    binding.endCallBtn.isClickable = true
                },
                onOfferReceivedCallback = {
                    Log.d(
                        "signalingListener",
                        "handlingSignalingClient: onOfferReceivedCallback called"
                    )
                    webRtcClient.setRemoteDescription(it)
                    webRtcClient.answer()
                    binding.loadingBar.isGone = true
                },
                onAnswerReceivedCallback = {
                    Log.d(
                        "signalingListener",
                        "handlingSignalingClient: onAnswerReceivedCallback called"
                    )
                    webRtcClient.setRemoteDescription(it)
                    binding.loadingBar.isGone = true
                },
                onIceCandidateReceivedCallback = {
                    Log.d(
                        "signalingListener",
                        "handlingSignalingClient: onIceCandidateReceivedCallback called"
                    )
                    webRtcClient.addIceCandidate(it)
                },
                onCallEndedCallback = {
                    Log.d(
                        "signalingListener",
                        "handlingSignalingClient: onCallEndedCallback called"
                    )
                    webRtcClient.endCall()
                    signalingMedium.destroy()
                }
            ), isJoin
        )

        if (!isJoin)
            webRtcClient.call()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

