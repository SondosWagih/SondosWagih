package app.planner.domain.repository

interface DeviceIdProvider {
    /** Returns the stable local device identifier, generating and persisting it on first use. */
    suspend fun getOrCreate(): String
}
