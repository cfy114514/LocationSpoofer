package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import android.content.Context
import android.util.AtomicFile
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import java.io.File

/** Internal app storage; AtomicFile keeps the previous complete snapshot if a write fails. */
internal class RealRouteDraft(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "real-route-draft-v1"))

    fun load(): List<RoutePoint> = try {
        file.openRead().bufferedReader(Charsets.UTF_8).use { decodeDraft(it.readText()) }
    } catch (_: java.io.FileNotFoundException) {
        emptyList()
    }

    fun save(points: List<RoutePoint>) {
        if (points.isEmpty()) { file.delete(); return }
        val stream = file.startWrite()
        try {
            stream.write(encodeDraft(points).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
}

