package com.abdulhameed.novavideo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class Cancelled : Exception()
class ApiError(message: String) : Exception(message)

// Keys are stored only on this phone.
object Keys {
    private fun p(c: Context) = c.getSharedPreferences("nova", Context.MODE_PRIVATE)
    fun kimi(c: Context): String = p(c).getString("kimi", "") ?: ""
    fun hf(c: Context): String = p(c).getString("hf", "") ?: ""
    fun better(c: Context): Boolean = p(c).getBoolean("better", false)
    fun save(c: Context, kimi: String, hf: String, better: Boolean) {
        p(c).edit()
            .putString("kimi", kimi.trim())
            .putString("hf", hf.trim())
            .putBoolean("better", better)
            .apply()
    }
}

object Api {
    private const val SPACE = "https://lightricks-ltx-video-distilled.hf.space"
    private const val KIMI_URL = "https://api.moonshot.ai/v1/chat/completions"
    private const val KIMI_MODEL = "kimi-k3.1-pro"
    private const val NEGATIVE = "worst quality, inconsistent motion, blurry, jittery, distorted"
    private const val SYSTEM_PROMPT =
        "You write prompts for an AI text-to-video model. Turn the user's idea into ONE flowing " +
            "paragraph of 50 to 80 words, in English. Describe the main subject, the action in time " +
            "order, the setting, the camera movement, the lighting and the visual style. Do not add " +
            "titles, lists, quotation marks or any extra text. Output only the paragraph."

    @Volatile
    var cancelled = false

    @Volatile
    private var current: HttpURLConnection? = null

    fun reset() {
        cancelled = false
    }

    fun cancel() {
        cancelled = true
        try {
            current?.disconnect()
        } catch (e: Exception) {
        }
    }

