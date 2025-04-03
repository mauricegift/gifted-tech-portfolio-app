package co.median.android.plugins.lottie

import android.animation.Animator
import android.app.Activity
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.core.view.allViews
import co.median.median_core.AppConfig
import co.median.median_core.BaseBridgeModule
import co.median.median_core.GoNativeActivity
import co.median.median_core.GoNativeContext
import co.median.median_core.animations.AnimatedSplashCompletedListener
import co.median.median_core.animations.MedianProgressViewItem
import co.median.median_core.animations.ProgressListener
import co.median.median_core.dto.Loop
import co.median.median_core.dto.SpinnerConfig
import co.median.median_core.dto.SplashScreenConfig
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable

class LottiePlugin : BaseBridgeModule() {
    private lateinit var splashConfig: SplashScreenConfig
    private lateinit var spinnerConfig: SpinnerConfig
    private var listener: AnimatedSplashCompletedListener? = null

    override fun onApplicationCreate(context: GoNativeContext?) {
        super.onApplicationCreate(context)
        val appConfig = AppConfig.getInstance(context)
        this.splashConfig = appConfig.splashScreen ?: SplashScreenConfig()
        this.spinnerConfig = appConfig.spinner ?: SpinnerConfig()
    }

    override fun <T> animateSplashScreen(
        activity: T & Any,
        provider: SplashScreenViewProvider,
        listener: AnimatedSplashCompletedListener?
    ) where T : Activity?, T : GoNativeActivity? {
        this.listener = listener

        // inflate and configure the Lottie splash animation
        val inflater: LayoutInflater = activity.layoutInflater

        val lottieView = inflater.inflate(R.layout.splash_lottie_view, null) as FrameLayout
        val lottieAnimation = lottieView.findViewById<LottieAnimationView>(R.id.lottie_splash)

        // Prevents a crash when device animations are disabled in OS settings,
        // allowing Lottie animations to run regardless. https://github.com/airbnb/lottie-android/issues/1534
        lottieAnimation.setIgnoreDisabledSystemAnimations(true)

        // add Lottie animation to splash view then play
        lottieAnimation.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animator: Animator) {
                if (splashConfig.loop == Loop.INFINITE) {
                    listener?.onAnimationDone()
                }
            }

            override fun onAnimationEnd(animator: Animator) {
                if (splashConfig.loop == Loop.ONCE) {
                    listener?.onAnimationDone()
                } else {
                    lottieAnimation.repeatCount = LottieDrawable.INFINITE
                    lottieAnimation.playAnimation()
                }
            }

            override fun onAnimationCancel(animator: Animator) {}

            override fun onAnimationRepeat(animator: Animator) {}
        })

        val splashView =
            provider.view as FrameLayout

        // waits for Lottie to load before removing non-Lottie views
        lottieAnimation.addLottieOnCompositionLoadedListener {
            splashView.allViews.filter { it != lottieView }.forEach { splashView.removeView(it)}
        }

        splashView.addView(lottieView)
        lottieAnimation.playAnimation()
    }

    override fun <T> getProgressView(activity: T & Any): MedianProgressViewItem? where T : Activity?, T : GoNativeActivity? {
        if (!spinnerConfig.animated)
            return null

        val lottieProgress = activity.layoutInflater.inflate(
            R.layout.progress_lottie_view,
            null
        ) as LottieAnimationView

        val listener = object : ProgressListener {
            override fun onShow() {
                lottieProgress.playAnimation()
            }

            override fun onHide() {
                lottieProgress.pauseAnimation()
            }
        }

        return MedianProgressViewItem(
            lottieProgress,
            listener
        )
    }

    companion object {
        private val TAG = LottiePlugin::class.java.simpleName
    }
}