package com.powerz.km003c.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.powerz.km003c.model.RecordedSample
import com.powerz.km003c.model.SavedSession
import com.powerz.km003c.model.SavedSessionSummary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Handles persistent storage of recorded sessions, high-resolution dark-theme PNG graph rendering,
 * direct saving to Gallery/Downloads via MediaStore, and FileProvider sharing (PNG / CSV / JSON).
 */
class SessionExporter(private val context: Context) {

    // Static Barlow cuts render reliably on Canvas (variable Montserrat falls back to its thinnest instance).
    private val wordsBold: Typeface by lazy {
        runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, com.powerz.km003c.R.font.barlow_semibold) }
            .getOrNull() ?: Typeface.DEFAULT_BOLD
    }
    private val numbersBold: Typeface by lazy {
        runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, com.powerz.km003c.R.font.barlow_bold) }
            .getOrNull() ?: Typeface.MONOSPACE
    }
    private val numbersMedium: Typeface by lazy {
        runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, com.powerz.km003c.R.font.barlow_medium) }
            .getOrNull() ?: Typeface.MONOSPACE
    }


    private val sessionsDir: File
        get() = File(context.filesDir, "sessions").apply { mkdirs() }

    private val shareCacheDir: File
        get() = File(context.cacheDir, "exports").apply { mkdirs() }

    // ---- Internal Session Archive ----

    fun saveSessionLocally(session: SavedSession) {
        val metaFile = File(sessionsDir, "${session.summary.id}.meta.json")
        val csvFile = File(sessionsDir, "${session.summary.id}.csv")

        val meta = JSONObject().apply {
            put("id", session.summary.id)
            put("name", session.summary.name)
            put("createdAtMs", session.summary.createdAtMs)
            put("durationSec", session.summary.durationSec)
            put("intervalMs", session.summary.intervalMs)
            put("sampleCount", session.summary.sampleCount)
            put("capacityMah", session.summary.capacityMah)
            put("energyMwh", session.summary.energyMwh)
            put("maxVbusV", session.summary.maxVbusV)
            put("maxIbusA", session.summary.maxIbusA)
            put("maxPowerW", session.summary.maxPowerW)
            put("avgPowerW", session.summary.avgPowerW)
            put("stopReason", session.summary.stopReason)
        }
        metaFile.writeText(meta.toString(2), Charsets.UTF_8)
        csvFile.writeText(buildCsvContent(session.samples), Charsets.UTF_8)
    }

    fun listSavedSessions(): List<SavedSessionSummary> {
        val files = sessionsDir.listFiles { f -> f.name.endsWith(".meta.json") } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching {
                val obj = JSONObject(file.readText(Charsets.UTF_8))
                SavedSessionSummary(
                    id = obj.getString("id"),
                    name = obj.optString("name", obj.getString("id")),
                    createdAtMs = obj.optLong("createdAtMs", file.lastModified()),
                    durationSec = obj.optDouble("durationSec", 0.0),
                    intervalMs = obj.optLong("intervalMs", 200L),
                    sampleCount = obj.optInt("sampleCount", 0),
                    capacityMah = obj.optDouble("capacityMah", 0.0),
                    energyMwh = obj.optDouble("energyMwh", 0.0),
                    maxVbusV = obj.optDouble("maxVbusV", 0.0),
                    maxIbusA = obj.optDouble("maxIbusA", 0.0),
                    maxPowerW = obj.optDouble("maxPowerW", 0.0),
                    avgPowerW = obj.optDouble("avgPowerW", 0.0),
                    stopReason = obj.optString("stopReason", "완료")
                )
            }.getOrNull()
        }.sortedByDescending { it.createdAtMs }
    }

    fun loadSession(id: String): SavedSession? {
        val summary = listSavedSessions().firstOrNull { it.id == id } ?: return null
        val csvFile = File(sessionsDir, "$id.csv")
        if (!csvFile.exists()) return SavedSession(summary, emptyList())

        val samples = mutableListOf<RecordedSample>()
        csvFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).forEach { line ->
                val parts = line.split(",")
                if (parts.size >= 10) {
                    runCatching {
                        samples.add(
                            RecordedSample(
                                elapsedSec = parts[0].toDouble(),
                                timestampMs = parts[1].toLong(),
                                vbusV = parts[2].toDouble(),
                                ibusA = parts[3].toDouble(),
                                powerW = parts[4].toDouble(),
                                dpV = parts[5].toDouble(),
                                dmV = parts[6].toDouble(),
                                cc1V = parts[7].toDouble(),
                                cc2V = parts[8].toDouble(),
                                tempC = parts[9].toDouble()
                            )
                        )
                    }
                }
            }
        }
        return SavedSession(summary, samples)
    }

    fun deleteSession(id: String) {
        File(sessionsDir, "$id.meta.json").delete()
        File(sessionsDir, "$id.csv").delete()
    }

    // ---- CSV / JSON Content Builders ----

    fun buildCsvContent(samples: List<RecordedSample>): String {
        val sb = StringBuilder(samples.size * 64 + 128)
        sb.append("elapsed_s,timestamp_ms,vbus_V,ibus_A,power_W,dp_V,dm_V,cc1_V,cc2_V,temp_C\n")
        for (s in samples) {
            sb.append(
                String.format(
                    Locale.US,
                    "%.3f,%d,%.5f,%.5f,%.5f,%.4f,%.4f,%.4f,%.4f,%.2f\n",
                    s.elapsedSec, s.timestampMs, s.vbusV, s.ibusA, s.powerW,
                    s.dpV, s.dmV, s.cc1V, s.cc2V, s.tempC
                )
            )
        }
        return sb.toString()
    }

    fun buildJsonContent(session: SavedSession): String {
        val root = JSONObject()
        root.put("name", session.summary.name)
        root.put("createdAtMs", session.summary.createdAtMs)
        root.put("durationSec", session.summary.durationSec)
        root.put("intervalMs", session.summary.intervalMs)
        root.put("sampleCount", session.summary.sampleCount)
        root.put("capacityMah", session.summary.capacityMah)
        root.put("energyMwh", session.summary.energyMwh)
        root.put("maxVbusV", session.summary.maxVbusV)
        root.put("maxIbusA", session.summary.maxIbusA)
        root.put("maxPowerW", session.summary.maxPowerW)
        root.put("avgPowerW", session.summary.avgPowerW)
        root.put("stopReason", session.summary.stopReason)

        val arr = JSONArray()
        for (s in session.samples) {
            arr.put(
                JSONObject().apply {
                    put("t", s.elapsedSec)
                    put("ts", s.timestampMs)
                    put("v", s.vbusV)
                    put("i", s.ibusA)
                    put("w", s.powerW)
                    put("dp", s.dpV)
                    put("dm", s.dmV)
                    put("cc1", s.cc1V)
                    put("cc2", s.cc2V)
                    put("temp", s.tempC)
                }
            )
        }
        root.put("samples", arr)
        return root.toString(2)
    }

    // ---- Save directly to Android MediaStore (Pictures / Downloads) ----

    fun saveGraphPngToGallery(
        session: SavedSession,
        showV: Boolean = true,
        showI: Boolean = true,
        showW: Boolean = true
    ): Result<String> = runCatching {
        val bitmap = renderGraphBitmap(session, showV, showI, showW)
        val safeName = sanitizeFilename(session.summary.name)
        val fileName = "${safeName}_${timestampTag()}.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/POWER-Z")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("갤러리 이미지 생성 실패")
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            bitmap.recycle()
            "Pictures/POWER-Z/$fileName"
        } else {
            val outFile = File(shareCacheDir, fileName)
            FileOutputStream(outFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            outFile.absolutePath
        }
    }

    fun saveCsvToDownloads(session: SavedSession): Result<String> =
        saveTextToDownloads(
            fileName = "${sanitizeFilename(session.summary.name)}_${timestampTag()}.csv",
            mimeType = "text/csv",
            content = buildCsvContent(session.samples)
        )

    fun saveJsonToDownloads(session: SavedSession): Result<String> =
        saveTextToDownloads(
            fileName = "${sanitizeFilename(session.summary.name)}_${timestampTag()}.json",
            mimeType = "application/json",
            content = buildJsonContent(session)
        )

    private fun saveTextToDownloads(fileName: String, mimeType: String, content: String): Result<String> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/POWER-Z")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Downloads 파일 생성 실패")
            resolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Download/POWER-Z/$fileName"
        } else {
            val outFile = File(shareCacheDir, fileName)
            outFile.writeText(content, Charsets.UTF_8)
            outFile.absolutePath
        }
    }

    // ---- Share Intents (FileProvider) ----

    fun createPngShareIntent(
        session: SavedSession,
        showV: Boolean = true,
        showI: Boolean = true,
        showW: Boolean = true
    ): Intent {
        val bitmap = renderGraphBitmap(session, showV, showI, showW)
        val outFile = File(shareCacheDir, "${sanitizeFilename(session.summary.name)}_${timestampTag()}.png")
        FileOutputStream(outFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return buildShareIntent(outFile, "image/png", "POWER-Z 그래프: ${session.summary.name}")
    }

    fun createCsvShareIntent(session: SavedSession): Intent {
        val outFile = File(shareCacheDir, "${sanitizeFilename(session.summary.name)}_${timestampTag()}.csv")
        outFile.writeText(buildCsvContent(session.samples), Charsets.UTF_8)
        return buildShareIntent(outFile, "text/csv", "POWER-Z CSV: ${session.summary.name}")
    }

    fun createJsonShareIntent(session: SavedSession): Intent {
        val outFile = File(shareCacheDir, "${sanitizeFilename(session.summary.name)}_${timestampTag()}.json")
        outFile.writeText(buildJsonContent(session), Charsets.UTF_8)
        return buildShareIntent(outFile, "application/json", "POWER-Z JSON: ${session.summary.name}")
    }

    private fun buildShareIntent(file: File, mimeType: String, subject: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ---- High-Resolution Laboratory Chart PNG Renderer (1600x920) ----

    fun renderGraphBitmap(
        session: SavedSession,
        showV: Boolean = true,
        showI: Boolean = true,
        showW: Boolean = true
    ): Bitmap {
        val width = 1600
        val height = 920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.parseColor("#283339"),
                Color.parseColor("#101518"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFFFF")
            textSize = 38f
            typeface = wordsBold
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9AA7AF")
            textSize = 22f
        }
        val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFFFF")
            textSize = 22f
            typeface = numbersMedium
        }

        // Header
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(session.summary.createdAtMs))
        val intervalStr = if (session.summary.intervalMs < 1000) "${session.summary.intervalMs}ms" else "${session.summary.intervalMs / 1000.0}s"
        canvas.drawText("POWER-Z  ·  ${session.summary.name}", 56f, 68f, titlePaint)
        canvas.drawText(
            "$dateStr  |  간격 $intervalStr  |  ${session.summary.sampleCount} pts  |  종료: ${session.summary.stopReason}",
            56f, 104f, subPaint
        )

        // Summary metric cards row
        val cardTop = 128f
        val cardBottom = 216f
        val cardGap = 18f
        val cardCount = 5
        val cardW = (width - 112f - cardGap * (cardCount - 1)) / cardCount
        val cards = listOf(
            Triple("MAX VBUS", String.format(Locale.US, "%.3f V", session.summary.maxVbusV), Color.parseColor("#FFDE00")),
            Triple("MAX IBUS", String.format(Locale.US, "%.3f A", session.summary.maxIbusA), Color.parseColor("#0093E7")),
            Triple("MAX / AVG W", String.format(Locale.US, "%.1f / %.1f W", session.summary.maxPowerW, session.summary.avgPowerW), Color.parseColor("#FF7A2F")),
            Triple("CAPACITY", String.format(Locale.US, "%.1f mAh", session.summary.capacityMah), Color.parseColor("#00F19F")),
            Triple("ENERGY / TIME", String.format(Locale.US, "%.2f Wh • %.0fs", session.summary.energyMwh / 1000.0, session.summary.durationSec), Color.parseColor("#00F19F"))
        )

        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A2227") }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A353C")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val cardLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9AA7AF")
            textSize = 18f
            typeface = wordsBold
            letterSpacing = 0.1f
        }
        val cardValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f
            typeface = numbersBold
        }

        cards.forEachIndexed { i, (label, value, color) ->
            val left = 56f + i * (cardW + cardGap)
            val rect = RectF(left, cardTop, left + cardW, cardBottom)
            canvas.drawRoundRect(rect, 16f, 16f, cardBgPaint)
            canvas.drawText(label, left + 18f, cardTop + 32f, cardLabelPaint)
            cardValuePaint.color = color
            canvas.drawText(value, left + 18f, cardTop + 70f, cardValuePaint)
        }

        // Chart frame
        val chartLeft = 100f
        val chartTop = 250f
        val chartRight = width - 56f
        val chartBottom = height - 84f
        val chartW = chartRight - chartLeft
        val chartH = chartBottom - chartTop

        val chartRect = RectF(chartLeft, chartTop, chartRight, chartBottom)
        canvas.drawRoundRect(chartRect, 16f, 16f, cardBgPaint)

        val samples = session.samples
        if (samples.size >= 2) {
            val maxV = (samples.maxOf { it.vbusV } * 1.15).coerceAtLeast(6.0)
            val maxI = (samples.maxOf { abs(it.ibusA) } * 1.15).coerceAtLeast(1.0)
            val maxW = (samples.maxOf { it.powerW } * 1.15).coerceAtLeast(5.0)
            val minT = samples.first().elapsedSec
            val maxT = samples.last().elapsedSec
            val spanT = (maxT - minT).coerceAtLeast(0.1)

            // Dashed Grid + Labels
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2A353C")
                strokeWidth = 1.5f
            }
            val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#5E6B73")
                textSize = 18f
                typeface = numbersMedium
            }

            for (step in 0..4) {
                val frac = step / 4f
                val y = chartBottom - frac * chartH
                canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
                val vTick = maxV * frac
                canvas.drawText(String.format(Locale.US, "%4.1fV", vTick), 24f, y + 6f, axisPaint)
            }
            for (step in 0..5) {
                val frac = step / 5f
                val x = chartLeft + frac * chartW
                val tVal = minT + spanT * frac
                canvas.drawText(String.format(Locale.US, "%.1fs", tVal), x - 24f, chartBottom + 32f, axisPaint)
            }

            fun drawChannel(colorHex: String, fillAlpha: Int, maxVal: Double, selector: (RecordedSample) -> Double) {
                val color = Color.parseColor(colorHex)
                val linePath = Path()
                val fillPath = Path()

                samples.forEachIndexed { idx, s ->
                    val x = chartLeft + (((s.elapsedSec - minT) / spanT) * chartW).toFloat().coerceIn(0f, chartW)
                    val y = chartBottom - ((selector(s) / maxVal) * chartH).toFloat().coerceIn(0f, chartH)
                    if (idx == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, chartBottom)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                val lastX = chartLeft + (((samples.last().elapsedSec - minT) / spanT) * chartW).toFloat().coerceIn(0f, chartW)
                fillPath.lineTo(lastX, chartBottom)
                fillPath.close()

                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, chartTop, 0f, chartBottom,
                        Color.argb(fillAlpha, Color.red(color), Color.green(color), Color.blue(color)),
                        Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
                        Shader.TileMode.CLAMP
                    )
                    style = Paint.Style.FILL
                }
                if (fillAlpha > 0) canvas.drawPath(fillPath, fillPaint)

                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    style = Paint.Style.STROKE
                    strokeWidth = 3.8f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawPath(linePath, strokePaint)
            }

            if (showW) drawChannel("#FF7A2F", 36, maxW) { it.powerW }
            if (showI) drawChannel("#0093E7", if (showW) 0 else 36, maxI) { abs(it.ibusA) }
            if (showV) drawChannel("#FFDE00", if (showW || showI) 0 else 36, maxV) { it.vbusV }

            // Legend at bottom right
            val legendText = String.format(
                Locale.US,
                "Scale Max:  VBUS %.1fV  |  IBUS %.2fA  |  POWER %.1fW",
                maxV, maxI, maxW
            )
            canvas.drawText(legendText, chartLeft, height - 18f, monoPaint)
        } else {
            canvas.drawText("표시할 샘플 데이터가 부족합니다.", chartLeft + 40f, chartTop + chartH / 2f, titlePaint)
        }

        return bitmap
    }

    private fun sanitizeFilename(raw: String): String {
        val cleaned = raw.replace(Regex("""[^\p{L}\p{N}_\-]"""), "_").take(36)
        return cleaned.ifBlank { "powerz_session" }
    }

    private fun timestampTag(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