    private fun open(url: String, method: String, headers: Map<String, String>, readTimeout: Int): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = readTimeout
        for ((k, v) in headers) conn.setRequestProperty(k, v)
        current = conn
        return conn
    }

    private fun readBody(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val s = if (code in 200..299) conn.inputStream else conn.errorStream
        return s?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun postJson(url: String, headers: Map<String, String>, json: String, readTimeout: Int): Pair<Int, String> {
        val h = HashMap<String, String>(headers)
        h["Content-Type"] = "application/json"
        val conn = open(url, "POST", h, readTimeout)
        conn.doOutput = true
        conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = readBody(conn)
        conn.disconnect()
        return Pair(code, text)
    }

    // ---- Kimi: rewrite the idea as a detailed scene description ----
    private fun kimiBody(idea: String, withEffort: Boolean): String {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
        msgs.put(JSONObject().put("role", "user").put("content", idea))
        val o = JSONObject().put("model", KIMI_MODEL).put("messages", msgs)
        if (withEffort) o.put("reasoning_effort", "low")
        return o.toString()
    }

    fun improvePrompt(key: String, idea: String): String {
        try {
            val headers = HashMap<String, String>()
            headers["Authorization"] = "Bearer $key"
            var r = postJson(KIMI_URL, headers, kimiBody(idea, true), 90000)
            if (r.first !in 200..299 && !cancelled) {
                r = postJson(KIMI_URL, headers, kimiBody(idea, false), 90000)
            }
            if (cancelled) throw Cancelled()
            if (r.first == 401) throw ApiError("Kimi did not accept the key")
            if (r.first !in 200..299) throw ApiError("Kimi error " + r.first)
            val j = JSONObject(r.second)
            val text = j.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content", "").trim().trim('"')
            if (text.isEmpty()) throw ApiError("Kimi gave an empty answer")
            return text
        } catch (e: Cancelled) {
            throw e
        } catch (e: ApiError) {
            throw e
        } catch (e: Exception) {
            if (cancelled) throw Cancelled()
            throw ApiError("Kimi could not be reached")
        }
    }

    // ---- Hugging Face Space: text-to-video ----
    private fun httpMessage(code: Int): String {
        return when (code) {
            401, 403 -> "Hugging Face did not accept your token. Check it in Settings."
            429 -> "Too many requests, or your free GPU time is used up. Try again later."
            502, 503, 504 -> "The video service is busy or waking up. Try again in a minute."
            else -> "The video service returned error $code."
        }
    }

    private fun friendly(data: String): String {
        val d = data.trim().trim('"')
        val low = d.lowercase()
        if (low.contains("quota")) {
            return "You have used today's free GPU time. It resets about 24 hours after your first use."
        }
        if (d.isEmpty() || low == "null") return "The video service reported an error. Try again in a minute."
        return d
    }

    private fun findUrl(o: Any?): String? {
        if (o is JSONObject) {
            val u = o.optString("url", "")
            if (u.isNotEmpty() && u != "null") return u
            val p = o.optString("path", "")
            if (p.isNotEmpty() && p != "null") return SPACE + "/gradio_api/file=" + p
            val keys = o.keys()
            while (keys.hasNext()) {
                val r = findUrl(o.opt(keys.next()))
                if (r != null) return r
            }
        } else if (o is JSONArray) {
            for (i in 0 until o.length()) {
                val r = findUrl(o.opt(i))
                if (r != null) return r
            }
        }
        return null
    }

    fun generateVideo(
        hf: String, prompt: String, seconds: Double, width: Int, height: Int,
        better: Boolean, dest: File, onStatus: (String) -> Unit
    ) {
        try {
            val data = JSONArray()
            data.put(prompt)
            data.put(NEGATIVE)
            data.put(JSONObject.NULL)
            data.put(JSONObject.NULL)
            data.put(height)
            data.put(width)
            data.put("text-to-video")
            data.put(seconds)
            data.put(9)
            data.put(42)
            data.put(true)
            data.put(1.0)
            data.put(better)
            val body = JSONObject().put("data", data).toString()

            val auth = HashMap<String, String>()
            if (hf.isNotEmpty()) auth["Authorization"] = "Bearer $hf"

            var prefix = "/gradio_api"
            var eventId = ""
            for (attempt in 0..1) {
                val r = postJson(SPACE + prefix + "/call/text_to_video", auth, body, 60000)
                if (cancelled) throw Cancelled()
                if (r.first in 200..299) {
                    eventId = JSONObject(r.second).getString("event_id")
                    break
                }
                if (r.first == 404 && attempt == 0) {
                    prefix = ""
                    continue
                }
                throw ApiError(httpMessage(r.first))
            }

            onStatus("Waiting for a free GPU. This can take a few minutes. Keep the app open.")
            val conn = open(SPACE + prefix + "/call/text_to_video/" + eventId, "GET", auth, 900000)
            conn.setRequestProperty("Accept", "text/event-stream")
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                throw ApiError(httpMessage(code))
            }
            var result = ""
            val reader = conn.inputStream.bufferedReader()
            var event = ""
            loop@ while (true) {
                if (cancelled) throw Cancelled()
                val line = reader.readLine() ?: break@loop
                if (line.startsWith("event:")) {
                    event = line.substring(6).trim()
                } else if (line.startsWith("data:")) {
                    val d = line.substring(5).trim()
                    if (event == "complete") {
                        result = d
                        break@loop
                    } else if (event == "error") {
                        throw ApiError(friendly(d))
                    } else {
                        onStatus("Rendering your video. Keep the app open.")
                    }
                }
            }
            conn.disconnect()
            if (result.isEmpty()) throw ApiError("The video service closed the connection. Try again.")

            val url = findUrl(JSONArray(result).opt(0)) ?: throw ApiError("The service did not return a video.")
            onStatus("Downloading your video...")
            val full = if (url.startsWith("http")) url else SPACE + url
            val dl = open(full, "GET", auth, 120000)
            val dcode = dl.responseCode
            if (dcode !in 200..299) {
                dl.disconnect()
                throw ApiError("Could not download the video ($dcode).")
            }
            dl.inputStream.use { input ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(65536)
                    while (true) {
                        if (cancelled) throw Cancelled()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
            }
            dl.disconnect()
        } catch (e: Cancelled) {
            throw e
        } catch (e: ApiError) {
            throw e
        } catch (e: Exception) {
            if (cancelled) throw Cancelled()
            throw ApiError("Network problem: " + (e.message ?: "unknown"))
        }
    }
}
