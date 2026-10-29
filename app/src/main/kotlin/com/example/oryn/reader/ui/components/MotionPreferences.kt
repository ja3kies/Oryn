package com.example.oryn.reader.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings

/** Uses Android's animator scale so Compose transitions follow the system's reduced-motion setting. */
internal fun systemAnimationsEnabled(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) > 0f
        }.getOrDefault(true)
    }
