package com.tavi.v2ex

import android.app.Application
import com.tavi.v2ex.data.AppDb
import com.tavi.v2ex.data.LocalDao
import com.tavi.v2ex.data.Prefs
import com.tavi.v2ex.data.V2exApi

class App : Application() {
    lateinit var prefs: Prefs; private set
    lateinit var api: V2exApi; private set
    lateinit var dao: LocalDao; private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        api = V2exApi(cacheDir) { prefs.token }
        dao = AppDb.get(this).dao()
        instance = this
    }

    companion object {
        lateinit var instance: App; private set
    }
}
