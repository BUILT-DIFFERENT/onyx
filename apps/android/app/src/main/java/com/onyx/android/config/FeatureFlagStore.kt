package com.onyx.android.config

import android.content.Context
import android.content.SharedPreferences

class FeatureFlagStore private constructor(
    context: Context,
) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun get(flag: FeatureFlag): Boolean = preferences.getBoolean(flag.key, flag.defaultValue)

    fun set(
        flag: FeatureFlag,
        value: Boolean,
    ) {
        preferences.edit().putBoolean(flag.key, value).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "onyx_feature_flags"

        @Volatile
        private var instance: FeatureFlagStore? = null

        fun getInstance(context: Context): FeatureFlagStore =
            instance
                ?: synchronized(this) {
                    instance ?: FeatureFlagStore(context).also { instance = it }
                }
    }
}
