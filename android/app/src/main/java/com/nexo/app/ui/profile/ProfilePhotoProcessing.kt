package com.nexo.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Downscales [uri]'s image so its longer side is at most [maxDimension]
 * and JPEG-compresses it at [quality], returning the result as a Base64
 * string ready for `BackendRepository.updateProfilePicture` — mirrors
 * iOS's `ProfileView.uploadProfilePicture` (downscale-then-compress, in
 * that order, since compression quality alone can't reliably keep a
 * full-resolution photo-library image small).
 *
 * Not unit-testable in a plain JVM test — `android.graphics.Bitmap` has
 * no JVM implementation without Robolectric, which this project doesn't
 * use (per `CLAUDE.md`'s testing notes). Kept as a small, isolated
 * function so at least the call site (`ProfileScreen`'s photo picker
 * callback) stays simple; covered by manual verification instead.
 */
fun compressProfilePhoto(context: Context, uri: Uri, maxDimension: Int = 300, quality: Int = 40): String? {
    val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
    val resized = resizeToMaxDimension(original, maxDimension)
    val output = ByteArrayOutputStream()
    resized.compress(Bitmap.CompressFormat.JPEG, quality, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

private fun resizeToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val longerSide = max(bitmap.width, bitmap.height)
    if (longerSide <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / longerSide
    val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}
