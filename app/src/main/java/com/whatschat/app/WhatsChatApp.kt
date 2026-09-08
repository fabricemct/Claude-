package com.whatschat.app

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class WhatsChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase AI Logic (Gemini, for photo-menu translation) requires App Check —
        // this proves calls come from a genuine install of this app rather than a
        // scraped/leaked key being called from somewhere else.
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
