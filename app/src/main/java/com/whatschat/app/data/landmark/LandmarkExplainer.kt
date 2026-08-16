package com.whatschat.app.data.landmark

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.whatschat.app.BuildConfig
import com.whatschat.app.data.translate.AppLanguage

/**
 * Identifies and describes whatever place, building, or landmark is in a
 * photo, using Gemini's multimodal (vision) model. Unlike every other
 * "smart" feature in this app, this one genuinely needs a cloud AI call —
 * ML Kit's on-device models only give generic scene/object labels (e.g.
 * "tower", "building"), not a specific place's name, location, or history.
 * Requires a Gemini API key (see the "Landmark photo lookup" section of the
 * README for how to get one and where it's configured).
 */
class LandmarkExplainer {

    private val model by lazy {
        GenerativeModel(modelName = "gemini-2.5-flash", apiKey = BuildConfig.GEMINI_API_KEY)
    }

    suspend fun explain(photo: Bitmap, language: AppLanguage): String {
        check(BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            "No Gemini API key configured. Add GEMINI_API_KEY to local.properties — see the README."
        }

        val prompt = """
            Look at this photo of a place, building, or landmark. Reply entirely in ${language.label}.
            Structure the reply as:
            - What it is (its name)
            - Where it is (city/region, country)
            - A short description (2-4 sentences: history, why it's notable)
            If you don't recognize it with reasonable confidence, say so honestly instead of guessing.
            No preamble, just the answer.
        """.trimIndent()

        val response = model.generateContent(content { image(photo); text(prompt) })
        return response.text?.takeIf { it.isNotBlank() }
            ?: "No description was returned — try a clearer or closer photo."
    }
}
