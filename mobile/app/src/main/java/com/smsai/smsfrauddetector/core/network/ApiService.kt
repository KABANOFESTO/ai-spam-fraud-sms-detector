package com.smsai.smsfrauddetector.core.network

import com.smsai.smsfrauddetector.data.remote.dto.ActiveModelDto
import com.smsai.smsfrauddetector.data.remote.dto.AdminUserCreateRequestDto
import com.smsai.smsfrauddetector.data.remote.dto.AdminUserCreateResponseDto
import com.smsai.smsfrauddetector.data.remote.dto.AdminUserMutationResponseDto
import com.smsai.smsfrauddetector.data.remote.dto.AnalysisResultDto
import com.smsai.smsfrauddetector.data.remote.dto.AnalyzeRequestDto
import com.smsai.smsfrauddetector.data.remote.dto.AuthResponseDto
import com.smsai.smsfrauddetector.data.remote.dto.DatasetDto
import com.smsai.smsfrauddetector.data.remote.dto.DashboardResponseDto
import com.smsai.smsfrauddetector.data.remote.dto.EvaluationReportDto
import com.smsai.smsfrauddetector.data.remote.dto.FraudReportDto
import com.smsai.smsfrauddetector.data.remote.dto.HealthDto
import com.smsai.smsfrauddetector.data.remote.dto.LoginRequestDto
import com.smsai.smsfrauddetector.data.remote.dto.ForgotPasswordRequestDto
import com.smsai.smsfrauddetector.data.remote.dto.LogoutRequestDto
import com.smsai.smsfrauddetector.data.remote.dto.ModelDto
import com.smsai.smsfrauddetector.data.remote.dto.PaginatedResponse
import com.smsai.smsfrauddetector.data.remote.dto.ProfileUpdateResponseDto
import com.smsai.smsfrauddetector.data.remote.dto.ResetPasswordRequestDto
import com.smsai.smsfrauddetector.data.remote.dto.RegisterResponseDto
import com.smsai.smsfrauddetector.data.remote.dto.RegisterRequestDto
import com.smsai.smsfrauddetector.data.remote.dto.ReportRequestDto
import com.smsai.smsfrauddetector.data.remote.dto.StatsDto
import com.smsai.smsfrauddetector.data.remote.dto.UserDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Url

interface ApiService {
    @POST("api/auth/login/")
    suspend fun login(@Body request: LoginRequestDto): AuthResponseDto

    @POST("api/auth/register/")
    suspend fun register(@Body request: RegisterRequestDto): RegisterResponseDto

    @POST("api/auth/logout/")
    suspend fun logout(@Body request: LogoutRequestDto): Map<String, String>

    @POST("api/auth/forgot-password/")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequestDto): Map<String, String>

    @POST("api/auth/reset-password/")
    suspend fun resetPassword(@Body request: ResetPasswordRequestDto): Map<String, String>

    @GET("api/auth/me/")
    suspend fun me(): UserDto

    @GET("api/auth/users/")
    suspend fun users(): List<UserDto>

    @POST("api/auth/admin/users/create/")
    suspend fun adminCreateUser(@Body request: AdminUserCreateRequestDto): AdminUserCreateResponseDto

    @PATCH("api/auth/admin/users/{pk}/update/")
    suspend fun adminUpdateUser(
        @Path("pk") userId: Int,
        @Body request: Map<String, @JvmSuppressWildcards Any>,
    ): AdminUserMutationResponseDto

    @PATCH("api/auth/admin/users/{pk}/toggle-active/")
    suspend fun adminToggleUser(@Path("pk") userId: Int): AdminUserMutationResponseDto

    @DELETE("api/auth/admin/users/{pk}/delete/")
    suspend fun adminDeleteUser(@Path("pk") userId: Int): Map<String, String>

    @Multipart
    @PATCH("api/auth/update-profile/")
    suspend fun updateProfile(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part profilePicture: MultipartBody.Part? = null,
    ): UserDto

    @POST("api/analysis/analyze/")
    suspend fun analyze(@Body request: AnalyzeRequestDto): AnalysisResultDto

    @GET("api/analysis/history/")
    suspend fun history(
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
        @QueryMap(encoded = true) filters: Map<String, String>? = null,
    ): PaginatedResponse<AnalysisResultDto>

    @GET("api/analysis/stats/")
    suspend fun stats(): StatsDto

    @GET("api/analysis/dashboard/")
    suspend fun dashboard(): DashboardResponseDto

    @POST("api/reports/")
    suspend fun createReport(@Body request: ReportRequestDto): FraudReportDto

    @GET("api/reports/")
    suspend fun reports(
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
    ): PaginatedResponse<FraudReportDto>

    @GET("api/reports/dashboard/")
    suspend fun reportDashboard(): Map<String, Int>

    @Multipart
    @POST("api/analysis/admin/datasets/import/")
    suspend fun importDataset(
        @Part file: MultipartBody.Part,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody> = emptyMap(),
    ): DatasetDto

    @GET("api/analysis/admin/datasets/")
    suspend fun datasets(
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
    ): PaginatedResponse<DatasetDto>

    @GET("api/analysis/admin/evaluation/")
    suspend fun evaluation(@Query("model_id") modelId: Int? = null): EvaluationReportDto

    @POST("api/analysis/admin/retrain/")
    suspend fun retrain(@Body body: Map<String, @JvmSuppressWildcards Any>): Map<String, Any>

    @GET("api/analysis/models/")
    suspend fun activeModels(): List<ModelDto>

    @GET("api/health/")
    suspend fun health(): HealthDto
}
