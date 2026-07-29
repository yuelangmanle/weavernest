package com.zhique.runtime.capability

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeSession
import com.zhique.runtime.permission.PermissionBroker
import java.net.URI

object SystemUriPolicy {
    fun requireWebUri(raw: String): URI {
        val uri = runCatching { URI(raw) }.getOrElse { throw IllegalArgumentException("url must be valid.") }
        require(uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank()) { "Only HTTP and HTTPS URLs are allowed." }
        return uri
    }
}

class ShareCapabilityHandler(context: Context) : RuntimeCapabilityHandler {
    override val capabilityId = "share"
    private val appContext = context.applicationContext

    override fun supports(method: String) = method == "share.open"

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? {
        require(method == "share.open") { "Unsupported share method: $method" }
        val text = params["text"] as? String
        val title = params["title"] as? String ?: "分享"
        require(!text.isNullOrBlank()) { "text is required." }
        appContext.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_TITLE, title), title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return mapOf("launched" to true)
    }
}

class SystemIntentCapabilityHandler(context: Context) : RuntimeCapabilityHandler {
    override val capabilityId = "system_intents"
    private val appContext = context.applicationContext

    override fun supports(method: String) = method in setOf(
        "system.openUrl",
        "system.openMap",
        "system.appInfo",
        "system.deviceInfo"
    )

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "system.openUrl" -> launch(Intent(Intent.ACTION_VIEW, Uri.parse(SystemUriPolicy.requireWebUri(params.requiredSystemString("url")).toString())))
        "system.openMap" -> {
            val query = (params["query"] as? String).orEmpty().ifBlank {
                val latitude = params["latitude"] as? Number ?: throw IllegalArgumentException("query or latitude is required.")
                val longitude = params["longitude"] as? Number ?: throw IllegalArgumentException("longitude is required.")
                "${latitude.toDouble()},${longitude.toDouble()}"
            }
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}")))
        }
        "system.appInfo" -> mapOf("packageName" to appContext.packageName, "versionName" to versionName())
        "system.deviceInfo" -> mapOf("manufacturer" to Build.MANUFACTURER, "model" to Build.MODEL, "androidApi" to Build.VERSION.SDK_INT)
        else -> throw IllegalArgumentException("Unsupported system method: $method")
    }

    private fun launch(intent: Intent): Map<String, Boolean> {
        appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return mapOf("launched" to true)
    }

    @Suppress("DEPRECATION")
    private fun versionName(): String = if (Build.VERSION.SDK_INT >= 33) {
        appContext.packageManager.getPackageInfo(appContext.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
    } else {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.orEmpty()
}

class PhoneDialCapabilityHandler(context: Context) : RuntimeCapabilityHandler {
    override val capabilityId = "phone_dial"
    private val appContext = context.applicationContext

    override fun supports(method: String) = method == "system.dial"

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? {
        require(method == "system.dial") { "Unsupported phone method: $method" }
        val number = params.requiredSystemString("number").filter { character -> character.isDigit() || character in setOf('+', '*', '#', '-') }
        require(number.isNotBlank()) { "number is invalid." }
        appContext.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return mapOf("launched" to true)
    }
}

class CalendarCapabilityHandler(
    context: Context,
    private val permissionBroker: PermissionBroker
) : RuntimeCapabilityHandler {
    override val capabilityId = "calendar"
    private val appContext = context.applicationContext

    override fun supports(method: String) = method in setOf(
        "calendar.addEvent",
        "calendar.list",
        "calendar.pickEvent"
    )

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "calendar.addEvent" -> addEvent(params)
        "calendar.list" -> listEvents(params)
        "calendar.pickEvent" -> pickEvent()
        else -> throw IllegalArgumentException("Unsupported calendar method: $method")
    }

    private suspend fun addEvent(params: Map<String, Any?>): Map<String, Long> {
        permissionBroker.ensure(capabilityId)
        val start = params["startMillis"] as? Number ?: throw IllegalArgumentException("startMillis is required.")
        val end = params["endMillis"] as? Number ?: (start.toLong() + 3_600_000L)
        val calendarId = defaultCalendarId()
        val uri = requireNotNull(appContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, params.requiredSystemString("title"))
            put(CalendarContract.Events.DTSTART, start.toLong())
            put(CalendarContract.Events.DTEND, end.toLong())
            put(CalendarContract.Events.EVENT_TIMEZONE, params["timeZone"] as? String ?: java.util.TimeZone.getDefault().id)
        })) { "Android could not create the calendar event." }
        return mapOf("id" to uri.lastPathSegment!!.toLong())
    }

    private suspend fun listEvents(params: Map<String, Any?>): Map<String, Any?> {
        permissionBroker.ensure(capabilityId)
        val start = (params["startMillis"] as? Number)?.toLong() ?: System.currentTimeMillis() - 86_400_000L
        val end = (params["endMillis"] as? Number)?.toLong() ?: System.currentTimeMillis() + 30L * 86_400_000L
        val events = appContext.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND),
            "${CalendarContract.Events.DTSTART} BETWEEN ? AND ?",
            arrayOf(start.toString(), end.toString()),
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < 100) {
                    add(mapOf("id" to cursor.getLong(0), "title" to cursor.getString(1), "startMillis" to cursor.getLong(2), "endMillis" to cursor.getLong(3)))
                }
            }
        }.orEmpty()
        return mapOf("events" to events)
    }

    private fun pickEvent(): Map<String, Boolean> {
        appContext.startActivity(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return mapOf("launched" to true)
    }

    private fun defaultCalendarId(): Long = appContext.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(CalendarContract.Calendars._ID),
        "${CalendarContract.Calendars.VISIBLE} = 1",
        null,
        null
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
        ?: throw IllegalStateException("No writable calendar is available on this device.")
}

private fun Map<String, Any?>.requiredSystemString(key: String): String = this[key] as? String
    ?: throw IllegalArgumentException("$key is required.")
