package app.sereno.weather

import android.app.Application
import app.sereno.weather.core.Container

class SerenoApplication : Application() {

    val container: Container by lazy { Container(this) }

    companion object {
        lateinit var instance: SerenoApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
