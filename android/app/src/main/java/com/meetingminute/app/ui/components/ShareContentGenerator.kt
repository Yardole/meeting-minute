package com.meetingminute.app.ui.components

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.meetingminute.app.domain.model.TranscriptSegment
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ShareFormat(val displayName: String) {
    PLAIN_TEXT("Plain text"),
    MARKDOWN("Markdown"),
    PDF("PDF"),
    WORD("Word")
}

fun ShareFormat.mimeType(): String = when (this) {
    ShareFormat.PLAIN_TEXT -> "text/plain"
    ShareFormat.MARKDOWN -> "text/markdown"
    ShareFormat.PDF -> "application/pdf"
    ShareFormat.WORD -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
}

fun ShareFormat.fileExtension(): String = when (this) {
    ShareFormat.PLAIN_TEXT -> "txt"
    ShareFormat.MARKDOWN -> "md"
    ShareFormat.PDF -> "pdf"
    ShareFormat.WORD -> "docx"
}

private fun formatTimeMs(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

fun buildShareContent(
    meetingTitle: String,
    meetingDate: String,
    durationMs: Int,
    summary: String?,
    segments: List<TranscriptSegment>,
    includeSummary: Boolean,
    includeTranscript: Boolean,
    format: ShareFormat
): String = when (format) {
    ShareFormat.PLAIN_TEXT -> buildPlainText(meetingTitle, meetingDate, durationMs, summary, segments, includeSummary, includeTranscript)
    ShareFormat.MARKDOWN -> buildMarkdown(meetingTitle, meetingDate, durationMs, summary, segments, includeSummary, includeTranscript)
    ShareFormat.PDF -> buildPlainText(meetingTitle, meetingDate, durationMs, summary, segments, includeSummary, includeTranscript)
    ShareFormat.WORD -> buildPlainText(meetingTitle, meetingDate, durationMs, summary, segments, includeSummary, includeTranscript)
}

// ── Plain Text ────────────────────────────────────────────────────────────────

private fun buildPlainText(
    title: String,
    date: String,
    durationMs: Int,
    summary: String?,
    segments: List<TranscriptSegment>,
    includeSummary: Boolean,
    includeTranscript: Boolean
): String = buildString {
    appendLine(title)
    appendLine("$date · ${formatTimeMs(durationMs)}")
    appendLine()

    if (includeSummary && !summary.isNullOrBlank()) {
        appendLine("─".repeat(40))
        appendLine("SUMMARY")
        appendLine("─".repeat(40))
        appendLine()
        appendLine(summary.trim())
        appendLine()
    }

    if (includeTranscript && segments.isNotEmpty()) {
        appendLine("─".repeat(40))
        appendLine("TRANSCRIPT")
        appendLine("─".repeat(40))
        appendLine()
        for (segment in segments) {
            appendLine("[${formatTimeMs(segment.startTimeMs)}] ${segment.speakerName}")
            appendLine(segment.text.trim())
            appendLine()
        }
    }
}

// ── Markdown ──────────────────────────────────────────────────────────────────

private fun buildMarkdown(
    title: String,
    date: String,
    durationMs: Int,
    summary: String?,
    segments: List<TranscriptSegment>,
    includeSummary: Boolean,
    includeTranscript: Boolean
): String = buildString {
    appendLine("# $title")
    appendLine("*$date · ${formatTimeMs(durationMs)}*")
    appendLine()

    if (includeSummary && !summary.isNullOrBlank()) {
        appendLine("## Summary")
        appendLine()
        appendLine(summary.trim())
        appendLine()
    }

    if (includeTranscript && segments.isNotEmpty()) {
        appendLine("## Transcript")
        appendLine()
        for (segment in segments) {
            appendLine("- **${segment.speakerName}** `[${formatTimeMs(segment.startTimeMs)}]`")
            appendLine("  ${segment.text.trim()}")
            appendLine()
        }
    }
}

// ── PDF ───────────────────────────────────────────────────────────────────────

fun generatePdfFile(context: Context, title: String, content: String): File {
    val safeName = title.replace(Regex("[^a-zA-Z0-9 _-]"), "").replace(" ", "_")
    val dir = File(context.cacheDir, "share")
    dir.mkdirs()
    val file = File(dir, "${safeName}.pdf")

    val pdf = PdfDocument()

    // Page setup — A4 at 72 dpi
    val pageWidth = 595
    val pageHeight = 842
    val margin = 56
    val textWidth = (pageWidth - margin * 2).toFloat()

    // Text paint for body
    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        typeface = Typeface.SERIF
        color = 0xFF2A241E.toInt()
    }

    // Text paint for title
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        color = 0xFF2A241E.toInt()
    }

    // Text paint for section headers
    val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        color = 0xFF6E7A45.toInt()
    }

    val lineHeight = 36f
    val paragraphSpacing = 18f

    data class DrawLine(val text: String, val paint: TextPaint, val addSpacingAfter: Boolean = false)

    val lines = mutableListOf<DrawLine>()

    for (paragraph in content.split("\n")) {
        when {
            paragraph.startsWith("──") || paragraph.isBlank() -> {
                lines.add(DrawLine("", bodyPaint))
            }
            paragraph == "SUMMARY" || paragraph == "TRANSCRIPT" -> {
                lines.add(DrawLine(paragraph, headerPaint, addSpacingAfter = true))
            }
            else -> {
                val isFirstLine = lines.isEmpty()
                val paint = if (isFirstLine) titlePaint else bodyPaint
                // Wrap text across multiple canvas lines
                val wrapped = wrapText(paragraph, paint, textWidth)
                wrapped.forEachIndexed { i, w ->
                    lines.add(DrawLine(w, paint, addSpacingAfter = i == wrapped.lastIndex && wrapped.size > 1))
                }
                // Title gets extra spacing
                if (isFirstLine) lines.add(DrawLine("", bodyPaint))
            }
        }
    }

    // Paginate
    var currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
    var y = margin.toFloat()

    for ((index, line) in lines.withIndex()) {
        val spacingAfter = if (line.addSpacingAfter) paragraphSpacing else 0f
        val totalHeight = lineHeight + spacingAfter

        if (y + totalHeight > pageHeight - margin) {
            pdf.finishPage(currentPage)
            currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
            y = margin.toFloat()
        }

        if (line.text.isNotBlank()) {
            currentPage.canvas.drawText(line.text, margin.toFloat(), y + lineHeight - 8f, line.paint)
        }
        y += totalHeight
    }

    pdf.finishPage(currentPage)
    pdf.writeTo(FileOutputStream(file))
    pdf.close()

    return file
}

