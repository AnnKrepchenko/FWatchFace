/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.krepchenko.fwatchface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.text.format.Time
import android.util.Log
import android.view.SurfaceHolder
import android.view.WindowInsets
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.*
import com.krepchenko.fwatchface.utils.FWatchFaceUtil
import com.krepchenko.fwatchface.utils.FWatchFaceUtil.FetchConfigDataMapCallback
import com.krepchenko.fwatchface.utils.FWatchFaceUtil.fetchConfigDataMap
import com.krepchenko.fwatchface.utils.NumberWordConverter
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * F watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
class FWatchFaceService : CanvasWatchFaceService() {

    override fun onCreateEngine(): CanvasWatchFaceService.Engine {
        return Engine()
    }

    private inner class Engine : CanvasWatchFaceService.Engine(), DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        /** How often [.mUpdateTimeHandler] ticks in milliseconds.  */
        internal var mInteractiveUpdateRateMs = NORMAL_UPDATE_RATE_MS

        /** Handler to update the time periodically in interactive mode.  */
        internal val mUpdateTimeHandler: Handler = object : Handler() {
            override fun handleMessage(message: Message) {
                when (message.what) {
                    MSG_UPDATE_TIME -> {
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "updating time")
                        }
                        invalidate()
                        if (shouldTimerBeRunning()) {
                            val timeMs = System.currentTimeMillis()
                            val delayMs = mInteractiveUpdateRateMs - timeMs % mInteractiveUpdateRateMs
                            this.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
                        }
                    }
                }
            }
        }

        internal var mGoogleApiClient: GoogleApiClient? = GoogleApiClient.Builder(this@FWatchFaceService)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build()

        internal val mTimeZoneReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mTime?.clear(intent.getStringExtra("time-zone"))
                mTime?.setToNow()
            }
        }
        internal var mRegisteredTimeZoneReceiver = false

        private val begin = "It's a fucking\n"
        internal var mBackgroundPaint: Paint? = null
        internal var mHourPaint: Paint? = null
        internal var mMinutePaint: Paint? = null
        internal var mSecondPaint: Paint? = null
        internal var mAmPmPaint: Paint? = null
        internal var mColonPaint: Paint? = null
        internal var mColonWidth: Float = 0.toFloat()
        internal var mMute: Boolean = false
        internal var mTime: Time? = null
        internal var mXOffset: Float = 0.toFloat()
        internal var mYOffset: Float = 0.toFloat()
        private var textSize: Float = 0.toFloat()
        internal var mInteractiveBackgroundColor = FWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND
        internal var mInteractiveHourDigitsColor = FWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_TEXT
        internal var mInteractiveMinuteDigitsColor = FWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS
        internal var mInteractiveSecondDigitsColor = FWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        internal var mLowBitAmbient: Boolean = false

        override fun onCreate(holder: SurfaceHolder?) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate")
            }
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@FWatchFaceService)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build())
            val resources = this@FWatchFaceService.resources
            mYOffset = resources.getDimension(R.dimen.digital_y_offset)

            mBackgroundPaint = Paint()
            mBackgroundPaint?.color = mInteractiveBackgroundColor
            mHourPaint = createTextPaint(mInteractiveHourDigitsColor, BOLD_TYPEFACE)
            mMinutePaint = createTextPaint(mInteractiveMinuteDigitsColor)
            mSecondPaint = createTextPaint(mInteractiveSecondDigitsColor)
            mAmPmPaint = createTextPaint(resources.getColor(R.color.digital_am_pm))
            mColonPaint = createTextPaint(resources.getColor(R.color.digital_colons))

            mTime = Time()
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        private fun createTextPaint(defaultInteractiveColor: Int, typeface: Typeface = NORMAL_TYPEFACE): Paint {
            val paint = Paint()
            paint.color = defaultInteractiveColor
            paint.typeface = typeface
            paint.isAntiAlias = true
            return paint
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onVisibilityChanged: " + visible)
            }
            super.onVisibilityChanged(visible)

            if (visible) {
                mGoogleApiClient!!.connect()

                registerReceiver()

                // Update time zone in case it changed while we weren't visible.
                mTime?.clear(TimeZone.getDefault().id)
                mTime?.setToNow()
            } else {
                unregisterReceiver()

                if (mGoogleApiClient != null && mGoogleApiClient!!.isConnected) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this)
                    mGoogleApiClient!!.disconnect()
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@FWatchFaceService.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@FWatchFaceService.unregisterReceiver(mTimeZoneReceiver)
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onApplyWindowInsets: " + if (insets.isRound) "round" else "square")
            }
            super.onApplyWindowInsets(insets)

            // Load resources that have alternate values for round watches.
            val resources = this@FWatchFaceService.resources
            val isRound = insets.isRound
            mXOffset = resources.getDimension(if (isRound)
                R.dimen.digital_x_offset_round
            else
                R.dimen.digital_x_offset)
            textSize = resources.getDimension(if (isRound)
                R.dimen.digital_text_size_round
            else
                R.dimen.digital_text_size)

            mHourPaint?.textSize = textSize
            mMinutePaint?.textSize = textSize
            mSecondPaint?.textSize = textSize
            mColonPaint?.textSize = textSize

            mColonWidth = mColonPaint!!.measureText(COLON_STRING)
        }

        override fun onPropertiesChanged(properties: Bundle?) {
            super.onPropertiesChanged(properties)

            val burnInProtection = properties!!.getBoolean(WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
            mHourPaint?.typeface = if (burnInProtection) NORMAL_TYPEFACE else BOLD_TYPEFACE

            mLowBitAmbient = properties.getBoolean(WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: burn-in protection = " + burnInProtection
                        + ", low-bit ambient = " + mLowBitAmbient)
            }
        }

        override fun onTimeTick() {
            super.onTimeTick()
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onTimeTick: ambient = " + isInAmbientMode)
            }
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode)
            }
            adjustPaintColorToCurrentMode(mBackgroundPaint!!, mInteractiveBackgroundColor,
                    FWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND)
            adjustPaintColorToCurrentMode(mHourPaint!!, mInteractiveHourDigitsColor,
                    FWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_TEXT)
            adjustPaintColorToCurrentMode(mMinutePaint!!, mInteractiveMinuteDigitsColor,
                    FWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS)
            // Actually, the seconds are not rendered in the ambient mode, so we could pass just any
            // value as ambientColor here.
            adjustPaintColorToCurrentMode(mSecondPaint!!, mInteractiveSecondDigitsColor,
                    FWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS)

            if (mLowBitAmbient) {
                val antiAlias = !inAmbientMode
                mHourPaint?.isAntiAlias = antiAlias
                mMinutePaint?.isAntiAlias = antiAlias
                mSecondPaint?.isAntiAlias = antiAlias
                mAmPmPaint?.isAntiAlias = antiAlias
                mColonPaint?.isAntiAlias = antiAlias
            }
            invalidate()

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer()
        }

        private fun adjustPaintColorToCurrentMode(paint: Paint, interactiveColor: Int,
                                                  ambientColor: Int) {
            paint.color = if (isInAmbientMode) ambientColor else interactiveColor
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onInterruptionFilterChanged: " + interruptionFilter)
            }
            super.onInterruptionFilterChanged(interruptionFilter)

            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE
            // We only need to update once a minute in mute mode.
            setInteractiveUpdateRateMs(if (inMuteMode) MUTE_UPDATE_RATE_MS else NORMAL_UPDATE_RATE_MS)

            if (mMute != inMuteMode) {
                mMute = inMuteMode
                val alpha = if (inMuteMode) MUTE_ALPHA else NORMAL_ALPHA
                mHourPaint?.alpha = alpha
                mMinutePaint?.alpha = alpha
                mColonPaint?.alpha = alpha
                mAmPmPaint?.alpha = alpha
                invalidate()
            }
        }

        fun setInteractiveUpdateRateMs(updateRateMs: Long) {
            if (updateRateMs == mInteractiveUpdateRateMs) {
                return
            }
            mInteractiveUpdateRateMs = updateRateMs

            // Stop and restart the timer so the new update rate takes effect immediately.
            if (shouldTimerBeRunning()) {
                updateTimer()
            }
        }

        private fun updatePaintIfInteractive(paint: Paint?, interactiveColor: Int) {
            if (!isInAmbientMode && paint != null) {
                paint.color = interactiveColor
            }
        }

        private fun setInteractiveBackgroundColor(color: Int) {
            mInteractiveBackgroundColor = color
            updatePaintIfInteractive(mBackgroundPaint, color)
        }

        private fun setInteractiveHourDigitsColor(color: Int) {
            mInteractiveHourDigitsColor = color
            updatePaintIfInteractive(mHourPaint, color)
        }


        private fun convertTo12Hour(hour: Int): Int {
            val result = hour.rem(12)
            return if (result == 0) 12 else result
        }

        override fun onDraw(canvas: Canvas?, bounds: Rect?) {
            mTime?.setToNow()
            canvas!!.drawRect(0f, 0f, bounds!!.width().toFloat(), bounds.height().toFloat(), mBackgroundPaint)
            val x = mXOffset
            var y = mYOffset
            canvas.drawText(begin, x, y, mHourPaint)
            y += textSize
            var hour = convertTo12Hour(mTime!!.hour)
            val minute = mTime!!.minute
            Log.i(TAG, hour.toString() + " " + minute)
            if (minute in 0..60) {
                if(minute!= 15 || minute!=45) {
                    canvas.drawText(getMinutes(if(minute>30) 30-minute.rem(30) else minute), x, y, mHourPaint)
                    if (minute > 12)
                        y += textSize
                    canvas.drawText(getMinutesText(minute), x, y, mHourPaint)
                } else{
                    canvas.drawText("quarter", x, y, mHourPaint)
                }

                y += textSize
                var hourText = ""
                if (minute > 30) {
                    hour = (hour + 1).rem(12)
                    hourText += "to "
                } else {
                    hourText += "past "
                }
                hourText += getHours(hour)
                if (minute == 30 || minute == 0)
                    hourText += " o'clock"
                canvas.drawText(hourText, x, y, mHourPaint)
            }

        }

        private fun getMinutes(minutes: Int): String {
            return NumberWordConverter.convert(minutes)
        }

        private fun getMinutesText(minutes: Int): String {
            return if (minutes.rem(10) == 1) "minute" else "minutes"
        }

        private fun getHours(hours: Int): String {
            var time = ""
            when (hours) {
                0, 12 -> time += "twelve"
                1 -> time += "one"
                2 -> time += "two"
                3 -> time += "tree"
                4 -> time += "four"
                5 -> time += "five"
                6 -> time += "six"
                7 -> time += "seven"
                8 -> time += "eight"
                9 -> time += "nine"
                10 -> time += "ten"
                11 -> time += "eleven"
            }
            return time
        }


        /**
         * Starts the [.mUpdateTimeHandler] timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private fun updateTimer() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer")
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !isInAmbientMode
        }

        private fun updateConfigDataItemAndUiOnStartup() {
            fetchConfigDataMap(mGoogleApiClient!!, DataMapCallback())
        }

        inner class DataMapCallback : FetchConfigDataMapCallback {
            override fun onConfigDataMapFetched(config: DataMap) {
                setDefaultValuesForMissingConfigKeys(config!!)
                FWatchFaceUtil.putConfigDataItem(mGoogleApiClient!!, config!!)

                updateUiForConfigDataMap(config!!)
            }

        }

        private fun setDefaultValuesForMissingConfigKeys(config: DataMap) {
            addIntKeyIfMissing(config, FWatchFaceUtil.KEY_BACKGROUND_COLOR,
                    FWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND)
            addIntKeyIfMissing(config, FWatchFaceUtil.KEY_TEXT_COLOR,
                    FWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_TEXT)
        }

        private fun addIntKeyIfMissing(config: DataMap, key: String, color: Int) {
            if (!config.containsKey(key)) {
                config.putInt(key, color)
            }
        }

        override // DataApi.DataListener
        fun onDataChanged(dataEvents: DataEventBuffer) {
            try {
                for (dataEvent in dataEvents) {
                    if (dataEvent.type != DataEvent.TYPE_CHANGED) {
                        continue
                    }

                    val dataItem = dataEvent.dataItem
                    if (dataItem.uri.path != FWatchFaceUtil.PATH_WITH_FEATURE) {
                        continue
                    }

                    val dataMapItem = DataMapItem.fromDataItem(dataItem)
                    val config = dataMapItem.dataMap
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Config DataItem updated:" + config)
                    }
                    updateUiForConfigDataMap(config)
                }
            } finally {
                dataEvents.close()
            }
        }

        private fun updateUiForConfigDataMap(config: DataMap) {
            var uiUpdated = false
            for (configKey in config.keySet()) {
                if (!config.containsKey(configKey)) {
                    continue
                }
                val color = config.getInt(configKey)
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Found watch face config key: " + configKey + " -> "
                            + Integer.toHexString(color))
                }
                if (updateUiForKey(configKey, color)) {
                    uiUpdated = true
                }
            }
            if (uiUpdated) {
                invalidate()
            }
        }

        /**
         * Updates the color of a UI item according to the given `configKey`. Does nothing if
         * `configKey` isn't recognized.

         * @return whether UI has been updated
         */
        private fun updateUiForKey(configKey: String, color: Int): Boolean {
            if (configKey == FWatchFaceUtil.KEY_BACKGROUND_COLOR) {
                setInteractiveBackgroundColor(color)
            } else if (configKey == FWatchFaceUtil.KEY_TEXT_COLOR) {
                setInteractiveHourDigitsColor(color)
            } else {
                Log.w(TAG, "Ignoring unknown config key: " + configKey)
                return false
            }
            return true
        }

        override // GoogleApiClient.ConnectionCallbacks
        fun onConnected(connectionHint: Bundle?) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + connectionHint!!)
            }
            Wearable.DataApi.addListener(mGoogleApiClient, this@Engine)
            updateConfigDataItemAndUiOnStartup()
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
    }

    companion object {

        internal val COLON_STRING = ":"

        /** Alpha value for drawing time when in mute mode.  */
        internal val MUTE_ALPHA = 100

        /** Alpha value for drawing time when not in mute mode.  */
        internal val NORMAL_ALPHA = 255

        internal val MSG_UPDATE_TIME = 0
        private val TAG = "FWatchFaceService"

        private val BOLD_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        private val NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

        /**
         * Update rate in milliseconds for normal (not ambient and not mute) mode. We update twice
         * a second to blink the colons.
         */
        private val NORMAL_UPDATE_RATE_MS: Long = 1000

        /**
         * Update rate in milliseconds for mute mode. We update every minute, like in ambient mode.
         */
        private val MUTE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1)
    }
}

