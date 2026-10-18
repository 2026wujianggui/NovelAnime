package com.yuanbao.novelanime

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var listShots: LinearLayout
    private lateinit var tvStatus: TextView
    private val shots = mutableListOf<Shot>()
    private var customStory: Boolean = false

    data class Shot(
        val no: Int, val title: String, val place: String,
        val chars: List<String>, val desc: String, val anchor: String, val motion: String
    )

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        listShots = findViewById(R.id.listShots)
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnImport).setOnClickListener { showImportDialog() }
        findViewById<Button>(R.id.btnBatch).setOnClickListener { batchGenerate() }

        loadBuiltIn()
    }

    override fun onResume() {
        super.onResume()
        val ok = KeyStore.ready(this)
        tvStatus.text = if (ok) "✅ 密钥已配置（${KeyStore.provider(this)}）"
        else "⚠️ 未配置密钥 → 点右上角「设置密钥」"
    }

    private fun loadBuiltIn() {
        val jo = JSONObject(BuiltInStory.JSON)
        val arr = jo.getJSONArray("shots")
        shots.clear()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val chars = mutableListOf<String>()
            val ca = o.getJSONArray("chars")
            for (j in 0 until ca.length()) chars.add(ca.getString(j))
            shots.add(
                Shot(
                    o.getInt("no"), o.getString("title"), o.getString("place"),
                    chars, o.getString("desc"), o.optString("anchor"), o.optString("motion")
                )
            )
        }
        customStory = false
        render()
    }

    /** 导入自己的小说：按段落切分镜 */
    private fun showImportDialog() {
        val et = EditText(this).apply {
            hint = "把小说粘贴进来（会按段落自动切分镜）"
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false); minLines = 8; gravity = Gravity.TOP
        }
        AlertDialog.Builder(this)
            .setTitle("导入小说")
            .setView(et)
            .setNegativeButton("取消", null)
            .setPositiveButton("切分镜") { _, _ ->
                val txt = et.text.toString().trim()
                if (txt.length < 20) { toast("太短了"); return@setPositiveButton }
                val paras = txt.split(Regex("\\n+")).filter { it.trim().length >= 15 }
                shots.clear()
                paras.forEachIndexed { i, p ->
                    shots.add(Shot(i + 1, "第${i + 1}镜", "—", listOf(), p.trim(), "", "缓慢推近，画面有细微动态"))
                }
                customStory = true
                render()
                toast("已切成 ${shots.size} 个镜")
            }.show()
    }

    private fun render() {
        listShots.removeAllViews()
        shots.forEach { sh ->
            listShots.addView(shotCard(sh))
        }
    }

    private fun shotCard(sh: Shot): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
            setBackgroundColor(Color.parseColor("#1E1E2A"))
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 10) }
        card.layoutParams = lp

        val head = TextView(this).apply {
            text = "镜 ${sh.no}　《${sh.title}》　${sh.place}"
            setTextColor(Color.parseColor("#FFB86C")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        val body = TextView(this).apply {
            text = sh.desc.take(90) + if (sh.desc.length > 90) "…" else ""
            setTextColor(Color.parseColor("#C8CDD8")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 6, 0, 6)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val bImg = Button(this).apply {
            text = "出图"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setOnClickListener { genImage(sh, card) }
        }
        val bVid = Button(this).apply {
            text = "图→视频"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setOnClickListener { genVideo(sh, card) }
        }
        row.addView(bImg); row.addView(bVid)

        val status = TextView(this).apply {
            text = ""; setTextColor(Color.parseColor("#8890A0")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            tag = "status"
        }
        val imgView = ImageView(this).apply {
            tag = "img"; visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 400
            ).apply { setMargins(0, 8, 0, 0) }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        card.addView(head); card.addView(body); card.addView(row)
        card.addView(status); card.addView(imgView)
        // 缓存 bitmap 引用
        card.tag = sh.no
        return card
    }

    private fun findCard(no: Int): LinearLayout? {
        for (i in 0 until listShots.childCount) {
            val v = listShots.getChildAt(i)
            if (v.tag == no) return v as LinearLayout
        }
        return null
    }

    private fun setStatus(card: LinearLayout, msg: String) {
        runOnUiThread {
            val tv = card.findViewWithTag<TextView>("status"); tv?.text = msg
        }
    }

    private fun buildPrompt(sh: Shot): String {
        val style = KeyStore.style(this).ifEmpty {
            "新海诚风格日本动画电影质感，柔和自然光，电影级构图，16:9"
        }
        val anch = if (sh.anchor.isNotBlank()) "【人物固定设定，必须严格遵守】${sh.anchor}。" else ""
        return "$style。$anch【场景】${sh.desc}"
    }

    private fun genImage(sh: Shot, card: LinearLayout) {
        if (!KeyStore.ready(this)) { toast("先去设置密钥"); return }
        lifecycleScope.launch {
            setStatus(card, "出图中…")
            try {
                val bmp = ApiClient.textToImage(
                    KeyStore.baseUrl(this@MainActivity), KeyStore.apiKey(this@MainActivity),
                    KeyStore.imageModel(this@MainActivity), buildPrompt(sh)
                )
                val f = File(getExternalFilesDir("img"), "shot_${sh.no}.png")
                f.parentFile?.mkdirs()
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
                runOnUiThread {
                    val iv = card.findViewWithTag<ImageView>("img")
                    iv?.setImageBitmap(bmp); iv?.visibility = android.view.View.VISIBLE
                }
                setStatus(card, "✅ 图已存：${f.name}")
            } catch (e: Exception) {
                setStatus(card, "❌ ${e.message?.take(150)}")
            }
        }
    }

    private fun genVideo(sh: Shot, card: LinearLayout) {
        if (!KeyStore.ready(this)) { toast("先去设置密钥"); return }
        val imgFile = File(getExternalFilesDir("img"), "shot_${sh.no}.png")
        if (!imgFile.exists()) { toast("先出图，再图生视频"); return }
        lifecycleScope.launch {
            setStatus(card, "提交视频任务…")
            try {
                val b64 = android.util.Base64.encodeToString(imgFile.readBytes(), android.util.Base64.NO_WRAP)
                val url = ApiClient.imageToVideo(
                    KeyStore.baseUrl(this@MainActivity), KeyStore.apiKey(this@MainActivity),
                    KeyStore.videoModel(this@MainActivity),
                    "镜头缓慢推进，${sh.motion}", b64
                ) { msg -> setStatus(card, msg) }
                val bytes = ApiClient.download(url)
                val vf = File(getExternalFilesDir("video"), "shot_${sh.no}.mp4")
                vf.parentFile?.mkdirs(); vf.writeBytes(bytes)
                setStatus(card, "✅ 视频已存：$vf")
            } catch (e: Exception) {
                setStatus(card, "❌ ${e.message?.take(150)}")
            }
        }
    }

    private fun batchGenerate() {
        if (!KeyStore.ready(this)) { toast("先去设置密钥"); return }
        toast("开始按序生成 ${shots.size} 镜，耗时较长")
        lifecycleScope.launch {
            for (sh in shots) {
                val card = findCard(sh.no) ?: continue
                genImageAndWait(sh, card)
                genVideoAndWait(sh, card)
            }
            runOnUiThread { toast("全部完成，文件在手机内部存储 Android/data/${packageName}/files/") }
        }
    }

    private suspend fun genImageAndWait(sh: Shot, card: LinearLayout) {
        try {
            val bmp = ApiClient.textToImage(
                KeyStore.baseUrl(this), KeyStore.apiKey(this),
                KeyStore.imageModel(this), buildPrompt(sh)
            )
            val f = File(getExternalFilesDir("img"), "shot_${sh.no}.png")
            f.parentFile?.mkdirs(); f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
            runOnUiThread {
                val iv = card.findViewWithTag<ImageView>("img")
                iv?.setImageBitmap(bmp); iv?.visibility = android.view.View.VISIBLE
            }
            setStatus(card, "✅ 图 OK")
        } catch (e: Exception) { setStatus(card, "❌ 图失败：${e.message?.take(80)}") }
    }

    private suspend fun genVideoAndWait(sh: Shot, card: LinearLayout) {
        val imgFile = File(getExternalFilesDir("img"), "shot_${sh.no}.png")
        if (!imgFile.exists()) { setStatus(card, "⚠️ 无图，跳过视频"); return }
        try {
            val b64 = android.util.Base64.encodeToString(imgFile.readBytes(), android.util.Base64.NO_WRAP)
            val url = ApiClient.imageToVideo(
                KeyStore.baseUrl(this), KeyStore.apiKey(this),
                KeyStore.videoModel(this), "镜头缓慢推进，${sh.motion}", b64
            ) { msg -> setStatus(card, msg) }
            val vf = File(getExternalFilesDir("video"), "shot_${sh.no}.mp4")
            vf.parentFile?.mkdirs(); vf.writeBytes(ApiClient.download(url))
            setStatus(card, "✅ 图+视频 完成")
        } catch (e: Exception) { setStatus(card, "❌ 视频失败：${e.message?.take(80)}") }
    }

    private fun toast(s: String) = runOnUiThread {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }
}
