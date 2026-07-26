package com.smsai.smsfrauddetector.data.repository

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.smsai.smsfrauddetector.BuildConfig
import com.smsai.smsfrauddetector.core.common.ApiResult
import com.smsai.smsfrauddetector.core.network.ApiClient
import com.smsai.smsfrauddetector.data.local.datastore.SessionSnapshot
import com.smsai.smsfrauddetector.data.local.datastore.SessionStore
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
import com.smsai.smsfrauddetector.data.remote.dto.ForgotPasswordRequestDto
import com.smsai.smsfrauddetector.data.remote.dto.LoginRequestDto
import com.smsai.smsfrauddetector.data.remote.dto.LogoutRequestDto
import com.smsai.smsfrauddetector.data.remote.dto.ModelDto
import com.smsai.smsfrauddetector.data.remote.dto.PaginatedResponse
import com.smsai.smsfrauddetector.data.remote.dto.RegisterRequestDto
import com.smsai.smsfrauddetector.data.remote.dto.ResetPasswordRequestDto
import com.smsai.smsfrauddetector.data.remote.dto.ReportRequestDto
import com.smsai.smsfrauddetector.data.remote.dto.StatsDto
import com.smsai.smsfrauddetector.data.remote.dto.UserDto
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.IOException
import java.io.File

