package app.web.examprida.twa

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

class ExambroApplication : Application(),
    Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTime: Long = 0
    private var currentActivity: Activity? = null

    @Volatile
    var isExamActiveGlobally: Boolean = false

    @Volatile
    var isExamPreparingGlobally: Boolean = false

    @Volatile
    var isShowingOtherFullscreenAd: Boolean = false

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        val cfg = AdMobConfig.getDynamicSettings(this)
        val testDevices = listOf(
            AdRequest.DEVICE_ID_EMULATOR,
            cfg.testDeviceId,
            AdMobConfig.getTestDeviceHashedId(cfg.testDeviceId)
        )
        val requestConfig = RequestConfiguration.Builder()
            .setTestDeviceIds(testDevices)
            .build()
        MobileAds.setRequestConfiguration(requestConfig)

        MobileAds.initialize(this) {
            if (!AdMobConfig.isExamInProgress(this)) {
                loadAppOpenAd()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        val activity = currentActivity ?: return
        if (activity is MainActivity && canShowAppOpenAd()) {
            showAppOpenAdIfAvailable(activity)
        }
    }

    private fun canShowAppOpenAd(): Boolean {
        val cfg = AdMobConfig.getDynamicSettings(this)
        return cfg.adsEnabled &&
            cfg.appOpenEnabled &&
            !isExamActiveGlobally &&
            !isExamPreparingGlobally &&
            !isShowingOtherFullscreenAd &&
            !AdMobConfig.isExamInProgress(this) &&
            !AdMobConfig.isOwnerAdSafeMode(this)
    }

    private fun isAdAvailable(): Boolean {
        if (appOpenAd == null) return false
        val elapsedHours = (Date().time - loadTime) / 3_600_000L
        return elapsedHours < 4
    }

    fun loadAppOpenAd() {
        if (isLoadingAd || isAdAvailable() || !canShowAppOpenAd()) return
        val cfg = AdMobConfig.getDynamicSettings(this)
        isLoadingAd = true
        AppOpenAd.load(
            this,
            cfg.appOpenUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAd = false
                }
            }
        )
    }

    private fun showAppOpenAdIfAvailable(activity: Activity) {
        if (isShowingAd || !canShowAppOpenAd()) return
        if (!isAdAvailable()) {
            loadAppOpenAd()
            return
        }
        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                loadAppOpenAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null
                isShowingAd = false
                loadAppOpenAd()
            }

            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
            }
        }
        isShowingAd = true
        appOpenAd?.show(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        if (!isShowingAd) currentActivity = activity
    }
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) currentActivity = null
    }
}
