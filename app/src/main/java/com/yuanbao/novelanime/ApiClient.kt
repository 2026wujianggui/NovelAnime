package com.yuanbao.novelanime

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val JSON_MT = "application/json; charset=utf-8".toMediaType()

    class ApiErr(msg: String) : Exception(msg)

    private fun auth(key: String) = if (key.startsWith("Bearer ")) key else "Bearer $key"

    /** 提交一个 POST，返回响应体字符串 */
    private fun post(url: String, key: String, body: String): String {
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", auth(key))
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MT))
            .build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string() ?: ""
            if (!r.isSuccessful) throw ApiErr("HTTP ${r.code}: ${s.take(300)}")
            return s
        }
    }

    private fun get(url: String, key: String): String {
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", auth(key))
            .get().build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string() ?: ""
            if (!r.isSuccessful) throw ApiErr("HTTP ${r.code}: ${s.take(300)}")
            return s
        }
    }

    /**
     * 文生图。返回 Bitmap。
     * 兼容两种返回：url 或 b64_json
     */
    suspend fun textToImage(
        base: String, key: String, model: String, prompt: String, size: String = "1280x720"
    ): Bitmap = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
            put("size", size)
            put("response_format", "b64_json")
            put("n", 1)
        }.toString()

        val s = post("${base.trimEnd('/')}/images/generations", key, body)
        val jo = JSONObject(s)
        val arr: JSONArray = jo.optJSONArray("data")
            ?: throw ApiErr("返回无 data 字段：${s.take(300)}")
        val first = arr.getJSONObject(0)

        when {
            first.has("b64_json") && !first.isNull("b64_json") -> {
                val bytes = Base64.decode(first.getString("b64_json"), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw ApiErr("图片解码失败")
            }
            first.has("url") && !first.isNull("url") -> {
                val u = first.getString("url")
                val req = Request.Builder().url(u).get().build()
                http.newCall(req).execute().use { r ->
                    val bytes = r.body?.bytes() ?: throw ApiErr("下载图片为空")
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: throw ApiErr("图片解码失败")
                }
            }
            else -> throw ApiErr("返回里既没有 url 也没有 b64_json：${s.take(300)}")
        }
    }

    /**
     * 图生视频（异步）。
     * 通用 OpenAI 兼容流程：POST 拿 id → 轮询 GET 拿 video_url
     * onProgress: 回调轮询次数
     */
    suspend fun imageToVideo(
        base: String, key: String, model: String,
        prompt: String, imageBase64: String,
        onProgress: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val b = base.trimEnd('/')

        // 1) 提交
        val contentArr = JSONArray().apply {
            put(JSONObject().apply { put("type", "text"); put("text", prompt) })
            put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:image/jpeg;base64,$imageBase64")
                })
            })
        }
        val body = JSONObject().apply {
            put("model", model)
            put("content", contentArr)
        }.toString()

        onProgress("提交任务…")
        val s = post("$b/videos/generations", key, body)
        val jo = JSONObject(s)
        val id = jo.optString("id").ifEmpty { jo.optString("task_id") }
        if (id.isEmpty()) throw ApiErr("未取到任务 id：${s.take(300)}")

        // 2) 轮询
        var last = ""
        for (i in 1..120) { // 最多约 10 分钟
            kotlinx.coroutines.delay(5000)
            val st = try {
                get("$b/videos/generations/$id", key)
            } catch (e: Exception) { continue }
            last = st
            val o = JSONObject(st)
            val status = o.optString("status").lowercase()
            when {
                status == "succeeded" || status == "success" || status == "completed" -> {
                    val url = o.optString("video_url")
                        .ifEmpty { o.optJSONObject("content")?.optString("video_url") ?: "" }
                        .ifEmpty { o.optJSONObject("output")?.optString("video_url") ?: "" }
                    if (url.isEmpty()) throw ApiErr("任务完成但无 video_url：${st.take(300)}")
                    return@withContext url
                }
                status == "failed" || status == "error" -> {
                    throw ApiErr("生成失败：${o.optString("error", st).take(300)}")
                }
                else -> onProgress("生成中… 已等待 ${i * 5} 秒（$status）")
            }
        }
        throw ApiErr("超时未出片。最后一次状态：${last.take(200)}")
    }

    /** 下载视频到字节 */
    suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { r ->
            r.body?.bytes() ?: throw ApiErr("视频下载为空")
        }
    }

    /** 连通性自检 */
    suspend fun ping(base: String, key: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", "")
            put("prompt", "ping")
        }.toString()
        try {
            val s = post("${base.trimEnd('/')}/images/generations", key, body)
            "连通（接口有响应）：${s.take(120)}"
        } catch (e: Exception) {
            "未连通：${e.message?.take(200)}"
        }
    }

    /**
     * 拉取模型列表。自动尝试多种 base 组合，
     * 返回 Pair(可用的 base, 模型 id 列表)
     */
    suspend fun fetchModels(baseInput: String, key: String): Pair<String, List<String>> =
        withContext(Dispatchers.IO) {
            val b0 = baseInput.trim().trimEnd('/')
            val candidates = linkedSetOf<String>()
            candidates.add(b0)
            if (b0.endsWith("/v1")) candidates.add(b0.removeSuffix("/v1"))
            else candidates.add("$b0/v1")
            candidates.add("$b0/v1")
            if (!b0.endsWith("/v1")) candidates.add(b0.removeSuffix("/v1"))

            val errs = mutableListOf<String>()
            for (b in candidates) {
                val url = "$b/models"
                try {
                    val s = get(url, key)
                    val jo = JSONObject(s)
                    val list = mutableListOf<String>()
                    val arr = jo.optJSONArray("data")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val id = o.optString("id").ifEmpty { o.optString("name") }
                            if (id.isNotBlank()) list.add(id)
                        }
                    } else {
                        jo.optJSONArray("models")?.let { a ->
                            for (i in 0 until a.length()) {
                                val o = a.getJSONObject(i)
                                val id = o.optString("id").ifEmpty { o.optString("name") }
                                if (id.isNotBlank()) list.add(id)
                            }
                        }
                    }
                    if (list.isNotEmpty()) return@withContext Pair(b, list)
                    errs.add("$url → 返回为空")
                } catch (e: Exception) {
                    errs.add("$url → ${e.message?.take(80)}")
                }
            }
            throw ApiErr("没能拉到模型列表：\n${errs.joinToString("\n")}")
        }
}
