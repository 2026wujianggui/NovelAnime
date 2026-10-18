package com.yuanbao.novelanime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 角色库 —— 本 App 最值钱的功能。
 *
 * 商业竞品（即梦/可灵/ELSER）的核心卖点就是「角色一致性」：
 * 同一个角色在第 1 镜和第 30 镜必须长得一样。
 *
 * 做法：
 *   1) 为每个角色建一张卡（外貌描述 + 一张参考图）
 *   2) 参考图由 AI 生成一次，之后所有分镜都带上这张图做参考
 *   3) 每次出图都固定 seed，漂移最小化
 */
object CharacterLib {

    data class CharCard(
        val name: String,
        val desc: String,       // 外貌描述（写死，不随分镜变）
        val refPath: String,    // 参考图本地路径
        val seed: Int
    )

    private fun dir(ctx: Context) = File(ctx.getExternalFilesDir("chars"), "").apply { mkdirs() }
    private fun jsonFile(ctx: Context) = File(dir(ctx), "chars.json")

    fun load(ctx: Context): MutableList<CharCard> {
        val f = jsonFile(ctx)
        if (!f.exists()) return defaultCards(ctx)
        return try {
            val arr = JSONArray(f.readText())
            val out = mutableListOf<CharCard>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(CharCard(o.getString("name"), o.getString("desc"),
                    o.optString("refPath"), o.optInt("seed", 1000 + i)))
            }
            out
        } catch (e: Exception) { defaultCards(ctx) }
    }

    fun save(ctx: Context, list: List<CharCard>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("name", it.name); put("desc", it.desc)
                put("refPath", it.refPath); put("seed", it.seed)
            })
        }
        jsonFile(ctx).writeText(arr.toString())
    }

    /** 内置：从《旧物残影》设定表带出来的四个角色 */
    private fun defaultCards(ctx: Context): MutableList<CharCard> {
        val l = mutableListOf(
            CharCard("沈砚",
                "28岁男性，黑色短发微乱，左眼瞳孔偏灰，穿深灰色粗布围裙，右手食指有烫伤疤，身形偏瘦，表情克制寡言",
                "", 1001),
            CharCard("宋宜",
                "53岁男性，花白短发，穿洗得发白的深色夹克，右腿有旧伤站立重心偏左，眼角深皱纹，神情疲惫执拗",
                "", 1002),
            CharCard("陈七",
                "40岁男性，圆脸，笑起来眼睛眯成缝，右手拇指戴玉扳指，穿休闲外套，体态微胖",
                "", 1003),
            CharCard("周曼",
                "46岁女性，头发挽在脑后，穿深灰色对襟衫，右手腕戴一圈沉香珠，神情平静举止得体",
                "", 1004)
        )
        save(ctx, l)
        return l
    }

    /** 参考图文件 */
    fun refFile(ctx: Context, name: String) = File(dir(ctx), "ref_$name.png")

    fun hasRef(ctx: Context, name: String) = refFile(ctx, name).exists()

    fun refBitmap(ctx: Context, name: String): Bitmap? {
        val f = refFile(ctx, name)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    fun saveRef(ctx: Context, name: String, bmp: Bitmap) {
        refFile(ctx, name).outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 95, it)
        }
    }

    /**
     * 生成该角色的「定妆图」提示词 —— 只用外貌，不带剧情，
     * 保证参考图干净、可复用。
     */
    fun refPrompt(ctx: Context, c: CharCard): String {
        val style = KeyStore.style(ctx).ifEmpty {
            "新海诚风格日本动画电影质感，柔和自然光，电影级构图"
        }
        return "$style。角色定妆图，正面半身肖像，纯色简洁背景，面部清晰完整。" +
               "【角色】${c.desc}。要求：五官稳定，比例准确，不要遮挡面部。"
    }

    /**
     * 组装分镜提示词 —— 带上角色卡的固定描述 + 参考图标记
     */
    fun shotPrompt(ctx: Context, baseDesc: String, chars: List<String>): String {
        val style = KeyStore.style(ctx).ifEmpty {
            "新海诚风格日本动画电影质感，柔和自然光，电影级构图，16:9"
        }
        val cards = load(ctx).filter { it.name in chars }
        val anch = if (cards.isNotEmpty())
            "【角色固定设定，必须与定妆图一致】" + cards.joinToString("；") { "${it.name}：${it.desc}" } + "。"
        else ""
        return "$style。$anch【场景】$baseDesc"
    }
}
