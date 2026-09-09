package com.example

import android.app.Application
import com.example.data.SecurePreferences

class RouterPeerApp : Application() {

    val securePreferences: SecurePreferences by lazy {
        SecurePreferences(this)
    }
}
