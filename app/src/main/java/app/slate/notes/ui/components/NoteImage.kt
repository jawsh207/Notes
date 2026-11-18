package app.slate.notes.ui.components

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.slate.notes.data.NoteStore
import app.slate.notes.data.resolveImagePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decoded pictures, kept in memory so cards don't decode them again as they scroll back into
 * view. Sized by bytes, a sixth of what the app may use.
 */
private object Pictures {
    val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun load(store: NoteStore, path: String, maxSide: Int): Bitmap? {
        val key = "$path@$maxSide"
        cache.get(key)?.let { return it }
        val id = store.findPath(path) ?: return null
        val source = ImageDecoder.createSource(store.resolver, store.documentUri(id))
        // ImageDecoder also turns photos the right way up from their camera orientation tag.
        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val side = maxOf(info.size.width, info.size.height)
                if (side > maxSide) decoder.setTargetSampleSize(Integer.highestOneBit(side / maxSide).coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull() ?: return null
        cache.put(key, bitmap)
        return bitmap
    }
}

/**
 * A picture from a note, written as [path] in a note in the folder [noteFolder]. Drawn at its
 * own shape across the width (or cropped to [aspect] when given), with a plain placeholder while
 * it loads. Pictures that can't be found or read are left out.
 */
@Composable
fun NoteImage(
    store: NoteStore?,
    noteFolder: String,
    path: String,
    modifier: Modifier = Modifier,
    maxSide: Int = 1080,
    aspect: Float? = null,
) {
    val resolved = resolveImagePath(noteFolder, path)
    val bitmap by produceState<Bitmap?>(null, store, resolved, maxSide) {
        value = if (store == null || resolved == null) null else withContext(Dispatchers.IO) { Pictures.load(store, resolved, maxSide) }
    }
    val image = bitmap
    if (image == null) {
        if (aspect != null) Box(modifier.fillMaxWidth().aspectRatio(aspect).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        return
    }
    Image(
        bitmap = image.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxWidth().aspectRatio(aspect ?: (image.width.toFloat() / image.height.coerceAtLeast(1))),
    )
}
