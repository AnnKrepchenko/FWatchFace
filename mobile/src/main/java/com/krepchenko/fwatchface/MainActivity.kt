package com.krepchenko.fwatchface

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.DialogInterface
import android.graphics.Color
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.wearable.companion.WatchFaceCompanion
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.TextView

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.wearable.DataApi
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

class MainActivity : Activity(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<DataApi.DataItemResult> {

    private var mGoogleApiClient: GoogleApiClient? = null
    private var mPeerId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_digital_watch_face_config)

        mPeerId = intent.getStringExtra(WatchFaceCompanion.EXTRA_PEER_ID)
        mGoogleApiClient = GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build()

    }

    override fun onStart() {
        super.onStart()
        mGoogleApiClient!!.connect()
    }

    override fun onStop() {
        if (mGoogleApiClient != null && mGoogleApiClient!!.isConnected) {
            mGoogleApiClient!!.disconnect()
        }
        super.onStop()
    }

    override // GoogleApiClient.ConnectionCallbacks
    fun onConnected(connectionHint: Bundle?) {
        Log.d(TAG, "onConnected: " + connectionHint!!)

        if (mPeerId != null) {
            val builder = Uri.Builder()
            val uri = builder.scheme("wear").path(PATH_WITH_FEATURE).authority(mPeerId).build()
            Wearable.DataApi.getDataItem(mGoogleApiClient, uri).setResultCallback(this)
        } else {
            displayNoConnectedDeviceDialog()
        }
    }

    override // ResultCallback<DataApi.DataItemResult>
    fun onResult(dataItemResult: DataApi.DataItemResult) {
        if (dataItemResult.status.isSuccess && dataItemResult.dataItem != null) {
            val configDataItem = dataItemResult.dataItem
            val dataMapItem = DataMapItem.fromDataItem(configDataItem)
            val config = dataMapItem.dataMap
            setUpAllPickers(config)
        } else {
            // If DataItem with the current config can't be retrieved, select the default items on
            // each picker.
            setUpAllPickers(null)
        }
    }

    override // GoogleApiClient.ConnectionCallbacks
    fun onConnectionSuspended(cause: Int) {
        Log.d(TAG, "onConnectionSuspended: " + cause)
    }

    override // GoogleApiClient.OnConnectionFailedListener
    fun onConnectionFailed(result: ConnectionResult) {
        Log.d(TAG, "onConnectionFailed: " + result)
    }

    private fun displayNoConnectedDeviceDialog() {
        val builder = AlertDialog.Builder(this)
        val messageText = resources.getString(R.string.title_no_device_connected)
        val okText = resources.getString(R.string.ok_no_device_connected)
        builder.setMessage(messageText)
                .setCancelable(false)
                .setPositiveButton(okText) { dialog, id -> }
        val alert = builder.create()
        alert.show()
    }

    /**
     * Sets up selected items for all pickers according to given `config` and sets up their
     * item selection listeners.

     * @param config the `DigitalWatchFaceService` config [DataMap]. If null, the
     * *         default items are selected.
     */
    private fun setUpAllPickers(config: DataMap?) {
        setUpColorPickerSelection(R.id.background, KEY_BACKGROUND_COLOR, config,
                R.string.color_black)
        setUpColorPickerSelection(R.id.hours, KEY_HOURS_COLOR, config, R.string.color_white)
        setUpColorPickerSelection(R.id.minutes, KEY_MINUTES_COLOR, config, R.string.color_white)
        setUpColorPickerSelection(R.id.seconds, KEY_SECONDS_COLOR, config, R.string.color_gray)

        setUpColorPickerListener(R.id.background, KEY_BACKGROUND_COLOR)
        setUpColorPickerListener(R.id.hours, KEY_HOURS_COLOR)
        setUpColorPickerListener(R.id.minutes, KEY_MINUTES_COLOR)
        setUpColorPickerListener(R.id.seconds, KEY_SECONDS_COLOR)
    }

    private fun setUpColorPickerSelection(spinnerId: Int, configKey: String, config: DataMap?,
                                          defaultColorNameResId: Int) {
        val defaultColorName = getString(defaultColorNameResId)
        val defaultColor = Color.parseColor(defaultColorName)
        val color: Int
        if (config != null) {
            color = config.getInt(configKey, defaultColor)
        } else {
            color = defaultColor
        }
        val spinner = findViewById(spinnerId) as Spinner
        val colorNames = resources.getStringArray(R.array.color_array)
        for (i in colorNames.indices) {
            if (Color.parseColor(colorNames[i]) == color) {
                spinner.setSelection(i)
                break
            }
        }
    }

    private fun setUpColorPickerListener(spinnerId: Int, configKey: String) {
        val spinner = findViewById(spinnerId) as Spinner
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View, pos: Int, id: Long) {
                val colorName = adapterView.getItemAtPosition(pos) as String
                sendConfigUpdateMessage(configKey, Color.parseColor(colorName))
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {}
        }
    }

    private fun sendConfigUpdateMessage(configKey: String, color: Int) {
        if (mPeerId != null) {
            val config = DataMap()
            config.putInt(configKey, color)
            val rawData = config.toByteArray()
            Wearable.MessageApi.sendMessage(mGoogleApiClient, mPeerId, PATH_WITH_FEATURE, rawData)

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Sent watch face config message: " + configKey + " -> "
                        + Integer.toHexString(color))
            }
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName

        // TODO: use the shared constants (needs covering all the samples with Gradle build model)
        private val KEY_BACKGROUND_COLOR = "BACKGROUND_COLOR"
        private val KEY_HOURS_COLOR = "HOURS_COLOR"
        private val KEY_MINUTES_COLOR = "MINUTES_COLOR"
        private val KEY_SECONDS_COLOR = "SECONDS_COLOR"
        private val PATH_WITH_FEATURE = "/watch_face_config/Digital"
    }
}
