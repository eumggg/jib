package com.jib.app.data.repository

import com.jib.app.data.remote.CheckInDto
import com.jib.app.data.remote.PhotoDto
import com.jib.app.data.remote.ReportDto
import com.jib.app.data.remote.ReviewDto
import com.jib.app.data.remote.UserActivityResponse
import com.jib.app.data.remote.UserDto

interface CommunityRepository {
    // Check-ins
    suspend fun createCheckIn(idempotencyKey: String, stationId: String, comment: String?): Result<CheckInDto>
    suspend fun listCheckIns(stationId: String): Result<List<CheckInDto>>

    // Reviews
    suspend fun createReview(idempotencyKey: String, stationId: String, rating: Int, body: String?): Result<ReviewDto>
    suspend fun listReviews(stationId: String): Result<List<ReviewDto>>

    // Photos
    suspend fun createPhoto(idempotencyKey: String, stationId: String, storageUrl: String): Result<PhotoDto>
    suspend fun listPhotos(stationId: String): Result<List<PhotoDto>>

    // Reports
    suspend fun createReport(
        idempotencyKey: String,
        stationId: String,
        kind: String,
        notes: String?,
    ): Result<ReportDto>

    // Users
    suspend fun getCurrentUser(): Result<UserDto>
    suspend fun updateDisplayName(displayName: String): Result<UserDto>
    suspend fun getCurrentUserActivity(): Result<UserActivityResponse>
}
