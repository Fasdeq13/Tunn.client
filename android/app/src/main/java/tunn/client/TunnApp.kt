package tunn.client

import android.app.Application
import android.content.Context

class TunnApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Layer.apply(this)
    }

    override fun onCreate() {
        super.onCreate()
        Boot.start(this)
    }

    companion object {
        lateinit var state: AppState
        lateinit var appContext: android.content.Context
    }
}
