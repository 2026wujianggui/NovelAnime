package com.yuanbao.novelanime

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * 角色库：核心是「定妆图」。
 * 先给角色生成一张标准像，之后所有分镜带上它，脸就不飘了。
 */
class CharacterActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private val cards = mutableListOf<CharacterLib.CharCard>()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_char)
        list = findViewById(R.id.listChars)
        cards.clear()
        cards.addAll(CharacterLib.load(this))
        render()

        findViewById<Button>(R.id.btnGenAll).setOnClickListener {
            if (!KeyStore.ready(this)) { toast("先设置密钥"); return@setOnClickListener }
            lifecycleScope.launch {
                cards.forEachIndexed { i, c ->
                    genRef(c, i)
                }
                toast("定妆图生成完毕")
                render()
            }
        }
    }

    private fun render() {
        list.removeAllViews()
        cards.forEachIndexed { i, c ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 10, 12, 10)
                setBackgroundColor(Color.parseColor("#1E1E2A"))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 10) }
            }
            val head = TextView(this).apply {
                text = c.name
                setTextColor(Color.parseColor("#FFB86C"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
            val desc = TextView(this).apply {
                text = c.desc
                setTextColor(Color.parseColor("#99A0B0"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, 4, 0, 6)
            }
            val iv = ImageView(this).apply {
                val b = CharacterLib.refBitmap(this@CharacterActivity, c.name)
                if (b != null) setImageBitmap(b)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 320)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#0E0E16"))
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val bGen = Button(this).apply {
                text = "生成定妆图"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setOnClickListener {
                    lifecycleScope.launch { genRef(c, i); render() }
                }
            }
            val st = TextView(this).apply {
                text = if (CharacterLib.hasRef(this@CharacterActivity, c.name)) "✅ 已有定妆图" else "未生成"
                setTextColor(Color.parseColor("#8890A0"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(12, 0, 0, 0)
            }
            row.addView(bGen); row.addView(st)
            card.addView(head); card.addView(desc); card.addView(iv); card.addView(row)
            list.addView(card)
        }
    }

    private suspend fun genRef(c: CharacterLib.CharCard, idx: Int) {
        try {
            val bmp: Bitmap = ApiClient.textToImage(
                KeyStore.baseUrl(this), KeyStore.apiKey(this),
                KeyStore.imageModel(this),
                CharacterLib.refPrompt(this, c),
                "768x1024"   // 竖版肖像更适合做定妆图
            )
            CharacterLib.saveRef(this, c.name, bmp)
        } catch (e: Exception) {
            runOnUiThread { toast("${c.name} 失败：${e.message?.take(80)}") }
        }
    }

    private fun toast(s: String) = runOnUiThread {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }
}