data class UserSession(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

class AppRepository(
    private val context: Context,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()

    private suspend fun api() = currentSession().let { session ->
        ApiClient.create(
            baseUrl = session.baseUrl ?: BuildConfig.DEFAULT_BASE_URL,
            tokenProvider = { session.accessToken },
        )
    }

    private fun error(message: String, code: Int? = null) = ApiResult.Error(message, code)

    private suspend fun <T> call(block: suspend () -> T): ApiResult<T> {
        return try {
            ApiResult.Success(block())
        } catch (io: IOException) {
            error("Network error. Please check your connection.")
        } catch (http: retrofit2.HttpException) {
            error(parseHttpError(http), http.code())
        } catch (throwable: Throwable) {
            error(throwable.message ?: "Something went wrong.")
        }
    }

    private fun parseHttpError(error: retrofit2.HttpException): String {
        return when (error.code()) {
            400 -> "Invalid request."
            401 -> "Session expired or invalid credentials."
            403 -> "You do not have permission to perform this action."
            404 -> "Requested resource was not found."
            409 -> "Conflict detected."
            422 -> "Validation error."
            500 -> "Server error. Please try again later."
            else -> error.message()
        }
    }

    suspend fun currentSession(): SessionSnapshot = sessionStore.sessionFlow.first()

    suspend fun isAuthenticated(): Boolean = !sessionStore.accessToken().isNullOrBlank()

    suspend fun login(email: String, password: String): ApiResult<UserSession> = call {
        val response = api().login(LoginRequestDto(email = email.trim(), password = password))
        sessionStore.saveSession(response)
        UserSession(response.access, response.refresh, response.user)
    }

    suspend fun requestPasswordReset(email: String): ApiResult<String> = call {
        api().forgotPassword(ForgotPasswordRequestDto(email = email.trim()))
            .getOrDefault("message", "If an account with this email exists, a password reset link has been sent.")
    }

    suspend fun resetPassword(
        uid: String,
        token: String,
        newPassword: String,
        confirmPassword: String,
    ): ApiResult<String> = call {
        api().resetPassword(
            ResetPasswordRequestDto(
                uid = uid.trim(),
                token = token.trim(),
                newPassword = newPassword,
                confirmPassword = confirmPassword,
            ),
        ).getOrDefault("message", "Password has been reset successfully.")
    }

    suspend fun register(
        username: String,
        email: String,
        password: String,
        firstName: String = "",
        lastName: String = "",
    ): ApiResult<UserSession> = call {
        val response = api().register(
            RegisterRequestDto(
                username = username.trim(),
                email = email.trim(),
                password = password,
                firstName = firstName.trim().ifBlank { null },
                lastName = lastName.trim().ifBlank { null },
            ),
        )
        val authTokens = response.tokens
        val sessionResponse = com.smsai.smsfrauddetector.data.remote.dto.AuthResponseDto(
            refresh = authTokens.refresh,
            access = authTokens.access,
            user = response.user,
        )
        sessionStore.saveSession(sessionResponse)
        UserSession(sessionResponse.access, sessionResponse.refresh, sessionResponse.user)
    }

    suspend fun logout(): ApiResult<Unit> = call {
        val refresh = sessionStore.refreshToken() ?: ""
        if (refresh.isNotBlank()) {
            api().logout(LogoutRequestDto(refresh))
        }
        sessionStore.clearSession()
    }

    suspend fun fetchCurrentUser(): ApiResult<UserDto> = call {
        val user = api().me()
        sessionStore.saveProfile(user)
        user
    }

    suspend fun updateProfile(
        username: String? = null,
        firstName: String? = null,
        lastName: String? = null,
        newPassword: String? = null,
        currentPassword: String? = null,
        profilePictureUri: Uri? = null,
    ): ApiResult<UserDto> = call {
        val fields = mutableMapOf<String, RequestBody>()
        username?.let { fields["username"] = it.toRequestBody("text/plain".toMediaType()) }
        firstName?.let { fields["first_name"] = it.toRequestBody("text/plain".toMediaType()) }
        lastName?.let { fields["last_name"] = it.toRequestBody("text/plain".toMediaType()) }
        newPassword?.let { fields["new_password"] = it.toRequestBody("text/plain".toMediaType()) }
        currentPassword?.let { fields["current_password"] = it.toRequestBody("text/plain".toMediaType()) }

        val profilePart = profilePictureUri?.let { uri ->
            val file = File(context.cacheDir, "profile_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Unable to read selected image.")
            MultipartBody.Part.createFormData(
                "profile_picture",
                file.name,
                file.asRequestBody("image/*".toMediaType()),
            )
        }

        val user = api().updateProfile(fields, profilePart)
        sessionStore.saveProfile(user)
        user
    }

    suspend fun analyze(message: String): ApiResult<AnalysisResultDto> = call {
        api().analyze(AnalyzeRequestDto(message = message.trim()))
    }

    suspend fun history(page: Int = 1, pageSize: Int = 20): ApiResult<PaginatedResponse<AnalysisResultDto>> = call {
        api().history(page = page, pageSize = pageSize, filters = emptyMap())
    }

    suspend fun deleteHistory(analysisId: Int): ApiResult<String> = call {
        api().deleteHistory(analysisId).getOrDefault("message", "Analysis history deleted successfully.")
    }

    suspend fun stats(): ApiResult<StatsDto> = call { api().stats() }

    suspend fun dashboard(): ApiResult<DashboardResponseDto> = call { api().dashboard() }

    suspend fun createReport(message: String, notes: String = "", analysisId: Int? = null): ApiResult<FraudReportDto> = call {
        api().createReport(ReportRequestDto(smsMessage = message.trim(), notes = notes.trim(), analysisId = analysisId))
    }

    suspend fun reports(page: Int = 1, pageSize: Int = 20): ApiResult<PaginatedResponse<FraudReportDto>> = call {
        api().reports(page = page, pageSize = pageSize)
    }

    suspend fun reportDashboard(): ApiResult<Map<String, Int>> = call { api().reportDashboard() }

    suspend fun datasets(page: Int = 1, pageSize: Int = 20): ApiResult<PaginatedResponse<DatasetDto>> = call {
        api().datasets(page = page, pageSize = pageSize)
    }

    suspend fun evaluation(modelId: Int? = null): ApiResult<EvaluationReportDto> = call {
        api().evaluation(modelId)
    }

    suspend fun retrain(datasetId: Int? = null, dataPath: String? = null, force: Boolean = false): ApiResult<Map<String, Any>> = call {
        val payload = mutableMapOf<String, Any>()
        datasetId?.let { payload["dataset_id"] = it }
        dataPath?.let { payload["data_path"] = it }
        payload["model_name"] = "SmsFraudTextClassifier"
        payload["version"] = "1.0.0"
        payload["force"] = force
        api().retrain(payload)
    }

    suspend fun activeModels(): ApiResult<List<ModelDto>> = call {
        loadActiveModelsSafely()
    }

    suspend fun currentActiveModel(): ApiResult<ModelDto?> = call {
        loadActiveModelsSafely().firstOrNull()
    }

    suspend fun health(): ApiResult<HealthDto> = call { api().health() }

    suspend fun importDataset(file: MultipartBody.Part, notes: String = ""): ApiResult<DatasetDto> = call {
        val fields = if (notes.isBlank()) emptyMap() else mapOf(
            "notes" to notes.toRequestBody("text/plain".toMediaType()),
        )
        api().importDataset(file = file, fields = fields)
    }

    suspend fun deleteDataset(datasetId: Int): ApiResult<String> = call {
        api().deleteDataset(datasetId).getOrDefault("message", "Dataset deleted successfully.")
    }

    suspend fun updateBaseUrl(baseUrl: String) {
        sessionStore.updateBaseUrl(baseUrl)
    }

    suspend fun setDarkMode(enabled: Boolean) {
        sessionStore.setDarkMode(enabled)
    }

    suspend fun setSmsMonitoringEnabled(enabled: Boolean) {
        sessionStore.setSmsMonitoringEnabled(enabled)
    }

    suspend fun currentBaseUrl(): String = sessionStore.baseUrl(BuildConfig.DEFAULT_BASE_URL)
    suspend fun currentDarkMode(): Boolean = sessionStore.darkMode()
    suspend fun currentSmsMonitoring(): Boolean = sessionStore.smsMonitoringEnabled()

    suspend fun users(): ApiResult<List<UserDto>> = call {
        parseUsersResponse(api().users())
    }

    suspend fun adminCreateUser(
        username: String,
        email: String,
        role: String,
        firstName: String = "",
        lastName: String = "",
        status: String = "Active",
    ): ApiResult<AdminUserCreateResponseDto> = call {
        api().adminCreateUser(
            AdminUserCreateRequestDto(
                username = username.trim(),
                email = email.trim(),
                role = role,
                firstName = firstName.trim().ifBlank { null },
                lastName = lastName.trim().ifBlank { null },
                status = status,
            ),
        )
    }

    suspend fun adminUpdateUser(
        userId: Int,
        username: String,
        firstName: String,
        lastName: String,
        role: String,
        status: String,
    ): ApiResult<AdminUserMutationResponseDto> = call {
        api().adminUpdateUser(
            userId = userId,
            request = mapOf(
                "username" to username.trim(),
                "first_name" to firstName.trim(),
                "last_name" to lastName.trim(),
                "role" to role.trim(),
                "status" to status.trim(),
            ),
        )
    }

    suspend fun adminToggleUser(userId: Int): ApiResult<AdminUserMutationResponseDto> = call {
        api().adminToggleUser(userId)
    }

    suspend fun adminDeleteUser(userId: Int): ApiResult<String> = call {
        api().adminDeleteUser(userId).getOrDefault("message", "User deleted successfully.")
    }

    private fun parseUsersResponse(body: ResponseBody): List<UserDto> {
        return try {
            val raw = body.string().trim()
            if (raw.isBlank()) return emptyList()

            val element = JsonParser.parseString(raw)
            val listType = object : TypeToken<List<UserDto>>() {}.type

            when {
                element.isJsonArray -> gson.fromJson(element, listType)
                element.isJsonObject -> {
                    val objectValue = element.asJsonObject
                    when {
                        objectValue.has("results") && objectValue["results"].isJsonArray ->
                            gson.fromJson(objectValue["results"], listType)
                        objectValue.has("users") && objectValue["users"].isJsonArray ->
                            gson.fromJson(objectValue["users"], listType)
                        objectValue.has("id") ->
                            listOf(gson.fromJson(element, UserDto::class.java))
                        else -> emptyList()
                    }
                }
                else -> emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun parseActiveModelsResponse(body: ResponseBody): List<ModelDto> {
        return try {
            val raw = body.string().trim()
            if (raw.isBlank()) return emptyList()

            val element = JsonParser.parseString(raw)
            val listType = object : TypeToken<List<ModelDto>>() {}.type

            when {
                element.isJsonArray -> gson.fromJson(element, listType)
                element.isJsonObject -> {
                    val objectValue = element.asJsonObject
                    when {
                        objectValue.has("results") && objectValue["results"].isJsonArray ->
                            gson.fromJson(objectValue["results"], listType)
                        objectValue.has("active_model") && objectValue["active_model"].isJsonObject ->
                            listOf(gson.fromJson(objectValue["active_model"], ModelDto::class.java))
                        objectValue.has("model") && objectValue["model"].isJsonObject ->
                            listOf(gson.fromJson(objectValue["model"], ModelDto::class.java))
                        objectValue.has("id") ->
                            listOf(gson.fromJson(element, ModelDto::class.java))
                        else -> emptyList()
                    }
                }
                else -> emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private suspend fun loadActiveModelsSafely(): List<ModelDto> {
        val models = parseActiveModelsResponse(api().activeModels())
        return if (models.isNotEmpty()) models else loadActiveModelFallback()
    }

    private suspend fun loadActiveModelFallback(): List<ModelDto> {
        return try {
            val activeModel = api().health().activeModel ?: return emptyList()
            listOf(activeModel.toModelDto())
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun ActiveModelDto.toModelDto(): ModelDto {
        return ModelDto(
            id = id,
            modelName = modelName,
            version = version,
            artifactPath = null,
            trainingDataPath = null,
            trainingSamples = 0,
            testSamples = 0,
            evaluationReport = null,
            accuracy = accuracy,
            precision = precision,
            recall = recall,
            f1Score = f1Score,
            trainedAt = trainedAt,
            isActive = true,
        )
    }
}
