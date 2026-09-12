package com.whatschat.app.data.ai

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.whatschat.app.data.translate.AppLanguage

/**
 * Translates a photographed menu/sign/document via Gemini, through Firebase AI Logic — no
 * API key embedded in the app; access is gated by App Check (see [com.whatschat.app.WhatsChatApp])
 * instead. Replaces an earlier on-device OCR approach, which only reliably picked up a
 * handful of words on a real restaurant menu (decorative fonts, mixed columns, prices).
 */
object GeminiPhotoTranslator {
    // Cheapest Gemini model with vision support — plenty for reading a menu/sign.
    private const val MODEL_NAME = "gemini-2.5-flash-lite"

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(MODEL_NAME)
    }

    suspend fun translate(bitmap: Bitmap, targetLanguage: AppLanguage): String {
        val prompt = content {
            image(bitmap)
            text(
                "This photo shows a menu, sign, or document. Read all the text in it and " +
                    "translate it into ${targetLanguage.label}. Keep the original structure " +
                    "— one item per line, prices kept as-is. Only output the translated " +
                    "text, nothing else (no notes, no explanations)."
            )
        }
        val response = model.generateContent(prompt)
        return response.text?.trim().orEmpty().ifBlank {
            throw IllegalStateException("No text found in the photo — try getting closer or a clearer angle.")
        }
    }
}
