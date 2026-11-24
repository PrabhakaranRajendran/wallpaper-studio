package com.prabhu.wallpaperstudio

import android.app.Application
import com.prabhu.wallpaperstudio.data.Store

class StudioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
