package com.krepchenko.fwatchface.utils

import android.graphics.Color
import android.net.Uri
import android.util.Log

import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.wearable.DataApi
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object FWatchFaceUtil {
    private val TAG = "DigitalWatchFaceUtil"

    /**
     * The [DataMap] key for [DigitalWatchFaceService] background color name.
     * The color name must be a [String] recognized by [Color.parseColor].
     */
    val KEY_BACKGROUND_COLOR = "BACKGROUND_COLOR"

    /**
     * The [DataMap] key for [DigitalWatchFaceService] hour digits color name.
     * The color name must be a [String] recognized by [Color.parseColor].
     */
    val KEY_TEXT_COLOR = "HOURS_COLOR"

    /**
     * The [DataMap] key for [DigitalWatchFaceService] minute digits color name.
     * The color name must be a [String] recognized by [Color.parseColor].
     */
    val KEY_MINUTES_COLOR = "MINUTES_COLOR"

    /**
     * The [DataMap] key for [DigitalWatchFaceService] second digits color name.
     * The color name must be a [String] recognized by [Color.parseColor].
     */
    val KEY_SECONDS_COLOR = "SECONDS_COLOR"

    /**
     * The path for the [DataItem] containing [DigitalWatchFaceService] configuration.
     */
    val PATH_WITH_FEATURE = "/watch_face_config/Digital"

    /**
     * Name of the default interactive mode background color and the ambient mode background color.
     */
    val COLOR_NAME_DEFAULT_AND_AMBIENT_BACKGROUND = "Black"
    val COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND = parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_BACKGROUND)

    /**
     * Name of the default interactive mode hour digits color and the ambient mode hour digits
     * color.
     */
    val COLOR_NAME_DEFAULT_AND_AMBIENT_HOUR_DIGITS = "White"
    val COLOR_VALUE_DEFAULT_AND_AMBIENT_TEXT = parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_HOUR_DIGITS)

    /**
     * Name of the default interactive mode minute digits color and the ambient mode minute digits
     * color.
     */
    val COLOR_NAME_DEFAULT_AND_AMBIENT_MINUTE_DIGITS = "White"
    val COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS = parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_MINUTE_DIGITS)

    /**
     * Name of the default interactive mode second digits color and the ambient mode second digits
     * color.
     */
    val COLOR_NAME_DEFAULT_AND_AMBIENT_SECOND_DIGITS = "Gray"
    val COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS = parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_SECOND_DIGITS)

    /**
     * Callback interface to perform an action with the current config [DataMap] for
     * [DigitalWatchFaceService].
     */
    interface FetchConfigDataMapCallback {
        /**
         * Callback invoked with the current config [DataMap] for
         * [DigitalWatchFaceService].
         */
        fun onConfigDataMapFetched(config: DataMap)
    }

    private fun parseColor(colorName: String): Int {
        return Color.parseColor(colorName.toLowerCase())
    }

    /**
     * Asynchronously fetches the current config [DataMap] for [DigitalWatchFaceService]
     * and passes it to the given callback.
     *
     *
     * If the current config [DataItem] doesn't exist, it isn't created and the callback
     * receives an empty DataMap.
     */
    fun fetchConfigDataMap(client: GoogleApiClient,
                           callback: FetchConfigDataMapCallback) {
        Wearable.NodeApi.getLocalNode(client).setResultCallback { getLocalNodeResult ->
            val localNode = getLocalNodeResult.node.id
            val uri = Uri.Builder()
                    .scheme("wear")
                    .path(PATH_WITH_FEATURE)
                    .authority(localNode)
                    .build()
            Wearable.DataApi.getDataItem(client, uri)
                    .setResultCallback(DataItemResultCallback(callback))
        }
    }

    /**
     * Overwrites (or sets, if not present) the keys in the current config [DataItem] with
     * the ones appearing in the given [DataMap]. If the config DataItem doesn't exist,
     * it's created.
     *
     *
     * It is allowed that only some of the keys used in the config DataItem appear in
     * `configKeysToOverwrite`. The rest of the keys remains unmodified in this case.
     */
    fun overwriteKeysInConfigDataMap(googleApiClient: GoogleApiClient,
                                     configKeysToOverwrite: DataMap) {

        fetchConfigDataMap(googleApiClient,
                object : FetchConfigDataMapCallback {
                    override fun onConfigDataMapFetched(currentConfig: DataMap) {
                        val overwrittenConfig = DataMap()
                        overwrittenConfig.putAll(currentConfig)
                        overwrittenConfig.putAll(configKeysToOverwrite)
                        putConfigDataItem(googleApiClient, overwrittenConfig)
                    }
                }
        )
    }

    /**
     * Overwrites the current config [DataItem]'s [DataMap] with `newConfig`.
     * If the config DataItem doesn't exist, it's created.
     */
    fun putConfigDataItem(googleApiClient: GoogleApiClient, newConfig: DataMap) {
        val putDataMapRequest = PutDataMapRequest.create(PATH_WITH_FEATURE)
        val configToPut = putDataMapRequest.dataMap
        configToPut.putAll(newConfig)
        Wearable.DataApi.putDataItem(googleApiClient, putDataMapRequest.asPutDataRequest())
                .setResultCallback { dataItemResult ->
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "putDataItem result status: " + dataItemResult.status)
                    }
                }
    }

    private class DataItemResultCallback(private val mCallback: FetchConfigDataMapCallback) : ResultCallback<DataApi.DataItemResult> {

        override fun onResult(dataItemResult: DataApi.DataItemResult) {
            if (dataItemResult.status.isSuccess) {
                if (dataItemResult.dataItem != null) {
                    val configDataItem = dataItemResult.dataItem
                    val dataMapItem = DataMapItem.fromDataItem(configDataItem)
                    val config = dataMapItem.dataMap
                    mCallback.onConfigDataMapFetched(config)
                } else {
                    mCallback.onConfigDataMapFetched(DataMap())
                }
            }
        }
    }
}