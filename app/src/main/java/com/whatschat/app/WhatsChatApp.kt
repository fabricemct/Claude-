package com.whatschat.app

import android.app.Application
import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class WhatsChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase AI Logic (Gemini, for photo-menu translation) requires App Check —
        // this proves calls come from a genuine install of this app rather than a
        // scraped/leaked key being called from somewhere else.
        //
        // Play Integrity kept failing ("App attestation failed") on debug builds shared
        // by sideloading — it's really meant for apps Google Play has some record of
        // (published, or at least in Play Console), which a plain debug APK isn't. Debug
        // builds use the debug provider instead: on first launch it prints a token to
        // Logcat (search "DebugAppCheckProvider") that must be added once, per install,
        // under Firebase Console → App Check → this app → "Manage debug tokens".
        if (BuildConfig.DEBUG) {
            Log.i(
                "WhatsChatApp",
                "App Check: using the DEBUG provider. Look for its token below and add it " +
                    "under Firebase Console -> App Check -> com.whatschat.app -> Manage debug tokens."
            )
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        } else {
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }
    }
}
