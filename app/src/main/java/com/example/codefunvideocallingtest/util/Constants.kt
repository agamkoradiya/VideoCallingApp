package com.example.codefunvideocallingtest.util

object Constants {

//    const val VIDEO_WIDTH = 320
//    const val VIDEO_HEIGHT = 240
//    const val VIDEO_FPS = 25
    const val VIDEO_WIDTH = 280
    const val VIDEO_HEIGHT = 200
    const val VIDEO_FPS = 20

    const val LOCAL_TRACK_ID = "local_track"
    const val LOCAL_STREAM_ID = "stream_track"

    const val SCREEN_SHARE_TRACK_ID = "local_track"
    const val SCREEN_SHARE_LOCAL_STREAM_ID = "stream_track"

    const val DATA_CHANNEL_NAME = "sendDataChannel"

    const val CHUNK_SIZE = 64000
    //firebase
    const val MAIN_COLLECTION_NAME = "WebRTC"
    const val SUB_COLLECTION_NAME = "Users"

    const val CAPTURE_PERMISSION_REQUEST_CODE = 999


    const val NOTIFICATION_CHANNEL_ID = "channel_id"
    const val NOTIFICATION_ID = 77
    const val NOTIFICATION_CHANNEL_NAME = "Screen Sharing"

    enum class USERTYPE {
        OFFER_USER,
        ANSWER_USER
    }

    enum class TYPE {
        OFFER,
        ANSWER,
        END
    }
}