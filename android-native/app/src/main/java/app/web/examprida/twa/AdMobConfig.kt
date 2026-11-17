package app.web.examprida.twa

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

object AdMobConfig {
    const val APP_ID = "ca-app-pub-3979615977174432~1153915437"

    // Default 6 Unit Iklan AdMob Resmi
    const val DEFAULT_BANNER_UNIT_ID = "ca-app-pub-3979615977174432/1997378769"
    const val DEFAULT_INTERSTITIAL_UNIT_ID = "ca-app-pub-3979615977174432/1125995275"
    const val DEFAULT_REWARDED_INTERSTITIAL_UNIT_ID = "ca-app-pub-3979615977174432/2596144566"
    const val DEFAULT_REWARDED_UNIT_ID = "ca-app-pub-3979615977174432/1466895725"
    const val DEFAULT_NATIVE_ADVANCED_UNIT_ID = "ca-app-pub-3979615977174432/4572230359"
    const val DEFAULT_APP_OPEN_UNIT_ID = "ca-app-pub-3979615977174432/9058270279"

    // ID Perangkat Pengujian Iklan (Google Advertising ID)
    const val DEFAULT_TEST_DEVICE_GAID = "d9fb552c-cf63-4133-a677-8130f7262b14"

    private const val PREFS_NAME = "prida_exambro_prefs"
    private const val KEY_OWNER_AD_SAFE = "owner_ad_safe_mode"
    private const val KEY_EXAM_IN_PROGRESS = "exam_in_progress"
    private const val KEY_EXAM_IN_PROGRESS_TS = "exam_in_progress_ts"
    private const val KEY_DYNAMIC_ADMOB_JSON = "dynamic_admob_json"

    data class DynamicAdSettings(
        val adsEnabled: Boolean = true,
        val bannerEnabled: Boolean = true,
        val interstitialEnabled: Boolean = true,
        val rewardedInterstitialEnabled: Boolean = true,
        val rewardedEnabled: Boolean = true,
        val nativeEnabled: Boolean = true,
        val appOpenEnabled: Boolean = true,
        val bannerUnitId: String = DEFAULT_BANNER_UNIT_ID,
        val interstitialUnitId: String = DEFAULT_INTERSTITIAL_UNIT_ID,
        val rewardedInterstitialUnitId: String = DEFAULT_REWARDED_INTERSTITIAL_UNIT_ID,
        val rewardedUnitId: String = DEFAULT_REWARDED_UNIT_ID,
        val nativeUnitId: String = DEFAULT_NATIVE_ADVANCED_UNIT_ID,
        val appOpenUnitId: String = DEFAULT_APP_OPEN_UNIT_ID,
        val testDeviceId: String = DEFAULT_TEST_DEVICE_GAID
    )

    fun saveDynamicSettings(context: Context, jsonStr: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DYNAMIC_ADMOB_JSON, jsonStr)
            .apply()
    }

    fun getDynamicSettings(context: Context): DynamicAdSettings {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DYNAMIC_ADMOB_JSON, null) ?: return DynamicAdSettings()
        return try {
            val obj = JSONObject(raw)
            DynamicAdSettings(
                adsEnabled = obj.optBoolean("adsEnabled", true),
                bannerEnabled = obj.optBoolean("bannerEnabled", true),
                interstitialEnabled = obj.optBoolean("interstitialEnabled", true),
                rewardedInterstitialEnabled = obj.optBoolean("rewardedInterstitialEnabled", true),
                rewardedEnabled = obj.optBoolean("rewardedEnabled", true),
                nativeEnabled = obj.optBoolean("nativeEnabled", true),
                appOpenEnabled = obj.optBoolean("appOpenEnabled", true),
                bannerUnitId = obj.optString("bannerUnitId", DEFAULT_BANNER_UNIT_ID).ifBlank { DEFAULT_BANNER_UNIT_ID },
                interstitialUnitId = obj.optString("interstitialUnitId", DEFAULT_INTERSTITIAL_UNIT_ID).ifBlank { DEFAULT_INTERSTITIAL_UNIT_ID },
                rewardedInterstitialUnitId = obj.optString("rewardedInterstitialUnitId", DEFAULT_REWARDED_INTERSTITIAL_UNIT_ID).ifBlank { DEFAULT_REWARDED_INTERSTITIAL_UNIT_ID },
                rewardedUnitId = obj.optString("rewardedUnitId", DEFAULT_REWARDED_UNIT_ID).ifBlank { DEFAULT_REWARDED_UNIT_ID },
                nativeUnitId = obj.optString("nativeUnitId", DEFAULT_NATIVE_ADVANCED_UNIT_ID).ifBlank { DEFAULT_NATIVE_ADVANCED_UNIT_ID },
                appOpenUnitId = obj.optString("appOpenUnitId", DEFAULT_APP_OPEN_UNIT_ID).ifBlank { DEFAULT_APP_OPEN_UNIT_ID },
                testDeviceId = obj.optString("testDeviceId", DEFAULT_TEST_DEVICE_GAID).ifBlank { DEFAULT_TEST_DEVICE_GAID }
            )
        } catch (_: Exception) {
            DynamicAdSettings()
        }
    }

    fun getTestDeviceHashedId(gaid: String = DEFAULT_TEST_DEVICE_GAID): String {
        return md5Uppercase(gaid)
    }

    fun isOwnerAdSafeMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OWNER_AD_SAFE, false)
    }

    fun setOwnerAdSafeMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_OWNER_AD_SAFE, enabled)
            .apply()
    }

    fun setExamInProgress(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EXAM_IN_PROGRESS, active)
            .putLong(KEY_EXAM_IN_PROGRESS_TS, if (active) System.currentTimeMillis() else 0L)
            .apply()
    }

    fun isExamInProgress(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val active = prefs.getBoolean(KEY_EXAM_IN_PROGRESS, false)
        if (!active) return false
        val ts = prefs.getLong(KEY_EXAM_IN_PROGRESS_TS, 0L)
        return (System.currentTimeMillis() - ts) < 6 * 60 * 60 * 1000L
    }

    private fun md5Uppercase(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val bytes = digest.digest(input.toByteArray())
            bytes.joinToString("") { "%02X".format(it) }
        } catch (_: Exception) {
            input
        }
    }
}
