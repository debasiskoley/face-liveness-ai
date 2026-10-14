package com.example.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.ApiClient
import com.example.data.remote.GateDto
import com.example.data.remote.RegisterDeviceRequest
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class RegisteredGate(val gateId: Int, val gateName: String)

data class DeviceRegistrationUiState(
    val deviceId: String = "",
    val deviceName: String = "",
    val gates: List<GateDto> = emptyList(),
    val selectedGateId: Int? = null,
    val isLoadingGates: Boolean = false,
    val isRegistering: Boolean = false,
    val registeredGate: RegisteredGate? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null
) {
    val selectedGate: GateDto?
        get() = gates.firstOrNull { it.gateId == selectedGateId }

    val canRegister: Boolean
        get() = selectedGate != null && deviceId.isNotBlank() && !isRegistering && !isLoadingGates
}

class DeviceRegistrationViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        DeviceRegistrationUiState(
            deviceId = readDeviceId(application),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        )
    )
    val uiState: StateFlow<DeviceRegistrationUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    /**
     * Asks the server whether this device is registered to a gate. Any failure (404, network,
     * bad response) counts as not registered. Returns true when a gate is assigned.
     */
    suspend fun refreshRegistration(): Boolean {
        val deviceId = _uiState.value.deviceId
        val gate = if (deviceId.isBlank()) {
            null
        } else {
            try {
                ApiClient.deviceApi.getDevice(deviceId).let { device ->
                    device.gateId?.let { id ->
                        RegisteredGate(id, device.gateName?.takeIf { it.isNotBlank() } ?: "Gate $id")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        _uiState.update { it.copy(registeredGate = gate) }
        return gate != null
    }

    /** Called each time the dialog opens: clears old messages and reloads the gate list. */
    fun open() {
        _uiState.update {
            it.copy(
                successMessage = null,
                errorMessage = null,
                selectedGateId = it.selectedGateId ?: it.registeredGate?.gateId
            )
        }
        loadGates()
    }

    fun loadGates() {
        job?.cancel()
        _uiState.update { it.copy(isLoadingGates = true, errorMessage = null) }
        job = viewModelScope.launch {
            try {
                val gates = ApiClient.deviceApi.getGates(onlyActive = true)
                    .filter { it.status != false }
                    .sortedBy { it.gateId }
                _uiState.update { state ->
                    state.copy(
                        isLoadingGates = false,
                        gates = gates,
                        selectedGateId = state.selectedGateId?.takeIf { id -> gates.any { it.gateId == id } }
                            ?: gates.singleOrNull()?.gateId,
                        errorMessage = if (gates.isEmpty()) "No active gates found." else null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoadingGates = false, errorMessage = describeError(e, "Could not load gates"))
                }
            }
        }
    }

    fun selectGate(gateId: Int) {
        _uiState.update { it.copy(selectedGateId = gateId, errorMessage = null, successMessage = null) }
    }

    fun register() {
        val state = _uiState.value
        val gate = state.selectedGate ?: return
        if (!state.canRegister) return

        _uiState.update { it.copy(isRegistering = true, errorMessage = null, successMessage = null) }
        job = viewModelScope.launch {
            try {
                val response = ApiClient.deviceApi.registerDevice(
                    RegisterDeviceRequest(
                        deviceId = state.deviceId,
                        deviceName = state.deviceName.ifBlank { "Android device" },
                        gateId = gate.gateId,
                        gateName = gate.gateName
                    )
                )
                val registered = RegisteredGate(
                    gateId = response.gateId ?: gate.gateId,
                    gateName = response.gateName ?: gate.gateName ?: "Gate ${gate.gateId}"
                )
                _uiState.update {
                    it.copy(
                        isRegistering = false,
                        registeredGate = registered,
                        successMessage = response.message ?: "Device registered successfully"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isRegistering = false, errorMessage = describeError(e, "Device registration failed"))
                }
            }
        }
    }

    private fun describeError(e: Exception, fallback: String): String = when (e) {
        is HttpException -> ApiClient.errorMessage(e)?.takeIf { it.isNotBlank() }
            ?: "$fallback (HTTP ${e.code()})"
        is IOException -> "Cannot reach the server at ${ApiClient.baseUrl}"
        is JsonDataException -> "Unexpected response from the server."
        else -> e.message ?: fallback
    }

    private companion object {
        /** ANDROID_ID: tied to the device and the app signing key; changes after a factory reset. */
        @SuppressLint("HardwareIds")
        fun readDeviceId(context: Context): String =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
    }
}
