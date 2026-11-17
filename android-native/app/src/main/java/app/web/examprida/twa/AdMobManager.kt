package app.web.examprida.twa

import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

class AdMobManager(private val activity: Activity) {

    private var bannerView: AdView? = null
    private var interstitialAd: InterstitialAd? = null
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var currentNativeAd: NativeAd? = null

    @Volatile
    var isExamLocked: Boolean = false

    @Volatile
    var isExamPreparing: Boolean = false

    private fun canServeAds(context: Context): Boolean {
        val cfg = AdMobConfig.getDynamicSettings(context)
        return cfg.adsEnabled &&
            !isExamLocked &&
            !isExamPreparing &&
            !AdMobConfig.isExamInProgress(context) &&
            !AdMobConfig.isOwnerAdSafeMode(context)
    }

    fun hideAllAdsImmediately(bannerContainer: FrameLayout, nativeContainer: FrameLayout) {
        activity.runOnUiThread {
            bannerContainer.visibility = View.GONE
            nativeContainer.visibility = View.GONE
            bannerView?.pause()
        }
    }

    fun resetCachedAdsForSettingsChange() {
        activity.runOnUiThread {
            bannerView?.destroy()
            bannerView = null
            currentNativeAd?.destroy()
            currentNativeAd = null
            interstitialAd = null
            rewardedInterstitialAd = null
            rewardedAd = null
        }
    }

    fun preloadAll() {
        if (!canServeAds(activity)) return
        loadInterstitial()
        loadRewardedInterstitial()
        loadRewarded()
    }

