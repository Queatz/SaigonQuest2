package quest.saigon.shared.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.PhoneNumbers
import quest.saigon.resolveLanguage
import quest.saigon.shared.model.MeDto
import quest.saigon.shared.model.UpdateMeRequest
import quest.saigon.shared.network.ApiResult
import quest.saigon.shared.network.TownApiClient
import quest.saigon.shared.platform.systemLanguageTag

data class SessionUiState(
    val me: MeDto? = null,
    val meKnown: Boolean = false,
    val languagePreference: String = "system",
    val language: Language = resolveLanguage("system", systemLanguageTag()),
    val mapsKey: String? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
) {
    val isSignedIn: Boolean get() = me != null
    val isSignedOut: Boolean get() = meKnown && me == null
}

class TownSessionStore(
    private val api: TownApiClient,
    private val scope: CoroutineScope,
    private val systemLanguageProvider: () -> String = { systemLanguageTag() },
) {
    private val _state = MutableStateFlow(
        SessionUiState(
            language = resolveLanguage("system", systemLanguageProvider()),
        ),
    )
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    val currentLanguage: Language get() = _state.value.language

    fun t(key: String, values: Map<String, String> = emptyMap()): String =
        Copy.text(_state.value.language, key, values)

    suspend fun bootstrap() {
        val storedPref = api.tokenStorage.readLanguage()
        val initialLang = resolveLanguage(storedPref, systemLanguageProvider())
        _state.update { it.copy(languagePreference = storedPref, language = initialLang) }

        val configDeferred = scope.async { api.getConfig() }
        val meResult = api.getMe()
        val configResult = configDeferred.await()

        val mapsKey = (configResult as? ApiResult.Success)?.value?.mapsKey?.takeIf { it.isNotBlank() }
        when (meResult) {
            is ApiResult.Success -> {
                val me = meResult.value
                val pref = me.language.ifBlank { storedPref }
                val lang = resolveLanguage(pref, systemLanguageProvider())
                api.tokenStorage.writeLanguage(pref)
                _state.update {
                    it.copy(
                        me = me,
                        meKnown = true,
                        languagePreference = pref,
                        language = lang,
                        mapsKey = mapsKey ?: it.mapsKey,
                        error = null,
                    )
                }
                syncStoredFcmTokenIfPresent(pref)
            }
            is ApiResult.Failure -> {
                if (meResult.status == 401) {
                    api.tokenStorage.writeSessionToken(null)
                }
                _state.update {
                    it.copy(
                        me = null,
                        meKnown = true,
                        mapsKey = mapsKey ?: it.mapsKey,
                    )
                }
            }
        }
    }

    suspend fun login(phone: String, password: String): Boolean {
        val canonicalPhone = PhoneNumbers.canonicalize(phone) ?: run {
            _state.update { it.copy(error = t("invalidPhone")) }
            return false
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        return when (val result = api.login(canonicalPhone, password)) {
            is ApiResult.Success -> {
                applySignedIn(result.value)
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isSubmitting = false, error = result.message) }
                false
            }
        }
    }

    suspend fun register(name: String, phone: String, password: String): Boolean {
        val canonicalPhone = PhoneNumbers.canonicalize(phone) ?: run {
            _state.update { it.copy(error = t("invalidPhone")) }
            return false
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        return when (val result = api.register(name, canonicalPhone, password)) {
            is ApiResult.Success -> {
                applySignedIn(result.value)
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isSubmitting = false, error = result.message) }
                false
            }
        }
    }

    suspend fun logout() {
        val fcmToken = api.tokenStorage.readFcmToken()
        if (!fcmToken.isNullOrBlank() && _state.value.me != null) {
            api.deleteFcmToken(fcmToken)
        }
        api.logout()
        _state.update { it.copy(me = null, meKnown = true, isSubmitting = false, error = null) }
    }

    suspend fun refreshMe(): MeDto? {
        return when (val result = api.getMe()) {
            is ApiResult.Success -> {
                val me = result.value
                val lang = resolveLanguage(me.language, systemLanguageProvider())
                api.tokenStorage.writeLanguage(me.language)
                _state.update {
                    it.copy(me = me, meKnown = true, languagePreference = me.language, language = lang)
                }
                me
            }
            is ApiResult.Failure -> {
                if (result.status == 401) handleUnauthorized()
                null
            }
        }
    }

    suspend fun updateProfile(request: UpdateMeRequest): Boolean {
        _state.update { it.copy(isSubmitting = true, error = null) }
        return when (val result = api.patchMe(request)) {
            is ApiResult.Success -> {
                val me = result.value
                val pref = me.language.ifBlank { _state.value.languagePreference }
                api.tokenStorage.writeLanguage(pref)
                _state.update {
                    it.copy(
                        me = me,
                        languagePreference = pref,
                        language = resolveLanguage(pref, systemLanguageProvider()),
                        isSubmitting = false,
                        error = null,
                    )
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isSubmitting = false, error = result.message) }
                false
            }
        }
    }

    suspend fun uploadProfilePhoto(photoBytes: ByteArray): Boolean {
        _state.update { it.copy(isSubmitting = true, error = null) }
        return when (val result = api.uploadProfilePhoto(photoBytes)) {
            is ApiResult.Success -> {
                _state.update { it.copy(me = result.value, isSubmitting = false, error = null) }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isSubmitting = false, error = result.message) }
                false
            }
        }
    }

    suspend fun setLanguagePreference(preference: String) {
        val normalized = preference.ifBlank { "system" }
        api.tokenStorage.writeLanguage(normalized)
        val lang = resolveLanguage(normalized, systemLanguageProvider())
        _state.update { it.copy(languagePreference = normalized, language = lang) }
        if (_state.value.me != null) {
            when (val result = api.patchMe(UpdateMeRequest(language = normalized))) {
                is ApiResult.Success -> _state.update { it.copy(me = result.value) }
                is ApiResult.Failure -> Unit
            }
            api.tokenStorage.readFcmToken()?.let { token ->
                api.registerFcmToken(token, normalized)
            }
        }
    }

    suspend fun syncFcmToken(token: String) {
        val clean = token.trim().takeIf { it.isNotBlank() } ?: return
        api.tokenStorage.writeFcmToken(clean)
        if (_state.value.me != null) {
            api.registerFcmToken(clean, _state.value.languagePreference)
        }
    }

    fun handleUnauthorized() {
        scope.launch { api.tokenStorage.writeSessionToken(null) }
        _state.update { it.copy(me = null, meKnown = true, isSubmitting = false) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun applySignedIn(me: MeDto) {
        val pref = me.language.ifBlank { _state.value.languagePreference }
        val lang = resolveLanguage(pref, systemLanguageProvider())
        api.tokenStorage.writeLanguage(pref)
        _state.update {
            it.copy(
                me = me,
                meKnown = true,
                languagePreference = pref,
                language = lang,
                isSubmitting = false,
                error = null,
            )
        }
        syncStoredFcmTokenIfPresent(pref)
    }

    private suspend fun syncStoredFcmTokenIfPresent(languagePref: String) {
        val token = api.tokenStorage.readFcmToken() ?: return
        api.registerFcmToken(token, languagePref)
    }
}
