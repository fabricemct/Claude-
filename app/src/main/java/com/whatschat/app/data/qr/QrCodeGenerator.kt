package com.whatschat.app.data.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** The scheme a "quick-connect" QR code carries — a plain uid isn't distinguishable from other QR content someone might scan. */
private const val SCHEME_PREFIX = "whatschat:contact:"

object QrCodeGenerator {

    fun contactPayload(uid: String): String = "$SCHEME_PREFIX$uid"

    /** Reads a uid back out of a scanned code's content, or null if it's not one of ours. */
    fun uidFromPayload(payload: String): String? =
        payload.removePrefix(SCHEME_PREFIX).takeIf { payload.startsWith(SCHEME_PREFIX) && it.isNotBlank() }

    /** Renders [uid] as a black-on-white QR code bitmap, [sizePx] square. */
    fun generate(uid: String, sizePx: Int = 512): Bitmap {
        val matrix = QRCodeWriter().encode(contactPayload(uid), BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
