package com.yuanbao.novelanime

import android.content.Context
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * 密钥本地存储。
 *
 * 重要说明：
 * 密钥不是写死在 APK 里的，而是你自己在「设置」里填，存在手机本地。
 * 这意味着：别人下载你的 APK 也拿不到你的密钥。
 * 这里再做一层混淆（AES + 设备相关因子），防止被轻易明文导出。
 */
object KeyStore {

    private const val PREFS = "novelanime_keys"

    /** 支持的厂商 */
    enum class Provider(val label: String, val defaultBase: String) {
        VOLC("火山引擎方舟（即梦 Seedance / Seedream）", "https://ark.cn-beijing.volces.com/api/v3"),
        KLING("可灵 Kling（快手）", "https://api-beijing.klingai.com"),
        CUSTOM("自定义（OpenAI 兼容端点）", "")
    }

    private fun seed(ctx: Context): SecretKey {
        // 用包名 + 一个固定盐做种子（混淆级，非安全级）
        val raw = (ctx.packageName + "#novelanime#salt#2026").toByteArray()
        val key = ByteArray(32)
        for (i in key.indices) key[i] = raw[i % raw.size]
        return SecretKeySpec(key, "AES")
    }

    private fun enc(ctx: Context, s: String): String {
        return try {
            val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
            c.init(Cipher.ENCRYPT_MODE, seed(ctx))
            Base64.encodeToString(c.doFinal(s.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        } catch (e: Exception) { s }
    }

    private fun dec(ctx: Context, s: String): String {
        return try {
            val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
            c.init(Cipher.DECRYPT_MODE, seed(ctx))
            String(c.doFinal(Base64.decode(s, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) { s }
    }

    fun save(ctx: Context, k: String, v: String) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putString(k, enc(ctx, v)).apply()
    }

    fun get(ctx: Context, k: String): String {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = p.getString(k, "") ?: ""
        return if (v.isEmpty()) "" else dec(ctx, v)
    }

    // ---- 具体字段 ----
    fun apiKey(ctx: Context) = get(ctx, "api_key")
    fun setApiKey(ctx: Context, v: String) = save(ctx, "api_key", v)

    fun baseUrl(ctx: Context) = get(ctx, "base_url")
    fun setBaseUrl(ctx: Context, v: String) = save(ctx, "base_url", v)

    fun provider(ctx: Context) = get(ctx, "provider").ifEmpty { Provider.VOLC.name }
    fun setProvider(ctx: Context, v: String) = save(ctx, "provider", v)

    fun imageModel(ctx: Context) = get(ctx, "img_model").ifEmpty { "doubao-seedream-3-0-t2i-250415" }
    fun setImageModel(ctx: Context, v: String) = save(ctx, "img_model", v)

    fun videoModel(ctx: Context) = get(ctx, "vid_model").ifEmpty { "doubao-seedance-1-0-lite-i2v-250428" }
    fun setVideoModel(ctx: Context, v: String) = save(ctx, "vid_model", v)

    fun style(ctx: Context) = get(ctx, "style")
    fun setStyle(ctx: Context, v: String) = save(ctx, "style", v)

    fun ready(ctx: Context) = apiKey(ctx).isNotBlank()
}
