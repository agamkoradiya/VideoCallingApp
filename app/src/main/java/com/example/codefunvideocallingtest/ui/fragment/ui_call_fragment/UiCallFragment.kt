package com.example.codefunvideocallingtest.ui.fragment.ui_call_fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.developerspace.webrtcsample.RTCAudioManager
import com.example.codefunvideocallingtest.R
import com.example.codefunvideocallingtest.adapter.ui_call.TextMessagesAdapter
import com.example.codefunvideocallingtest.databinding.FragmentUiCallBinding
import com.example.codefunvideocallingtest.listener.SignalingListenerObserver
import com.example.codefunvideocallingtest.observer.DataChannelObserver
import com.example.codefunvideocallingtest.observer.PeerConnectionObserver
import com.example.codefunvideocallingtest.rtc.regular.PeerConnectionUtil
import com.example.codefunvideocallingtest.rtc.regular.WebRtcClient
import com.example.codefunvideocallingtest.signaling.SignalingMedium
import com.example.codefunvideocallingtest.util.URIPathHelper
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import javax.inject.Inject
import kotlin.properties.Delegates

private const val TAG = "ui_CallFragment"
private const val IMAGE_RELATED_TAG = "ui_ImageRelated"
private const val WEB_RTC_DATA_CHANNEL_TAG = "ui_WebRtcDataChannel"

@AndroidEntryPoint
class UiCallFragment : Fragment() {

    private var _binding: FragmentUiCallBinding? = null
    private val binding get() = _binding!!

    private val navArgs by navArgs<UiCallFragmentArgs>()

    private lateinit var roomName: String
    private var isJoin by Delegates.notNull<Boolean>()

    private lateinit var webRtcClient: WebRtcClient
    private lateinit var peerConnectionUtil: PeerConnectionUtil

    private lateinit var signalingMedium: SignalingMedium

    @Inject
    lateinit var eglBase: EglBase

    @Inject
    lateinit var firebaseFirestore: FirebaseFirestore

    @Inject
    lateinit var textMessagesAdapter: TextMessagesAdapter

    private val uiCallViewModel by viewModels<UiCallViewModel>()

    // Control buttons
    private var isMute = false
    private var isVideoPaused = false
    private var inSpeakerMode = true

    private val audioManager by lazy { RTCAudioManager.create(requireContext()) }

    // Related sending or receiving Images
    var incomingFileSize by Delegates.notNull<Int>()

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
                    Log.d(IMAGE_RELATED_TAG, "Image Uri from gallery is - ${it.path} ")
                    val filePath = uriPathHelper.getPath(requireContext(), it)
                    Log.d(IMAGE_RELATED_TAG, "Image File path from gallery is - $filePath")

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
        _binding = FragmentUiCallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setValues()
        observingValues()
        receivingPreviousFragmentData()
        initializingClasses()

//        audioManager.selectAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
//        audioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)

        binding.sendMessageBtn.setOnClickListener {
            val message = binding.chatMessageEdt.text.toString()
            webRtcClient.sendTextMessage(message)
            binding.chatMessageEdt.setText("")

            val inputManager: InputMethodManager =
                requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputManager.hideSoftInputFromWindow(
                view.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
        }

        binding.sendImageBtn.setOnClickListener {
            val intent = Intent("android.intent.action.GET_CONTENT")
            intent.type = "image/*"
            launchGalleryActivityForImage.launch(intent)
        }

        binding.receivedImageDeleteBtn.setOnClickListener {
            showOrHideReceivedImageView(GONE)
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

    private fun setValues() {
        binding.chatMessageRecyclerView.adapter = textMessagesAdapter
    }

    private fun observingValues() {
        uiCallViewModel.receivedTextMessages.observe(viewLifecycleOwner) {
            Log.d("viewmodel", "observingValues: called ${it.toString()}")
            textMessagesAdapter.submitList(it.reversed())
            textMessagesAdapter.notifyItemChanged(it.size)
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
                    Log.d(TAG, "onAddStreamCallback: ${it.videoTracks.first()}")
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
                },
                onAnswerReceivedCallback = {
                    Log.d(
                        "signalingListener",
                        "handlingSignalingClient: onAnswerReceivedCallback called"
                    )
                    webRtcClient.setRemoteDescription(it)
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
                    activity?.runOnUiThread {
                        showProgressForImage(VISIBLE, incomingFileSize)
                    }
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
                activity?.runOnUiThread {
                    binding.progressBar.progress = currentIndexPointer
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
                        showProgressForImage(GONE)
                        showOrHideReceivedImageView(VISIBLE, bitmap)
                    }
                }
            }
        }
    }


    private fun settingMessageInTextView(message: String) {
        uiCallViewModel.addTextMessage(message)
    }

    private fun showOrHideReceivedImageView(visibility: Int, bitmap: Bitmap? = null) {
        binding.apply {
            if (visibility == GONE || bitmap == null) {
                receivedImgView.setImageBitmap(null)
                receivedImgView.visibility = GONE
                receivedImageDeleteBtn.visibility = GONE
            } else {
                receivedImgView.setImageBitmap(bitmap)
                receivedImgView.visibility = VISIBLE
                receivedImageDeleteBtn.visibility = VISIBLE
            }
        }
    }

    private fun showProgressForImage(visibility: Int, max: Int? = null) {
        if (visibility == GONE || max == null) {
            binding.apply {
                progressBar.max = 0
                progressBar.setProgress(0, true)
                progressBar.visibility = GONE
            }
        } else {
            Snackbar.make(binding.root, "Receiving Image File", Snackbar.LENGTH_SHORT).show()
            binding.apply {
                progressBar.visibility = VISIBLE
                progressBar.max = max
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}