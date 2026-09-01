package dev.duongvan.atvremote.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections

/**
 * Companion only reports app names, so artwork is resolved through Apple's
 * public iTunes lookup endpoint and cached on disk. Anything without a store
 * listing (built-in tvOS apps, sideloaded titles) falls back to a monogram.
 */
object ArtworkLoader {

    private const val CACHE_DIR = "appicons"
    private const val PREFS = "atv-artwork"
    private const val MAX_CACHE_BYTES = 8 * 1024 * 1024

    private val memory = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val missing: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val gate = Semaphore(3)

    suspend fun icon(context: Context, bundleId: String): Bitmap? {
        memory.get(bundleId)?.let { return it }
        if (missing.contains(bundleId)) return null
        return withContext(Dispatchers.IO) {
            gate.withPermit { load(context.applicationContext, bundleId) }
        }
    }

    private fun load(context: Context, bundleId: String): Bitmap? {
        memory.get(bundleId)?.let { return it }

        val file = File(File(context.cacheDir, CACHE_DIR).apply { mkdirs() }, fileName(bundleId))
        if (file.exists()) {
            decode(file.readBytes())?.let {
                memory.put(bundleId, it)
                return it
            }
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(bundleId, null)
        if (cached != null && cached.isEmpty()) {
            missing.add(bundleId)
            return null
        }

        val url = cached ?: lookupArtwork(bundleId).also {
            prefs.edit().putString(bundleId, it ?: "").apply()
        }
        if (url == null) {
            missing.add(bundleId)
            return null
        }

        val bytes = download(url) ?: return null
        val bitmap = decode(bytes) ?: return null
        runCatching { file.writeBytes(bytes) }
        memory.put(bundleId, bitmap)
        return bitmap
    }

    private fun fileName(bundleId: String): String =
        bundleId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".img"

    private fun decode(bytes: ByteArray): Bitmap? {
        // Store artwork is 512px; half of that is plenty for a 64dp tile.
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
    }

    private fun lookupArtwork(bundleId: String): String? {
        // Regional titles only exist in the local storefront, global ones in US.
        for (country in listOf("VN", "US")) {
            lookupArtwork(bundleId, country)?.let { return it }
        }
        return null
    }

    private fun lookupArtwork(bundleId: String, country: String): String? {
        val encoded = URLEncoder.encode(bundleId, "UTF-8")
        val body = fetch("https://itunes.apple.com/lookup?bundleId=$encoded&country=$country")
            ?: return null
        return runCatching {
            val results = JSONObject(String(body, Charsets.UTF_8)).optJSONArray("results")
            if (results == null || results.length() == 0) return null
            val entry = results.getJSONObject(0)
            entry.optString("artworkUrl512").ifEmpty { entry.optString("artworkUrl100") }
                .ifEmpty { null }
        }.getOrNull()
    }

    private fun download(url: String): ByteArray? = fetch(url)

    private fun fetch(url: String): ByteArray? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 8000
        connection.instanceFollowRedirects = true
        try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
