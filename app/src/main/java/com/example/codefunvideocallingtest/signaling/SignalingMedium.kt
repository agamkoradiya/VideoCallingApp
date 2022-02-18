package com.example.codefunvideocallingtest.signaling

import android.util.Log
import com.example.codefunvideocallingtest.listener.SignalingListener
import com.example.codefunvideocallingtest.model.IceCandidateModel
import com.example.codefunvideocallingtest.model.OfferModel
import com.example.codefunvideocallingtest.util.Constants
import com.example.codefunvideocallingtest.util.Constants.SUB_COLLECTION_NAME
import com.example.codefunvideocallingtest.util.Constants.TYPE.*
import com.example.codefunvideocallingtest.util.Constants.USERTYPE.ANSWER_USER
import com.example.codefunvideocallingtest.util.Constants.USERTYPE.OFFER_USER
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import kotlin.coroutines.CoroutineContext

/**
 * Created by Agam on 02-02-2022.
 */

private const val TAG = "SignalingMedium"

class SignalingMedium(
    roomName: String,
    private val firebaseFirestore: FirebaseFirestore,
    private val signalingListener: SignalingListener,
    private val isJoin: Boolean
) : CoroutineScope {

    private val roomDocument =
        firebaseFirestore.collection(Constants.MAIN_COLLECTION_NAME).document(roomName)
    private val userCollection = roomDocument.collection(SUB_COLLECTION_NAME)

    private val job = Job()

    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    private var sdpType: Constants.TYPE? = null

    init {
        connectionEstablishedTask()
        checkingIsThereAnyOfferOrAnswer()
    }


    private fun connectionEstablishedTask() = launch {
        firebaseFirestore.enableNetwork().addOnSuccessListener {
            signalingListener.onConnectionEstablished()
        }
    }

    private fun checkingIsThereAnyOfferOrAnswer() = launch {
        try {
            roomDocument.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.d(TAG, "checkingIsThereAnyOfferOrAnswer: Error - ${error.localizedMessage}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {

                    val data = snapshot.toObject(OfferModel::class.java)
                    Log.d(TAG, "checkingIsThereAnyOfferOrAnswer: Current Data - ${data.toString()}")

                    data?.let { _data ->
                        // First checking for 'OFFER'
                        if (_data.type == OFFER.name) {
                            signalingListener.onOfferReceived(
                                SessionDescription(
                                    SessionDescription.Type.OFFER,
                                    _data.sdp
                                )
                            )
                            sdpType = OFFER
                        }
                        // Now checking for 'ANSWER'
                        else if (_data.type == ANSWER.name) {
                            signalingListener.onAnswerReceived(
                                SessionDescription(
                                    SessionDescription.Type.ANSWER,
                                    _data.sdp
                                )
                            )
                            sdpType = ANSWER
                        } else if (_data.type == END.name) {
                            signalingListener.onCallEnded()
                            sdpType = END
                        }
                    }

                } else {
                    Log.d(TAG, "checkingIsThereAnyOfferOrAnswer: Current Data - null")
                }
            }


//            gettingIceCandidates()
            userCollection.addSnapshotListener { querySnapshot, error ->
                if (error != null) {
                    Log.d(TAG, "gettingIceCandidates: Error - ${error.localizedMessage}")
                    return@addSnapshotListener
                }

//                if (sdpType == null)
//                    return@addSnapshotListener

                if (querySnapshot != null && !querySnapshot.isEmpty && sdpType != null) {

                    for (dataSnapShot in querySnapshot) {

                        val data = dataSnapShot.toObject(IceCandidateModel::class.java)
                        Log.d(TAG, "gettingIceCandidates: data : ${data.toString()}")

                        if (sdpType == OFFER && data.type == OFFER_USER.name) {
                            signalingListener.onIceCandidateReceived(
                                IceCandidate(
                                    data.sdpMid,
                                    data.sdpMLineIndex!!,
                                    data.sdp
                                )
                            )
                        } else if (sdpType == ANSWER && data.type == ANSWER_USER.name ) {
                            signalingListener.onIceCandidateReceived(
                                IceCandidate(
                                    data.sdpMid,
                                    data.sdpMLineIndex!!,
                                    data.sdp
                                )
                            )
                        }
                    }

                } else {
                    Log.d(TAG, "gettingIceCandidates: Current Data - null")
                }
            }


        } catch (e: Exception) {
            Log.d(TAG, "checkingIsThereAnyOfferOrAnswer: error : ${e.localizedMessage}")
        }
    }

    private fun gettingIceCandidates() = launch {
        userCollection.addSnapshotListener { querySnapshot, error ->
            if (error != null) {
                Log.d(TAG, "gettingIceCandidates: Error - ${error.localizedMessage}")
                return@addSnapshotListener
            }

            if (sdpType == null)
                return@addSnapshotListener

            if (querySnapshot != null && !querySnapshot.isEmpty) {

                for (dataSnapShot in querySnapshot) {

                    val data = dataSnapShot.toObject(IceCandidateModel::class.java)
                    Log.d(TAG, "gettingIceCandidates: data : ${data.toString()}")

                    if (sdpType == OFFER && data.type == OFFER_USER.name) {
                        signalingListener.onIceCandidateReceived(
                            IceCandidate(
                                data.sdpMid,
                                data.sdpMLineIndex!!,
                                data.sdp
                            )
                        )
                    } else if (sdpType == ANSWER && data.type == ANSWER_USER.name) {
                        signalingListener.onIceCandidateReceived(
                            IceCandidate(
                                data.sdpMid,
                                data.sdpMLineIndex!!,
                                data.sdp
                            )
                        )
                    }
                }

            } else {
                Log.d(TAG, "gettingIceCandidates: Current Data - null")
            }
        }
    }


    fun sendIceCandidateModelToUser(iceCandidate: IceCandidate, isJoin: Boolean) = runBlocking {
        Log.d(TAG, "sendIceCandidateModelToUser: called")
        val type: String = when (isJoin) {
            true -> ANSWER_USER.name
            false -> OFFER_USER.name
        }

        val iceCandidateModel = IceCandidateModel(
            serverUrl = iceCandidate.serverUrl,
            sdpMid = iceCandidate.sdpMid,
            sdpMLineIndex = iceCandidate.sdpMLineIndex,
            sdp = iceCandidate.sdp,
            type = type
        )

        Log.d(TAG, "sendIceCandidateModelToUser: iceCandidateModel - $iceCandidateModel")

        userCollection.document(type).set(iceCandidateModel)
            .addOnSuccessListener {
                Log.d(TAG, "sendIceCandidateModelToUser: Success")
            }
            .addOnFailureListener {
                Log.d(TAG, "sendIceCandidateModelToUser: Error $it")
            }

        try {
        } catch (e: Exception) {
            Log.d(TAG, "sendIceCandidateModelToUser: error - ${e.localizedMessage}")
        }
    }


    fun destroy() {
        job.complete()
    }

}