    // 1. BANNER ADAPTIF
    fun loadAndToggleBanner(container: FrameLayout, show: Boolean) {
        activity.runOnUiThread {
            val cfg = AdMobConfig.getDynamicSettings(activity)
            if (!show || !cfg.bannerEnabled || !canServeAds(activity)) {
                container.visibility = View.GONE
                bannerView?.pause()
                return@runOnUiThread
            }

            if (bannerView == null) {
                val adView = AdView(activity).apply {
                    adUnitId = cfg.bannerUnitId
                    setAdSize(getAdaptiveBannerSize())
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            val latest = AdMobConfig.getDynamicSettings(activity)
                            if (latest.bannerEnabled && canServeAds(activity)) {
                                container.visibility = View.VISIBLE
                            } else {
                                container.visibility = View.GONE
                                pause()
                            }
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            container.visibility = View.GONE
                        }
                    }
                }
                container.removeAllViews()
                container.addView(adView)
                bannerView = adView
                adView.loadAd(AdRequest.Builder().build())
            } else {
                bannerView?.resume()
                container.visibility = View.VISIBLE
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getAdaptiveBannerSize(): AdSize {
        val display = activity.windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)
        val density = outMetrics.density
        val adWidth = (outMetrics.widthPixels / density).toInt().coerceAtLeast(320)
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    // 2. INTERSTISIAL (Hanya pasca-ujian)
    fun loadInterstitial() {
        val cfg = AdMobConfig.getDynamicSettings(activity)
        if (!cfg.adsEnabled || !cfg.interstitialEnabled || AdMobConfig.isOwnerAdSafeMode(activity) || interstitialAd != null) return
        InterstitialAd.load(
            activity,
            cfg.interstitialUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    fun showInterstitial(onClosed: () -> Unit) {
        activity.runOnUiThread {
            val cfg = AdMobConfig.getDynamicSettings(activity)
            val ad = interstitialAd
            if (!cfg.interstitialEnabled || !canServeAds(activity) || ad == null) {
                if (cfg.interstitialEnabled && !AdMobConfig.isOwnerAdSafeMode(activity)) loadInterstitial()
                onClosed()
                return@runOnUiThread
            }
            val app = activity.application as? ExambroApplication
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    app?.isShowingOtherFullscreenAd = true
                }

                override fun onAdDismissedFullScreenContent() {
                    app?.isShowingOtherFullscreenAd = false
                    interstitialAd = null
                    loadInterstitial()
                    onClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    app?.isShowingOtherFullscreenAd = false
                    interstitialAd = null
                    loadInterstitial()
                    onClosed()
                }
            }
            ad.show(activity)
        }
    }

    // 3. PENGANTARA BERBONUS (Rewarded Interstitial)
    fun loadRewardedInterstitial() {
        val cfg = AdMobConfig.getDynamicSettings(activity)
        if (!cfg.adsEnabled || !cfg.rewardedInterstitialEnabled || AdMobConfig.isOwnerAdSafeMode(activity) || rewardedInterstitialAd != null) return
        RewardedInterstitialAd.load(
            activity,
            cfg.rewardedInterstitialUnitId,
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedInterstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedInterstitialAd = null
                }
            }
        )
    }

    fun showRewardedInterstitial(onResult: (Boolean) -> Unit) {
        activity.runOnUiThread {
            val cfg = AdMobConfig.getDynamicSettings(activity)
            val ad = rewardedInterstitialAd
            if (!cfg.rewardedInterstitialEnabled || !canServeAds(activity) || ad == null) {
                if (canServeAds(activity)) loadRewardedInterstitial()
                onResult(AdMobConfig.isOwnerAdSafeMode(activity) || !cfg.adsEnabled || !cfg.rewardedInterstitialEnabled)
                return@runOnUiThread
            }
            val app = activity.application as? ExambroApplication
            var earned = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    app?.isShowingOtherFullscreenAd = true
                }

                override fun onAdDismissedFullScreenContent() {
                    app?.isShowingOtherFullscreenAd = false
                    rewardedInterstitialAd = null
                    loadRewardedInterstitial()
                    onResult(earned)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    app?.isShowingOtherFullscreenAd = false
                    rewardedInterstitialAd = null
                    loadRewardedInterstitial()
                    onResult(false)
                }
            }
            ad.show(activity) {
                earned = true
            }
        }
    }

    // 4. IKLAN REWARD (Latihan Soal AI)
    fun loadRewarded() {
        val cfg = AdMobConfig.getDynamicSettings(activity)
        if (!cfg.adsEnabled || !cfg.rewardedEnabled || AdMobConfig.isOwnerAdSafeMode(activity) || rewardedAd != null) return
        RewardedAd.load(
            activity,
            cfg.rewardedUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    fun showRewarded(onResult: (Boolean) -> Unit) {
        activity.runOnUiThread {
            val cfg = AdMobConfig.getDynamicSettings(activity)
            val ad = rewardedAd
            if (!cfg.rewardedEnabled || !canServeAds(activity) || ad == null) {
                if (canServeAds(activity)) loadRewarded()
                onResult(AdMobConfig.isOwnerAdSafeMode(activity) || !cfg.adsEnabled || !cfg.rewardedEnabled)
                return@runOnUiThread
            }
            val app = activity.application as? ExambroApplication
            var earned = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    app?.isShowingOtherFullscreenAd = true
                }

                override fun onAdDismissedFullScreenContent() {
                    app?.isShowingOtherFullscreenAd = false
                    rewardedAd = null
                    loadRewarded()
                    onResult(earned)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    app?.isShowingOtherFullscreenAd = false
                    rewardedAd = null
                    loadRewarded()
                    onResult(false)
                }
            }
            ad.show(activity) {
                earned = true
            }
        }
    }

    // 5. NATIVE ADVANCED
    fun toggleNativeAd(container: FrameLayout, show: Boolean) {
        activity.runOnUiThread {
            val cfg = AdMobConfig.getDynamicSettings(activity)
            if (!show || !cfg.nativeEnabled || !canServeAds(activity)) {
                container.visibility = View.GONE
                return@runOnUiThread
            }

            if (currentNativeAd != null) {
                bindNativeAd(container, currentNativeAd!!)
                container.visibility = View.VISIBLE
                return@runOnUiThread
            }

            val loader = AdLoader.Builder(activity, cfg.nativeUnitId)
                .forNativeAd { nativeAd ->
                    if (activity.isDestroyed || activity.isFinishing || !canServeAds(activity)) {
                        nativeAd.destroy()
                        container.visibility = View.GONE
                        return@forNativeAd
                    }
                    currentNativeAd?.destroy()
                    currentNativeAd = nativeAd
                    bindNativeAd(container, nativeAd)
                    container.visibility = View.VISIBLE
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        container.visibility = View.GONE
                    }
                })
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .build()
                )
                .build()

            loader.loadAd(AdRequest.Builder().build())
        }
    }

    private fun bindNativeAd(container: FrameLayout, nativeAd: NativeAd) {
        val adView = LayoutInflater.from(activity)
            .inflate(R.layout.layout_native_ad, container, false) as NativeAdView

        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        val bodyView = adView.findViewById<TextView>(R.id.ad_body)
        val ctaView = adView.findViewById<Button>(R.id.ad_call_to_action)
        val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
        val advertiserView = adView.findViewById<TextView>(R.id.ad_advertiser)
        val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
        val closeBtn = adView.findViewById<ImageButton>(R.id.btn_close_native_ad)

        adView.headlineView = headlineView
        adView.bodyView = bodyView
        adView.callToActionView = ctaView
        adView.iconView = iconView
        adView.advertiserView = advertiserView
        adView.mediaView = mediaView

        headlineView.text = nativeAd.headline
        bodyView.text = nativeAd.body ?: ""
        bodyView.visibility = if (nativeAd.body != null) View.VISIBLE else View.GONE

        ctaView.text = nativeAd.callToAction ?: "Buka"
        ctaView.visibility = if (nativeAd.callToAction != null) View.VISIBLE else View.GONE

        advertiserView.text = nativeAd.advertiser ?: "Sponsor"
        advertiserView.visibility = if (nativeAd.advertiser != null) View.VISIBLE else View.GONE

        val icon = nativeAd.icon
        if (icon != null) {
            iconView.setImageDrawable(icon.drawable)
            iconView.visibility = View.VISIBLE
        } else {
            iconView.visibility = View.GONE
        }

        if (nativeAd.mediaContent != null) {
            mediaView.mediaContent = nativeAd.mediaContent
            mediaView.visibility = View.VISIBLE
        } else {
            mediaView.visibility = View.GONE
        }

        closeBtn.setOnClickListener {
            container.visibility = View.GONE
        }

        adView.setNativeAd(nativeAd)
        container.removeAllViews()
        container.addView(adView)
    }

    fun destroy() {
        bannerView?.destroy()
        currentNativeAd?.destroy()
        bannerView = null
        currentNativeAd = null
        interstitialAd = null
        rewardedInterstitialAd = null
        rewardedAd = null
    }
}
