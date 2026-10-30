package com.senda.app.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.IconContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    shape: Shape = CircleShape
) {
    val context = LocalContext.current
    val image by produceState(
        initialValue = AppIconCache.get(packageName),
        key1 = packageName
    ) {
        if (value == null) {
            value = AppIconCache.load(context.applicationContext, packageName)
        }
    }

    val icon = image
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(shape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(IconContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = packageName.substringAfterLast('.').take(1).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = AccentSage
            )
        }
    }
}

/**
 * Cache compartilhado entre todas as listas. A extração do ícone é feita em
 * Dispatchers.IO para não decodificar bitmaps na thread de composição.
 */
private object AppIconCache {

    private const val ICON_SIZE_PX = 96
    private const val MAX_ENTRIES = 128

    private val cache = LruCache<String, ImageBitmap>(MAX_ENTRIES)

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    suspend fun load(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }

        return withContext(Dispatchers.IO) {
            cache.get(packageName) ?: runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(width = ICON_SIZE_PX, height = ICON_SIZE_PX)
                    .asImageBitmap()
            }.getOrNull()?.also { icon -> cache.put(packageName, icon) }
        }
    }
}
