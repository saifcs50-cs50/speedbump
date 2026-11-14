package com.speedbump.core.ui.component

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.background
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App icons are Drawables owned by PackageManager and rasterising one is a
 * binder call plus a bitmap allocation. The picker scrolls through a few
 * hundred of them, so they are loaded off the main thread and cached.
 */
private object AppIconCache {
    private const val MAX_ENTRIES = 192
    private const val RASTER_PX = 128

    private val cache = LruCache<String, ImageBitmap>(MAX_ENTRIES)

    fun load(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return runCatching {
            context.packageManager
                .getApplicationIcon(packageName)
                .toBitmap(RASTER_PX, RASTER_PX)
                .asImageBitmap()
                .also { cache.put(packageName, it) }
        }.getOrNull()
    }
}

@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) { AppIconCache.load(context, packageName) }
    }

    val shape = RoundedCornerShape(size / 4)
    if (icon != null) {
        Image(
            bitmap = icon!!,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        // Placeholder keeps row heights stable while icons stream in, so the
        // list does not jump under the user's thumb.
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
    }
}