private fun wrapText(text: String, paint: TextPaint, maxWidth: Float): List<String> {
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var currentLine = StringBuilder()

    for (word in words) {
        val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
        if (paint.measureText(testLine) <= maxWidth) {
            if (currentLine.isNotEmpty()) currentLine.append(" ")
            currentLine.append(word)
        } else {
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            } else {
                // Single word is wider than maxWidth — split by character
                for (ch in word) {
                    if (paint.measureText(currentLine.toString() + ch) <= maxWidth) {
                        currentLine.append(ch)
                    } else {
                        lines.add(currentLine.toString())
                        currentLine = StringBuilder(ch.toString())
                    }
                }
            }
        }
    }
    if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
    return lines
}

// ── Word (DOCX) ───────────────────────────────────────────────────────────────

fun generateDocxFile(context: Context, title: String, content: String): File {
    val safeName = title.replace(Regex("[^a-zA-Z0-9 _-]"), "").replace(" ", "_")
    val dir = File(context.cacheDir, "share")
    dir.mkdirs()
    val file = File(dir, "${safeName}.docx")

    ZipOutputStream(FileOutputStream(file)).use { zip ->
        // [Content_Types].xml
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""".toByteArray())
        zip.closeEntry()

        // _rels/.rels
        zip.putNextEntry(ZipEntry("_rels/.rels"))
        zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""".toByteArray())
        zip.closeEntry()

        // word/_rels/document.xml.rels
        zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
        zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
</Relationships>""".toByteArray())
        zip.closeEntry()

        // word/document.xml
        zip.putNextEntry(ZipEntry("word/document.xml"))
        zip.write(buildDocxBody(title, content).toByteArray())
        zip.closeEntry()
    }

    return file
}

private fun buildDocxBody(title: String, content: String): String {
    val paragraphs = StringBuilder()

    var isFirst = true
    for (paragraph in content.split("\n")) {
        val escaped = xmlEscape(paragraph.trim())
        if (escaped.isEmpty()) {
            paragraphs.append("  <w:p><w:pPr><w:spacing w:after=\"120\"/></w:pPr></w:p>\n")
            continue
        }

        when {
            isFirst -> {
                // Title
                paragraphs.append("  <w:p><w:pPr><w:spacing w:after=\"200\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"48\"/></w:rPr><w:t xml:space=\"preserve\">$escaped</w:t></w:r></w:p>\n")
                isFirst = false
            }
            paragraph == "SUMMARY" || paragraph == "TRANSCRIPT" -> {
                // Section header
                paragraphs.append("  <w:p><w:pPr><w:spacing w:before=\"240\" w:after=\"120\"/></w:pPr><w:r><w:rPr><w:b/><w:color w:val=\"6E7A45\"/><w:sz w:val=\"32\"/></w:rPr><w:t xml:space=\"preserve\">$escaped</w:t></w:r></w:p>\n")
            }
            paragraph.startsWith("──") -> {
                // Skip separator lines
            }
            else -> {
                paragraphs.append("  <w:p><w:pPr><w:spacing w:after=\"80\"/></w:pPr><w:r><w:rPr><w:sz w:val=\"24\"/></w:rPr><w:t xml:space=\"preserve\">$escaped</w:t></w:r></w:p>\n")
            }
        }
    }

    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
$paragraphs
  </w:body>
</w:document>"""
}

private fun xmlEscape(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")