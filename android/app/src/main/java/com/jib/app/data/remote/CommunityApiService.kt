package com.jib.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

// --- Check-ins ---

data class CheckInDto(
    val id: String,
    val stationId: String,
    val userId: String,
    val rating: Int?,
    val comment: String?,
    val createdAt: String,
)

data class CheckInsResponse(@SerializedName("checkIns") val checkIns: List<CheckInDto>)

data class CreateCheckInRequest(
    val idempotencyKey: String,
    val stationId: String,
    val rating: Int? = null,
    val comment: String? = null,
)

// --- Reviews ---

data class ReviewDto(
    val id: String,
    val stationId: String,
    val userId: String,
    val displayName: String?,
    val rating: Int,
    val body: String?,
    val createdAt: String,
)

data class ReviewsResponse(val reviews: List<ReviewDto>)

data class CreateReviewRequest(
    val idempotencyKey: String,
    val stationId: String,
    val rating: Int,
    val body: String? = null,
)

// --- Photos ---

data class PhotoDto(
    val id: String,
    val stationId: String,
    val userId: String,
    val storageUrl: String,
    val createdAt: String,
)

data class PhotosResponse(val photos: List<PhotoDto>)

data class CreatePhotoRequest(
    val idempotencyKey: String,
    val stationId: String,
    val storageUrl: String,
)

// --- Reports ---

data class CreateReportRequest(
    val idempotencyKey: String,
    val stationId: String,
    val kind: String, // BROKEN | CLOSED | INCORRECT_INFO
    val notes: String? = null,
)

data class ReportDto(
    val id: String,
    val stationId: String,
    val userId: String,
    val kind: String,
    val notes: String?,
    val createdAt: String,
)

// --- Users ---

data class UserDto(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
)

data class UpdateUserRequest(val displayName: String)

data class UserActivityItem(
    val id: String,
    val stationId: String,
    val stationName: String?,
    val createdAt: String,
    // For reviews/check-ins
    val rating: Int? = null,
    val comment: String? = null,
    val body: String? = null,
    // For photos
    val storageUrl: String? = null,
)

data class UserActivityResponse(
    val stations: List<UserActivityItem> = emptyList(),
    val checkIns: List<UserActivityItem> = emptyList(),
    val reviews: List<UserActivityItem> = emptyList(),
    val photos: List<UserActivityItem> = emptyList(),
)

interface CommunityApiService {

    // Check-ins
    @POST("checkins")
    suspend fun createCheckIn(@Body body: CreateCheckInRequest): CheckInDto

    @GET("checkins")
    suspend fun getCheckIns(@Query("stationId") stationId: String): CheckInsResponse

    // Reviews
    @POST("reviews")
    suspend fun createReview(@Body body: CreateReviewRequest): ReviewDto

    @GET("reviews")
    suspend fun getReviews(@Query("stationId") stationId: String): ReviewsResponse

    // Photos
    @POST("photos")
    suspend fun createPhoto(@Body body: CreatePhotoRequest): PhotoDto

    @GET("photos")
    suspend fun getPhotos(@Query("stationId") stationId: String): PhotosResponse

    // Reports
    @POST("reports")
    suspend fun createReport(@Body body: CreateReportRequest): ReportDto

    // Users
    @GET("users/me")
    suspend fun getCurrentUser(): UserDto

    @PATCH("users/me")
    suspend fun updateCurrentUser(@Body body: UpdateUserRequest): UserDto

    @GET("users/me/activity")
    suspend fun getCurrentUserActivity(): UserActivityResponse
}
