package app.sereno.weather

import android.app.Application
import app.sereno.weather.core.Container
import app.sereno.weather.notify.Notifier
import app.sereno.weather.work.WeatherUpdateWorker

class SerenoApplication : Application() {

    val container: Container by lazy { Container(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Channels must exist before anything tries to post to them, and
        // creating them is cheap and idempotent.
        Notifier(this).ensureChannels()
        WeatherUpdateWorker.schedule(this)
    }

    companion object {
        lateinit var instance: SerenoApplication
            private set
    }
}
