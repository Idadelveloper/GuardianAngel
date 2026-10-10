package com.example.guardianangel.domain.repository

import com.example.guardianangel.domain.model.GeoPoint
import com.example.guardianangel.domain.model.SafeLocation
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing Home and trusted Safe Locations.
 *
 * Invariant: Home coordinates and safe locations are stored locally on-device
 * and never uploaded without explicit user consent.
 */
interface SafeLocationRepository {
    fun observeLocations(): Flow<List<SafeLocation>>
    fun observeHome(): Flow<SafeLocation?>
    suspend fun getHome(): SafeLocation?
    suspend fun setHome(name: String, point: GeoPoint, address: String = "", radiusMeters: Float = 80f): SafeLocation
    suspend fun addSafeLocation(location: SafeLocation)
    suspend fun updateSafeLocation(location: SafeLocation)
    suspend fun removeSafeLocation(id: String)
}
