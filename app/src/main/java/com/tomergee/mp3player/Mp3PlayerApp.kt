package com.tomergee.mp3player

import android.app.Application

class Mp3PlayerApp : Application() {
    val container by lazy { AppContainer(this) }
}
