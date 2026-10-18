package com.yuanbao.novelanime

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.widget.ListView
import android.widget.LinearLayout
import android.app.AlertDialog
import android.view.ViewGroup

class SettingsActivity : AppCompatActivity() {

    private lateinit var sp: Spinner
    private lateinit var etKey: EditText
    private lateinit var etBase: EditText
    private lateinit var etImg: EditText
    private lateinit var etVid: EditText
    private lateinit var etStyle: EditText
    private lateinit var tvTest: TextView

    private val providers = KeyStore.Provider.values()
    private val defaults = mapOf(
        KeyStore.Provider.VOLC.name to Pair(
            "https://ark.cn-beijing.volces.com/api/v3",
            Pair("doubao-seedream-3-0-t2i-250415", "doubao-seedance-1-0-lite-i2v-250428")
        ),
        KeyStore.Provider.KLING.name to Pair(
            "https://api-beijing.klingai.com",
            Pair("kling-v1-5", "kling-v1-5")
        ),
        KeyStore.Provider.CUSTOM.name to Pair("", Pair("", ""))
    )

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_settings)
        sp = findViewById(R.id.spProvider)
        etKey = findViewById(R.id.etKey)
        etBase = findViewById(R.id.etBase)
        etImg = findViewById(R.id.etImgModel)
        etVid = findViewById(R.id.etVidModel)
        etStyle = findViewById(R.id.etStyle)
        tvTest = findViewById(R.id.tvTest)

        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            providers.map { it.label })

        val cur = KeyStore.provider(this)
        val idx = providers.indexOfFirst { it.name == cur }.let { if (it < 0) 0 else it }
        sp.setSelection(idx)

        etKey.setText(KeyStore.apiKey(this))
        etBase.setText(KeyStore.baseUrl(this).ifEmpty { defaults[cur]?.first ?: "" })
        etImg.setText(KeyStore.imageModel(this))
        etVid.setText(KeyStore.videoModel(this))
        etStyle.setText(KeyStore.style(this).ifEmpty {
            "新海诚风格日本动画电影质感，柔和自然光，电影级构图，16:9"
        })

        sp.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, i: Int, id: Long) {
                val name = providers[i].name
                val d = defaults[name]
                if (d != null && d.first.isNotBlank()) {
                    etBase.setText(d.first)
                    etImg.setText(d.second.first)
                    etVid.setText(d.second.second)
                }
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        })

        findViewById<Button>(R.id.btnTest).setOnClickListener { test() }
        val btnModels = Button(this).apply {
            text = "拉取模型列表（自动探测）"
            setOnClickListener { fetchModelsUI() }
        }
        // 插到「测试连通性」按钮上方
        val btnParent = findViewById<Button>(R.id.btnTest).parent as ViewGroup
        val testIdx = (0 until btnParent.childCount).firstOrNull {
            btnParent.getChildAt(it).id == R.id.btnTest
        }
        if (testIdx != null) btnParent.addView(btnModels, testIdx)
        else btnParent.addView(btnModels)
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
    }

    private fun save() {
        val name = providers[sp.selectedItemPosition].name
        KeyStore.setProvider(this, name)
        KeyStore.setApiKey(this, etKey.text.toString().trim())
        KeyStore.setBaseUrl(this, etBase.text.toString().trim())
        KeyStore.setImageModel(this, etImg.text.toString().trim())
        KeyStore.setVideoModel(this, etVid.text.toString().trim())
        KeyStore.setStyle(this, etStyle.text.toString().trim())
        Toast.makeText(this, "已保存到本机", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun fetchModelsUI() {
        val key = etKey.text.toString().trim()
        val base = etBase.text.toString().trim()
        if (key.isBlank() || base.isBlank()) { tvTest.text = "先填 Key 和地址"; return }
        tvTest.text = "正在拉取模型列表…"
        lifecycleScope.launch {
            try {
                val (okBase, models) = ApiClient.fetchModels(base, key)
                // 回填可用地址
                etBase.setText(okBase)
                showModelPicker(okBase, models)
                tvTest.text = "✅ 通了：$okBase（共 ${models.size} 个模型）"
            } catch (e: Exception) {
                tvTest.text = "❌ ${e.message?.take(600)}"
            }
        }
    }

    private fun showModelPicker(okBase: String, models: List<String>) {
        // 猜测哪些是绘图、哪些是视频
        fun isImg(m: String): Boolean {
            val s = m.lowercase()
            return listOf("dall","gpt-image","image","flux","seedream","midjourney","mj","sd","stable","recraft","ideogram","kolors","wanx","irag")
                .any { s.contains(it) } && !listOf("video","kling","seedance","hailuo","sora","wan2","i2v","t2v").any { s.contains(it) }
        }
        fun isVid(m: String): Boolean {
            val s = m.lowercase()
            return listOf("video","kling","seedance","hailuo","sora","i2v","t2v","wan2","animate","omni").any { s.contains(it) }
        }
        val imgs = models.filter { isImg(it) }
        val vids = models.filter { isVid(it) }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 8)
        }
        val hint = TextView(this).apply {
            text = "点一个模型填进对应栏。\n灰字是自动分类，仅供参考。"
            setTextSize(11f)
        }
        container.addView(hint)

        fun makeList(title: String, data: List<String>, et: EditText) {
            container.addView(TextView(this).apply {
                text = title; setTextSize(13f)
                setPadding(0, 12, 0, 4)
            })
            if (data.isEmpty()) {
                container.addView(TextView(this).apply { text = "（没识别到）"; setTextSize(11f) })
                return
            }
            val lv = ListView(this)
            lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, data)
            lv.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 300)
            lv.setOnItemClickListener { _, _, pos, _ ->
                et.setText(data[pos])
                tvTest.text = "已选择：${data[pos]}"
            }
            container.addView(lv)
        }
        makeList("① 绘图模型（点一下填入）", imgs.ifEmpty { models }, etImg)
        makeList("② 视频模型（点一下填入）", vids.ifEmpty { models }, etVid)

        val scroll = android.widget.ScrollView(this)
        scroll.addView(container)
        AlertDialog.Builder(this)
            .setTitle("可用模型 ${models.size} 个")
            .setView(scroll)
            .setPositiveButton("完成", null)
            .show()
    }

    private fun test() {
        val key = etKey.text.toString().trim()
        val base = etBase.text.toString().trim()
        if (key.isBlank() || base.isBlank()) { tvTest.text = "先填 Key 和地址"; return }
        tvTest.text = "测试中…"
        lifecycleScope.launch {
            val r = ApiClient.ping(base, key)
            tvTest.text = r
        }
    }
}
