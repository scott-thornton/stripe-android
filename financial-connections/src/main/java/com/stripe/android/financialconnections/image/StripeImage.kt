package com.stripe.android.financialconnections.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp.Companion.Infinity
import androidx.compose.ui.unit.IntSize.Companion.Zero
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.URL

@Composable
internal fun StripeImage(
    url: String,
    @DrawableRes placeholderResId: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    BoxWithConstraints(modifier) {
        val context = LocalContext.current
        val (width, height) = calculateBoxSize()
        val imageBitmapState = remember { mutableStateOf(ImageBitmap(width, height)) }
        var loadImageJob: Job?
        val scope = rememberCoroutineScope()

        DisposableEffect(url) {
            var currentStream: InputStream? = null
            loadImageJob = scope.launch(Dispatchers.Default) {
                try {
                    imageBitmapState.value = decodeSampledBitmap(width, height) { options ->
                        BitmapFactory.decodeStream(
                            URL(url).openStream().also { stream -> currentStream = stream },
                            null,
                            options
                        )
                    }.asImageBitmap()
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    // upon any exception loading the
                    e.printStackTrace()
                    AppCompatResources
                        .getDrawable(context, placeholderResId)
                        ?.toBitmap()
                        ?.asImageBitmap()
                        ?.let { imageBitmapState.value = it }
                }
            }
            onDispose {
                imageBitmapState.value = ImageBitmap(width, height)
                currentStream?.close()
                loadImageJob?.cancel()
            }
        }
        Image(
            modifier = modifier,
            contentDescription = contentDescription,
            bitmap = imageBitmapState.value
        )
    }
}

private fun BoxWithConstraintsScope.calculateBoxSize(): Pair<Int, Int> {
    var width =
        if (constraints.maxWidth > Zero.width &&
            constraints.maxWidth < Infinity.value.toInt()
        ) {
            constraints.maxWidth
        } else {
            -1
        }

    var height =
        if (constraints.maxHeight > Zero.height &&
            constraints.maxHeight < Infinity.value.toInt()
        ) {
            constraints.maxHeight
        } else {
            -1
        }

    // if height xor width not able to be determined, make image a square of the determined dimension
    if (width == -1) width = height
    if (height == -1) height = width
    return Pair(width, height)
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    // Raw height and width of image
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {

        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

private fun decodeSampledBitmap(
    reqWidth: Int,
    reqHeight: Int,
    bitmapFactoryDecoderFunction: (BitmapFactory.Options) -> Bitmap?
): Bitmap = BitmapFactory.Options().run {
    // First decode with inJustDecodeBounds=true to check dimensions
    inJustDecodeBounds = true
    bitmapFactoryDecoderFunction(this)
    // Calculate inSampleSize
    inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)
    // Decode bitmap with inSampleSize set
    inJustDecodeBounds = false
    requireNotNull(bitmapFactoryDecoderFunction(this))
}

// TODO@carlosmuvi figure if caching brings benefits to our use case.
// private val imageMemoryCache = object : LruCache<String, Bitmap>(
//    // Use 1/8th of the available memory for this memory cache.
//    (Runtime.getRuntime().maxMemory() / 1024).toInt() / 8
// ) {
//    override fun sizeOf(key: String, bitmap: Bitmap): Int {
//        return bitmap.byteCount / 1024
//    }
// }
