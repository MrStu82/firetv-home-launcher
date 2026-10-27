package com.mrstu82.tvhome

import android.content.Context
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Bing's latest 8 daily UHD photographs: list refetched daily, files cached on disk, rotated every ~3h. */
object Bing {
    const val ROTATE_MS = 3 * 3_600_000L
    private const val LIST = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-GB"
    private val FALLBACK = Wall(null, "Sea of cloud below the peaks")
    @Volatile private var busy = false

    /** The cached Bing photo for this slot, else the bundled photograph. */
    fun wall(ctx: Context, prefs: Prefs): Wall {
        val cached = images(prefs).mapNotNull { (id, cap) -> file(ctx, id).takeIf { it.exists() }?.let { Wall(it, cap) } }
        if (cached.isEmpty()) return FALLBACK
        return if (prefs.dailyRotation) cached[((System.currentTimeMillis() / ROTATE_MS) % cached.size).toInt()] else cached[0]
    }

    /** Off the UI thread: refetch the list once a day, download missing photos, prune old ones; [changed] on new files. */
    fun refresh(ctx: Context, prefs: Prefs, changed: () -> Unit) {
        if (busy) return
        busy = true
        Thread {
            try {
                val now = System.currentTimeMillis()
                if (prefs.bingJson == null || now - prefs.bingAt > 86_400_000L) runCatching {
                    String(get(LIST), Charsets.UTF_8).also { JSONObject(it).getJSONArray("images") }
                }.onSuccess { prefs.bingJson = it; prefs.bingAt = now }
                val list = images(prefs)
                var got = false
                list.forEach { (id, _) ->
                    val f = file(ctx, id)
                    if (!f.exists()) runCatching {
                        val tmp = File(f.path + ".part")
                        tmp.writeBytes(get("https://www.bing.com/th?id=${id}_UHD.jpg"))
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(tmp.path, o)
                        if (o.outWidth >= 3840 && o.outHeight >= 2160 && tmp.renameTo(f)) got = true else tmp.delete()
                    }
                }
                val keep = list.map { file(ctx, it.first).name }.toSet()
                dir(ctx).listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
                if (got) changed()
            } catch (_: Exception) {
            } finally { busy = false }
        }.start()
    }

    /** (image id, copyright caption) pairs from the cached list, newest first. */
    private fun images(prefs: Prefs): List<Pair<String, String>> = runCatching {
        val a = JSONObject(prefs.bingJson ?: return emptyList()).getJSONArray("images")
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            it.getString("urlbase").substringAfter("id=") to it.optString("copyright")
        }.filter { it.first.matches(Regex("[A-Za-z0-9._-]+")) }
    }.getOrDefault(emptyList())

    private fun dir(ctx: Context) = File(ctx.filesDir, "bing").apply { mkdirs() }
    private fun file(ctx: Context, id: String) = File(dir(ctx), "$id.jpg")

    private fun get(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 20000
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally { conn.disconnect() }
    }
}
