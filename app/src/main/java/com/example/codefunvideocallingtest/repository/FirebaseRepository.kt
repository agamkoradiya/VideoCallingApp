package com.example.codefunvideocallingtest.repository

import android.util.Log
import com.example.codefunvideocallingtest.model.OfferModel
import com.example.codefunvideocallingtest.util.Constants.MAIN_COLLECTION_NAME
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.lang.Exception

/**
 * Created by Agam on 03-02-2022.
 */

private const val TAG = "FirebaseRepository"
//private const val TAG = "ABC"

class FirebaseRepository(firebaseFirestore: FirebaseFirestore, roomName: String) {

    private val roomDocument = firebaseFirestore.collection(MAIN_COLLECTION_NAME).document(roomName)

    suspend fun setOfferAndAnswer(offerModel: OfferModel) {
        Log.d(TAG, "setOfferAndAnswer: called")
        try {
            roomDocument.set(offerModel).await()
        } catch (e: Exception) {
            Log.d(TAG, "setOfferAndAnswer: exception - ${e.localizedMessage}")
        }
    }
}