package app.planner

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.planner.data.settings.SettingsRepository
import app.planner.domain.model.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    @Test fun themeDefaultsToSystemAndPersistsChanges() = runBlocking {
        val file = File.createTempFile("settings_test", ".preferences_pb").also { it.delete() }
        val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) { file })
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.themeMode.first())
    }
}
