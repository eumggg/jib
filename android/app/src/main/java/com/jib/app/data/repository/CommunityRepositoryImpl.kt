package com.jib.app.data.repository

import com.jib.app.data.remote.CheckInDto
import com.jib.app.data.remote.CommunityApiService
import com.jib.app.data.remote.CreateCheckInRequest
import com.jib.app.data.remote.CreatePhotoRequest
import com.jib.app.data.remote.CreateReportRequest
import com.jib.app.data.remote.CreateReviewRequest
import com.jib.app.data.remote.PhotoDto
import com.jib.app.data.remote.ReportDto
import com.jib.app.data.remote.ReviewDto
import com.jib.app.data.remote.UpdateUserRequest
import com.jib.app.data.remote.UserActivityResponse
import com.jib.app.data.remote.UserDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunityRepositoryImpl @Inject constructor(
    private val api: CommunityApiService,
) : CommunityRepository {

    override suspend fun createCheckIn(
        idempotencyKey: String,
        stationId: String,
        comment: String?,
    ): Result<CheckInDto> = runCatching {
        api.createCheckIn(
            CreateCheckInRequest(
                idempotencyKey = idempotencyKey,
                stationId = stationId,
                comment = comment,
            )
        )
    }

    override suspend fun listCheckIns(stationId: String): Result<List<CheckInDto>> =
        runCatching { api.getCheckIns(stationId).checkIns }

    override suspend fun createReview(
        idempotencyKey: String,
        stationId: String,
        rating: Int,
        body: String?,
    ): Result<ReviewDto> = runCatching {
        api.createReview(
            CreateReviewRequest(
                idempotencyKey = idempotencyKey,
                stationId = stationId,
                rating = rating,
                body = body,
            )
        )
    }

    override suspend fun listReviews(stationId: String): Result<List<ReviewDto>> =
        runCatching { api.getReviews(stationId).reviews }

    override suspend fun createPhoto(
        idempotencyKey: String,
        stationId: String,
        storageUrl: String,
    ): Result<PhotoDto> = runCatching {
        api.createPhoto(
            CreatePhotoRequest(
                idempotencyKey = idempotencyKey,
                stationId = stationId,
                storageUrl = storageUrl,
            )
        )
    }

    override suspend fun listPhotos(stationId: String): Result<List<PhotoDto>> =
        runCatching { api.getPhotos(stationId).photos }

    override suspend fun createReport(
        idempotencyKey: String,
        stationId: String,
        kind: String,
        notes: String?,
    ): Result<ReportDto> = runCatching {
        api.createReport(
            CreateReportRequest(
                idempotencyKey = idempotencyKey,
                stationId = stationId,
                kind = kind,
                notes = notes,
            )
        )
    }

    override suspend fun getCurrentUser(): Result<UserDto> =
        runCatching { api.getCurrentUser() }

    override suspend fun updateDisplayName(displayName: String): Result<UserDto> =
        runCatching { api.updateCurrentUser(UpdateUserRequest(displayName)) }

    override suspend fun getCurrentUserActivity(): Result<UserActivityResponse> =
        runCatching { api.getCurrentUserActivity() }
}
