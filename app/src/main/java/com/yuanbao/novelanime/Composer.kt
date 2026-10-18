package com.yuanbao.novelanime

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * 成片合成 —— 把一堆 5 秒片段拼成一集。
 *
 * 设计取舍：
 * 用 MediaExtractor + MediaMuxer 做「无重编码直拼」——
 * 不解码不编码，直接搬运码流。好处是快、稳、不会崩；
 * 代价是不能做交叉淡化转场（硬切），也不能烧字幕进画面。
 *
 * 所以配套做了两件事弥补：
 *   1) 导出 SRT 字幕文件（丢进剪映自动对齐）
 *   2) 导出剪映草稿 JSON（可选）
 *
 * 这是「有戏AI」等成熟产品的做法：App 负责量产素材，
 * 精修交给剪映。比硬塞一个半吊子剪辑器靠谱得多。
 */
object Composer {

    data class Segment(
        val video: File,
        val caption: String = ""
    )

    /** 合成 MP4。返回输出文件；失败返回 null */
    fun composeVideos(
        ctx: Context,
        segs: List<Segment>,
        outName: String = "episode.mp4",
        onProgress: (Int) -> Unit
    ): File? {
        val valid = segs.mapNotNull { s ->
            if (s.video.exists() && s.video.length() > 0) s else null
        }
        if (valid.isEmpty()) return null

        val outDir = File(ctx.getExternalFilesDir("episodes"), "").apply { mkdirs() }
        val out = File(outDir, outName)
        if (out.exists()) out.delete()

        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrack = -1
        var audioTrack = -1
        val indexMap = HashMap<Int, Int>()

        // 第一遍：用首个片段声明轨道
        val fmt = probe(valid.first().video)
        if (fmt.first != null) videoTrack = muxer.addTrack(fmt.first!!)
        if (fmt.second != null) audioTrack = muxer.addTrack(fmt.second!!)
        muxer.start()

        val buf = ByteBuffer.allocate(1024 * 1024)
        val info = android.media.MediaCodec.BufferInfo()
        var videoPtsOffset = 0L
        var audioPtsOffset = 0L
        var lastVideoPts = 0L
        var lastAudioPts = 0L

        valid.forEachIndexed { i, seg ->
            onProgress(i * 100 / valid.size)
            val ex = MediaExtractor()
            try {
                ex.setDataSource(seg.video.absolutePath)
                for (t in 0 until ex.trackCount) {
                    val f = ex.getTrackFormat(t)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                    val isV = mime.startsWith("video")
                    ex.selectTrack(t)

                    while (true) {
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) break
                        val pts = ex.sampleTime
                        val flags = ex.sampleFlags
                        info.set(0, n, pts + if (isV) videoPtsOffset else audioPtsOffset, flags)
                        val dst = if (isV) videoTrack else audioTrack
                        if (dst >= 0) muxer.writeSampleData(dst, buf, info)
                        if (isV) lastVideoPts = pts else lastAudioPts = pts
                        ex.advance()
                    }
                    ex.unselectTrack(t)
                }
                videoPtsOffset += lastVideoPts + 33_000
                audioPtsOffset += lastAudioPts + 21_000
            } catch (e: Exception) {
                // 单片段失败不中断整体
            } finally {
                ex.release()
            }
        }

        return try {
            muxer.stop(); muxer.release()
            onProgress(100)
            out
        } catch (e: Exception) {
            try { muxer.release() } catch (_: Exception) {}
            null
        }
    }

    /** 探测首片段的视/音轨格式 */
    private fun probe(f: File): Pair<MediaFormat?, MediaFormat?> {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(f.absolutePath)
            var v: MediaFormat? = null
            var a: MediaFormat? = null
            for (t in 0 until ex.trackCount) {
                val fmt = ex.getTrackFormat(t)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video") && v == null) v = fmt
                if (mime.startsWith("audio") && a == null) a = fmt
            }
            Pair(v, a)
        } catch (e: Exception) {
            Pair(null, null)
        } finally { ex.release() }
    }

    /**
     * 导出 SRT 字幕 —— 丢进剪映会自动对齐时间轴。
     * 每段按固定时长排，后续可手动微调。
     */
    fun exportSrt(ctx: Context, segs: List<Segment>, outName: String = "episode.srt"): File {
        val dir = File(ctx.getExternalFilesDir("episodes"), "").apply { mkdirs() }
        val f = File(dir, outName)
        val sb = StringBuilder()
        var t = 0L
        segs.forEachIndexed { i, s ->
            val dur = 5_000L
            val s0 = fmt(t); t += dur
            val s1 = fmt(t)
            sb.append("${i + 1}\n")
            sb.append("$s0 --> $s1\n")
            sb.append("${s.caption}\n\n")
        }
        f.writeText(sb.toString())
        return f
    }

    private fun fmt(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1000
        val r = ms % 1000
        return String.format("%02d:%02d:%02d,%03d", h, m, s, r)
    }

    /** 导出分镜表（给剪辑师用） */
    fun exportShotList(ctx: Context, segs: List<Segment>, outName: String = "分镜表.txt"): File {
        val dir = File(ctx.getExternalFilesDir("episodes"), "").apply { mkdirs() }
        val f = File(dir, outName)
        val sb = StringBuilder("镜号\t文件\t字幕\n")
        segs.forEachIndexed { i, s ->
            sb.append("${i + 1}\t${s.video.name}\t${s.caption}\n")
        }
        f.writeText(sb.toString())
        return f
    }
}
