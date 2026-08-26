package com.vidsize.compressor.ads

import android.content.Context
import com.vidsize.compressor.VidsizeApplication

fun Context.suppressAppOpenOnReturn() {
    (applicationContext as? VidsizeApplication)
        ?.appOpenAdManager
        ?.suppressNextForeground()
}
