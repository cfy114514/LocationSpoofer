package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import android.content.Context
import android.util.AtomicFile
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import java.io.File

/** Internal app storage; AtomicFile keeps the previous complete snapshot if a write fails. */
internal class RealRouteDraft(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "real-route-draft-v1"))

    fun load(): RealRouteDraftData = try {
        file.openRead().bufferedReader(Charsets.UTF_8).use { decodeDraftData(it.readText()) }
    } catch (_: java.io.FileNotFoundException) {
        RealRouteDraftData(emptyList())
    }

    fun save(draft: RealRouteDraftData) {
        if (draft.points.isEmpty()) {
            file.delete()
            check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists() && !File(file.baseFile.path + ".new").exists()) { "Draft removal failed" }
            return
        }
        val stream = file.startWrite()
        try {
            stream.write(encodeDraftData(draft).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
}

