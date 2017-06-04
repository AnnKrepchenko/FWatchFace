package com.krepchenko.fwatchface

import android.os.Bundle
import android.util.Log

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.krepchenko.fwatchface.utils.FWatchFaceUtil

import java.util.concurrent.TimeUnit

/**
 * A [WearableListenerService] listening for [FWatchFaceService] config messages
 * and updating the config [com.google.android.gms.wearable.DataItem] accordingly.
 */
class FWatchFaceConfigListenerService : WearableListenerService(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private var mGoogleApiClient: GoogleApiClient? = null

    override // WearableListenerService
    fun onMessageReceived(messageEvent: MessageEvent?) {
        if (messageEvent!!.path != FWatchFaceUtil.PATH_WITH_FEATURE) {
            return
        }
        val rawData = messageEvent.data
        // It's allowed that the message carries only some of the keys used in the config DataItem
        // and skips the ones that we don't want to change.
        val configKeysToOverwrite = DataMap.fromByteArray(rawData)
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Received watch face config message: " + configKeysToOverwrite)
        }

        if (mGoogleApiClient == null) {
            mGoogleApiClient = GoogleApiClient.Builder(this).addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this).addApi(Wearable.API).build()
        }
        if (!mGoogleApiClient!!.isConnected) {
            val connectionResult = mGoogleApiClient!!.blockingConnect(30, TimeUnit.SECONDS)

            if (!connectionResult.isSuccess) {
                Log.e(TAG, "Failed to connect to GoogleApiClient.")
                return
            }
        }

        FWatchFaceUtil.overwriteKeysInConfigDataMap(mGoogleApiClient!!, configKeysToOverwrite)
    }

    override // GoogleApiClient.ConnectionCallbacks
    fun onConnected(connectionHint: Bundle?) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onConnected: " + connectionHint!!)
        }
    }

    override // GoogleApiClient.ConnectionCallbacks
    fun onConnectionSuspended(cause: Int) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onConnectionSuspended: " + cause)
        }
    }

    override // GoogleApiClient.OnConnectionFailedListener
    fun onConnectionFailed(result: ConnectionResult) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onConnectionFailed: " + result)
        }
    }

    companion object {
        private val TAG = "FListenerService"
    }
}
