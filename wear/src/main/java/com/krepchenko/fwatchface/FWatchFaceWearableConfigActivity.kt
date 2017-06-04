package com.krepchenko.fwatchface

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.wearable.view.BoxInsetLayout
import android.support.wearable.view.CircledImageView
import android.support.wearable.view.WearableListView
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Wearable
import com.krepchenko.fwatchface.utils.FWatchFaceUtil

/**
 * The watch-side config activity for [FWatchFaceService], which allows for setting the
 * background color.
 */
class FWatchFaceWearableConfigActivity : Activity(), WearableListView.ClickListener, WearableListView.OnScrollListener {

    private var mGoogleApiClient: GoogleApiClient? = null
    private var mHeader: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_digital_config)

        mHeader = findViewById(R.id.header) as TextView
        val listView = findViewById(R.id.color_picker) as WearableListView
        val content = findViewById(R.id.content) as BoxInsetLayout
        // BoxInsetLayout adds padding by default on round devices. Add some on square devices.
        content.setOnApplyWindowInsetsListener { v, insets ->
            if (!insets.isRound) {
                v.setPaddingRelative(
                        resources.getDimensionPixelSize(R.dimen.content_padding_start).toInt(),
                        v.paddingTop,
                        v.paddingEnd,
                        v.paddingBottom)
            }
            v.onApplyWindowInsets(insets)
        }

        listView.setHasFixedSize(true)
        listView.setClickListener(this)
        listView.addOnScrollListener(this)

        val colors = resources.getStringArray(R.array.color_array)
        listView.adapter = ColorListAdapter(colors)

        mGoogleApiClient = GoogleApiClient.Builder(this)
                .addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {
                    override fun onConnected(connectionHint: Bundle?) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "onConnected: " + connectionHint!!)
                        }
                    }

                    override fun onConnectionSuspended(cause: Int) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "onConnectionSuspended: " + cause)
                        }
                    }
                })
                .addOnConnectionFailedListener { result ->
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "onConnectionFailed: " + result)
                    }
                }
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

    override // WearableListView.ClickListener
    fun onClick(viewHolder: WearableListView.ViewHolder) {
        val colorItemViewHolder = viewHolder as ColorItemViewHolder
        updateConfigDataItem(colorItemViewHolder.mColorItem.color)
        finish()
    }

    override // WearableListView.ClickListener
    fun onTopEmptyRegionClick() {
    }

    override // WearableListView.OnScrollListener
    fun onScroll(scroll: Int) {
    }

    override // WearableListView.OnScrollListener
    fun onAbsoluteScrollChange(scroll: Int) {
        val newTranslation = Math.min(-scroll, 0).toFloat()
        mHeader!!.translationY = newTranslation
    }

    override // WearableListView.OnScrollListener
    fun onScrollStateChanged(scrollState: Int) {
    }

    override // WearableListView.OnScrollListener
    fun onCentralPositionChanged(centralPosition: Int) {
    }

    private fun updateConfigDataItem(backgroundColor: Int) {
        val configKeysToOverwrite = DataMap()
        configKeysToOverwrite.putInt(FWatchFaceUtil.KEY_BACKGROUND_COLOR,
                backgroundColor)
        FWatchFaceUtil.overwriteKeysInConfigDataMap(mGoogleApiClient!!, configKeysToOverwrite)
    }

    private inner class ColorListAdapter(private val mColors: Array<String>) : WearableListView.Adapter() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorItemViewHolder {
            return ColorItemViewHolder(ColorItem(parent.context))
        }

        override fun onBindViewHolder(holder: WearableListView.ViewHolder, position: Int) {
            val colorItemViewHolder = holder as ColorItemViewHolder
            val colorName = mColors[position]
            colorItemViewHolder.mColorItem.setColor(colorName)

            val layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            val colorPickerItemMargin = resources
                    .getDimension(R.dimen.digital_config_color_picker_item_margin).toInt()
            // Add margins to first and last item to make it possible for user to tap on them.
            if (position == 0) {
                layoutParams.setMargins(0, colorPickerItemMargin, 0, 0)
            } else if (position == mColors.size - 1) {
                layoutParams.setMargins(0, 0, 0, colorPickerItemMargin)
            } else {
                layoutParams.setMargins(0, 0, 0, 0)
            }
            colorItemViewHolder.itemView.layoutParams = layoutParams
        }

        override fun getItemCount(): Int {
            return mColors.size
        }
    }

    /** The layout of a color item including image and label.  */
    class ColorItem(context: Context) : LinearLayout(context), WearableListView.OnCenterProximityListener {

        private val mLabel: TextView
        private val mColor: CircledImageView

        private val mExpandCircleRadius: Float
        private val mShrinkCircleRadius: Float

        private val mExpandCircleAnimator: ObjectAnimator
        private val mExpandLabelAnimator: ObjectAnimator
        private val mExpandAnimator: AnimatorSet

        private val mShrinkCircleAnimator: ObjectAnimator
        private val mShrinkLabelAnimator: ObjectAnimator
        private val mShrinkAnimator: AnimatorSet

        init {
            View.inflate(context, R.layout.color_picker_item, this)

            mLabel = findViewById(R.id.label) as TextView
            mColor = findViewById(R.id.color) as CircledImageView

            mExpandCircleRadius = mColor.circleRadius
            mShrinkCircleRadius = mExpandCircleRadius * SHRINK_CIRCLE_RATIO

            mShrinkCircleAnimator = ObjectAnimator.ofFloat(mColor, "circleRadius",
                    mExpandCircleRadius, mShrinkCircleRadius)
            mShrinkLabelAnimator = ObjectAnimator.ofFloat(mLabel, "alpha",
                    EXPAND_LABEL_ALPHA, SHRINK_LABEL_ALPHA)
            mShrinkAnimator = AnimatorSet().setDuration(ANIMATION_DURATION_MS.toLong())
            mShrinkAnimator.playTogether(mShrinkCircleAnimator, mShrinkLabelAnimator)

            mExpandCircleAnimator = ObjectAnimator.ofFloat(mColor, "circleRadius",
                    mShrinkCircleRadius, mExpandCircleRadius)
            mExpandLabelAnimator = ObjectAnimator.ofFloat(mLabel, "alpha",
                    SHRINK_LABEL_ALPHA, EXPAND_LABEL_ALPHA)
            mExpandAnimator = AnimatorSet().setDuration(ANIMATION_DURATION_MS.toLong())
            mExpandAnimator.playTogether(mExpandCircleAnimator, mExpandLabelAnimator)
        }

        override fun onCenterPosition(animate: Boolean) {
            if (animate) {
                mShrinkAnimator.cancel()
                if (!mExpandAnimator.isRunning) {
                    mExpandCircleAnimator.setFloatValues(mColor.circleRadius, mExpandCircleRadius)
                    mExpandLabelAnimator.setFloatValues(mLabel.alpha, EXPAND_LABEL_ALPHA)
                    mExpandAnimator.start()
                }
            } else {
                mExpandAnimator.cancel()
                mColor.circleRadius = mExpandCircleRadius
                mLabel.alpha = EXPAND_LABEL_ALPHA
            }
        }

        override fun onNonCenterPosition(animate: Boolean) {
            if (animate) {
                mExpandAnimator.cancel()
                if (!mShrinkAnimator.isRunning) {
                    mShrinkCircleAnimator.setFloatValues(mColor.circleRadius, mShrinkCircleRadius)
                    mShrinkLabelAnimator.setFloatValues(mLabel.alpha, SHRINK_LABEL_ALPHA)
                    mShrinkAnimator.start()
                }
            } else {
                mShrinkAnimator.cancel()
                mColor.circleRadius = mShrinkCircleRadius
                mLabel.alpha = SHRINK_LABEL_ALPHA
            }
        }

        fun setColor(colorName: String) {
            mLabel.text = colorName
            mColor.setCircleColor(Color.parseColor(colorName))
        }

        val color: Int
            get() = mColor.defaultCircleColor

        companion object {
            /** The duration of the expand/shrink animation.  */
            private val ANIMATION_DURATION_MS = 150
            /** The ratio for the size of a circle in shrink state.  */
            private val SHRINK_CIRCLE_RATIO = .75f

            private val SHRINK_LABEL_ALPHA = .5f
            private val EXPAND_LABEL_ALPHA = 1f
        }
    }

    inner class ColorItemViewHolder(val mColorItem: ColorItem) : WearableListView.ViewHolder(mColorItem)

    companion object {
        private val TAG = "FWatchFaceConfig"
    }
}
