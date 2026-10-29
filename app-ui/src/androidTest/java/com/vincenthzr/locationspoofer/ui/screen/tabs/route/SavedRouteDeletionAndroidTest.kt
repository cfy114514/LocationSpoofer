package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vincenthzr.locationspoofer.data.db.AppDatabase
import com.vincenthzr.locationspoofer.data.db.SavedRouteEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SavedRouteDeletionAndroidTest {
    @Test fun deletingOlderSameNamedRouteKeepsNewerRoute() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.getDatabase(context).savedRouteDao()
        val name = "deletion-test-${UUID.randomUUID()}"
        val older = SavedRouteEntity(name = name, pointsJson = "[{\"lat\":31.2,\"lng\":121.4}]", timestamp = 1)
        val newer = SavedRouteEntity(name = name, pointsJson = "[{\"lat\":32.2,\"lng\":121.4}]", timestamp = 2)
        try {
            dao.insertSavedRoute(older)
            dao.insertSavedRoute(newer)
            val both = dao.getAllSavedRoutesList().filter { it.name == name }
            assertEquals(2, both.size)
            val olderId = both.single { it.timestamp == 1L }.id
            val newerId = both.single { it.timestamp == 2L }.id
            dao.deleteSavedRouteById(olderId)
            val remaining = dao.getAllSavedRoutesList().filter { it.name == name }
            assertEquals(1, remaining.size)
            assertEquals(newerId, remaining.single().id)
            assertTrue(remaining.single().pointsJson.contains("32.2"))
        } finally {
            dao.getAllSavedRoutesList().filter { it.name == name }.forEach { dao.deleteSavedRouteById(it.id) }
        }
    }
}
