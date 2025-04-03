package co.median.android.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import co.median.android.R
import co.median.median_core.animations.MedianProgressViewItem
import com.google.android.material.progressindicator.CircularProgressIndicator

class MedianProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private var progressView: MedianProgressViewItem? = null

    fun setProgressView(progressViewItem: MedianProgressViewItem) {
        progressView = progressViewItem
        addView(progressViewItem.view)
    }

    private val fadeOutDuration = 60L

    fun show() {
        visibility = View.VISIBLE
        alpha = 1.0f
        progressView?.listener?.onShow()
    }

    fun hide() {
        animate()
            .alpha(0.0f)
            .setDuration(fadeOutDuration)
            .withEndAction {
                visibility = View.INVISIBLE
                progressView?.listener?.onHide()
            }
    }

    fun hideImmediately() {
        alpha = 1.0f
        visibility = View.INVISIBLE
        progressView?.listener?.onHide()
    }

    fun setupDefaultProgress() {
        addView(
            CircularProgressIndicator(context).apply {
                isIndeterminate = true
                setIndicatorColor(ContextCompat.getColor(context, R.color.progress_indicator))
            }
        )
    }
}