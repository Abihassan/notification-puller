package com.notificationpuller.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.notificationpuller.database.NotificationEntity
import com.notificationpuller.database.NotificationRepository
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

class NotificationExportManager(private val context: Context) {

    companion object {
        const val FORMAT_CSV = "csv"
        const val FORMAT_XLSX = "xlsx"
        const val FORMAT_JSON = "json"
    }

    suspend fun exportAndShare(
        format: String,
        repository: NotificationRepository,
        ids: List<String> = emptyList(),
        search: String = "",
        packageName: String = "",
        unreadOnly: Boolean = false,
        activeOnly: Boolean = false,
        fromTime: Long = 0L,
        toTime: Long = 0L
    ): String {
        val normalized = format.trim().lowercase(Locale.US)
        require(normalized in setOf(FORMAT_CSV, FORMAT_XLSX, FORMAT_JSON)) {
            "Unsupported export format: $format"
        }

        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val exportDir = File(baseDir, "exports")
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw IllegalStateException("Unable to create export directory")
        }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(exportDir, "notification_export_$stamp.$normalized")

        val items = if (ids.isNotEmpty()) {
            repository.getByIds(ids)
        } else {
            repository.getAllFiltered(search, packageName, unreadOnly, activeOnly, fromTime, toTime)
        }

        when (normalized) {
            FORMAT_CSV -> writeCsv(file, items)
            FORMAT_JSON -> writeJson(file, items)
            FORMAT_XLSX -> writeXlsx(file, items)
        }

        share(file, normalized, items.size)
        return file.name
    }

    private fun writeCsv(file: File, items: List<NotificationEntity>) {
        file.outputStream().use { output ->
            BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8)).use { writer ->
                writer.write("Date,Time,App Name,App Package,Title,Message,Sub Text,Category,Status,Active,Read,Ongoing,Group Key,Posted At,Updated At,Removed At,Notification Key")
                writer.newLine()
                items.forEach { n ->
                    val values = listOf(
                        datePart(n.timestamp), timePart(n.timestamp), appName(n.packageName), n.packageName, n.title, n.text,
                        n.subText, n.category, n.status, n.isActive.toString(), n.isRead.toString(),
                        n.isOngoing.toString(), n.groupKey, n.timestamp.toString(), n.updatedAt.toString(),
                        n.removedAt?.toString() ?: "", n.notificationKey
                    )
                    writer.write(values.joinToString(",") { csvEscape(it) })
                    writer.newLine()
                }
            }
        }
    }

    private fun writeJson(file: File, items: List<NotificationEntity>) {
        val root = JSONArray()
        items.forEach { n ->
            root.put(JSONObject().apply {
                put("id", n.id)
                put("notificationKey", n.notificationKey)
                put("appName", appName(n.packageName))
                put("packageName", n.packageName)
                put("title", n.title)
                put("text", n.text)
                put("subText", n.subText)
                put("category", n.category)
                put("timestamp", n.timestamp)
                put("createdAt", n.createdAt)
                put("updatedAt", n.updatedAt)
                put("removedAt", n.removedAt ?: JSONObject.NULL)
                put("isOngoing", n.isOngoing)
                put("isActive", n.isActive)
                put("isRead", n.isRead)
                put("status", n.status)
                put("groupKey", n.groupKey)
                put("isGroupSummary", n.isGroupSummary)
            })
        }
        file.writeText(root.toString(2), StandardCharsets.UTF_8)
    }

    private fun writeXlsx(file: File, items: List<NotificationEntity>) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            putText(zip, "[Content_Types].xml", contentTypesXml())
            putText(zip, "_rels/.rels", rootRelsXml())
            putText(zip, "xl/workbook.xml", workbookXml())
            putText(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml())
            putText(zip, "xl/styles.xml", stylesXml())
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>".toByteArray(StandardCharsets.UTF_8))
            zip.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>".toByteArray(StandardCharsets.UTF_8))
            writeXlsxRow(zip, 1, headers(), true)
            items.forEachIndexed { index, n -> writeXlsxRow(zip, index + 2, rowValues(n), false) }
            zip.write("</sheetData></worksheet>".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun headers() = listOf(
        "Date", "Time", "App Name", "App Package", "Title", "Message", "Sub Text", "Category",
        "Status", "Active", "Read", "Ongoing", "Group Key", "Posted At", "Updated At",
        "Removed At", "Notification Key"
    )

    private fun rowValues(n: NotificationEntity) = listOf(
        datePart(n.timestamp), timePart(n.timestamp), appName(n.packageName), n.packageName, n.title, n.text, n.subText,
        n.category, n.status, n.isActive.toString(), n.isRead.toString(), n.isOngoing.toString(),
        n.groupKey, n.timestamp.toString(), n.updatedAt.toString(), n.removedAt?.toString() ?: "", n.notificationKey
    )

    private fun writeXlsxRow(zip: ZipOutputStream, row: Int, values: List<String>, header: Boolean) {
        zip.write("<row r=\"$row\">".toByteArray(StandardCharsets.UTF_8))
        values.forEachIndexed { index, value ->
            val ref = columnName(index + 1) + row
            val style = if (header) " s=\"1\"" else ""
            zip.write("<c r=\"$ref\" t=\"inlineStr\"$style><is><t xml:space=\"preserve\">${xmlEscape(value)}</t></is></c>".toByteArray(StandardCharsets.UTF_8))
        }
        zip.write("</row>".toByteArray(StandardCharsets.UTF_8))
    }

    private fun share(file: File, format: String, count: Int) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        val mime = when (format) {
            FORMAT_CSV -> "text/csv"
            FORMAT_XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> "application/json"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Notification export: $count notifications")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Export $count notifications")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun putText(zip: ZipOutputStream, path: String, text: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(text.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun appName(packageName: String): String = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        packageName
    }

    private fun datePart(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(time))
    private fun timePart(time: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(time))
    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ")}\""
    private fun xmlEscape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun columnName(number: Int): String {
        var n = number
        val result = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            result.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return result.reverse().toString()
    }

    private fun contentTypesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
    private fun rootRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private fun workbookXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Notifications" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    private fun workbookRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
    private fun stylesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="1"><fill><patternFill patternType="none"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" applyFont="1"/></cellXfs></styleSheet>"""
}
