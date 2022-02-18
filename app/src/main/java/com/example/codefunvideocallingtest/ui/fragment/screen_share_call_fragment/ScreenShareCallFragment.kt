package com.example.codefunvideocallingtest.ui.fragment.screen_share_call_fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.example.codefunvideocallingtest.R
import com.example.codefunvideocallingtest.databinding.FragmentScreenShareCallBinding
import com.example.codefunvideocallingtest.listener.SignalingListenerObserver
import com.example.codefunvideocallingtest.observer.DataChannelObserver
import com.example.codefunvideocallingtest.observer.PeerConnectionObserver
import com.example.codefunvideocallingtest.rtc.screenshare.PeerConnectionUtilForScreenSharing
import com.example.codefunvideocallingtest.rtc.screenshare.WebRtcClientForScreenSharing
import com.example.codefunvideocallingtest.services.screen_share.MediaProjectionService
import com.example.codefunvideocallingtest.signaling.SignalingMedium
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import javax.inject.Inject
import kotlin.properties.Delegates

private const val TAG = "screen_CallFragment"
private const val WEB_RTC_DATA_CHANNEL_TAG = "screen_WebRtcDataChannel"
private const val TAG_SCREEN_SHARE = "screen_ScreenShare"

@AndroidEntryPoint
class ScreenShareCallFragment : Fragment() {

    private var _binding: FragmentScreenShareCallBinding? = null
    private val binding get() = _binding!!

    private val navArgs by navArgs<ScreenShareCallFragmentArgs>()

    private lateinit var roomName: String
    private var isJoin by Delegates.notNull<Boolean>()

    private lateinit var webRtcClient: WebRtcClientForScreenSharing
    private lateinit var peerConnectionUtilForScreenSharing: PeerConnectionUtilForScreenSharing

    private lateinit var signalingMedium: SignalingMedium

    @Inject
    lateinit var eglBase: EglBase

    @Inject
    lateinit var firebaseFirestore: FirebaseFirestore

    // Control buttons
    private var isMute = false

    // For screen sharing
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var sDeviceWidth by Delegates.notNull<Int>()
    private var sDeviceHeight by Delegates.notNull<Int>()

    private val getPermissionForScreenSharingContracts = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(), ActivityResultCallback {
            if (it.resultCode == Activity.RESULT_OK && it.data != null) {
                webRtcClient.startScreenSharing(binding.localView, it.data, sDeviceWidth,sDeviceHeight)
            }
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScreenShareCallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        receivingPreviousFragmentData()

        requireActivity().apply {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            sDeviceWidth = metrics.widthPixels
            sDeviceHeight = metrics.heightPixels
        }

        if (isJoin) {
            if (Build.VERSION.SDK_INT > 28) {
                val intent = Intent(requireContext(), MediaProjectionService::class.java)
                requireActivity().startForegroundService(intent)
            }
        }

        initializingClasses()

        binding.switchCameraBtn.setOnClickListener {
            webRtcClient.switchCamera()
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

        binding.endCallBtn.setOnClickListener {

            if (isJoin) {
                if (Build.VERSION.SDK_INT > 28) {
                    val intent = Intent(requireContext(), MediaProjectionService::class.java)
                    requireActivity().stopService(intent)
                }
            }

            webRtcClient.endCall()
            signalingMedium.destroy()
        }

        binding.shareScreenBtn.setOnClickListener {
//
//            if (isJoin)
//                startScreenCapture()
//            else
//                webRtcClient.startLocalVideoCapture(binding.localView)

        }
    }

    private fun receivingPreviousFragmentData() {
        roomName = navArgs.roomId
        isJoin = navArgs.isJoin

        Log.d(TAG, "receivingPreviousFragmentData: roomName = $roomName & isJoin = $isJoin")
    }

    private fun initializingClasses() {
        peerConnectionUtilForScreenSharing = PeerConnectionUtilForScreenSharing(
            requireActivity().application,
            eglBase.eglBaseContext
        )

        webRtcClient = WebRtcClientForScreenSharing(
            context = requireActivity().application,
            peerConnectionUtil = peerConnectionUtilForScreenSharing,
            eglBase = eglBase,
            firebaseFirestore = firebaseFirestore,
            roomName = roomName,
            peerConnectionObserver = PeerConnectionObserver(
                onIceCandidateCallback = {
                    signalingMedium.sendIceCandidateModelToUser(it, isJoin)
                    webRtcClient.addIceCandidate(it)
                },
                onTrackCallback = {
                    Log.d(TAG, "onTrackCallback: ${it.toString()}")
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
                            }
                        )
                    )
                }
            )
        )
        gettingCameraPictureToShowInLocalView()
    }

    private fun gettingCameraPictureToShowInLocalView() {
        webRtcClient.initSurfaceView(binding.remoteView)
        webRtcClient.initSurfaceView(binding.localView)

            if (isJoin)
                startScreenCapture()
            else
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


    private fun startScreenCapture() {

        runBlocking {
            delay(1000)
        }

        mediaProjectionManager = activity?.application?.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
        // Get Permission for screen sharing
//        startActivityForResult(
//            mediaProjectionManager.createScreenCaptureIntent(),
//            CAPTURE_PERMISSION_REQUEST_CODE
//        )
        getPermissionForScreenSharingContracts.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        if (requestCode == CAPTURE_PERMISSION_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
//            Log.d(TAG, "onActivityResult: result code : $resultCode , data : ${data.toString()}")
//
//
//            val videoCapturerAndroid = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
//                override fun onStop() {
//                    super.onStop()
//                    Log.d(TAG_SCREEN_SHARE, "onStop: video capturer android onstop called")
//                }
//            })
//
//            webRtcClient.startScreenSharing(binding.localView, videoCapturerAndroid)
//        }
//        return
//    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}