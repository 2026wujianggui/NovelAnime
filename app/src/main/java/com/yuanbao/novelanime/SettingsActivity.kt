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
