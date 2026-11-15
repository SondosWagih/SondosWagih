package app.planner.data.repository

import app.planner.data.local.dao.AppSettingDao
import app.planner.data.local.entity.AppSettingEntity
import app.planner.domain.repository.DeviceIdProvider
import java.util.UUID

class RoomDeviceIdProvider(private val dao: AppSettingDao) : DeviceIdProvider {
    override suspend fun getOrCreate(): String {
        dao.get(KEY)?.let { return it }
        // IGNORE makes this race-safe: the first writer wins, everyone then reads the same value.
        dao.putIfAbsent(AppSettingEntity(KEY, UUID.randomUUID().toString(), System.currentTimeMillis()))
        return checkNotNull(dao.get(KEY))
    }

    private companion object { const val KEY = "device_id" }
}
