package com.luxmusic.android.download

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.core.content.edit
import com.luxmusic.android.data.DownloadAccountState
import com.luxmusic.android.data.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.URL

class DownloadAccountStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableAccounts = MutableStateFlow(loadStates())
    val accounts: StateFlow<List<DownloadAccountState>> = mutableAccounts.asStateFlow()

    fun importCookies(service: DownloadService, uri: Uri): Result<DownloadAccountState> {
        return runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Не удалось прочитать файл cookies.")

            val filteredContent = filterCookiesForService(service, raw)
            if (filteredContent == null) {
                error("В cookies.txt не найдено ни одной cookie для ${service.title}.")
            }

            saveSession(
                service = service,
                cookiesText = filteredContent,
                userAgent = defaultUserAgent(),
            )
        }
    }

    fun captureCookiesFromWebView(
        service: DownloadService,
        userAgent: String?,
    ): Result<DownloadAccountState> {
        return runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.flush()

            val domainHeaders = webViewDomainsFor(service).associateWith { domain ->
                cookieManager.getCookie("https://$domain")
            }

            val cookiesText = DownloadParsing.cookiesFileFromHeaders(domainHeaders)
                ?: error("Не удалось получить cookies после входа в ${service.title}. Завершите авторизацию и попробуйте снова.")

            saveSession(
                service = service,
                cookiesText = cookiesText,
                userAgent = userAgent?.takeIf { it.isNotBlank() } ?: defaultUserAgent(),
            )
        }
    }

    fun clearSession(service: DownloadService) {
        preferences.edit { remove(service.name) }
        publish()
    }

    fun sessionFor(service: DownloadService): StoredAccountSession? {
        val payload = preferences.getString(service.name, null) ?: return null
        return runCatching {
            val root = JSONObject(payload)
            StoredAccountSession(
                cookiesText = root.getString("cookiesText"),
                userAgent = root.optString("userAgent").takeIf { it.isNotBlank() },
                updatedAt = root.optLong("updatedAt").takeIf { it > 0L } ?: 0L,
            )
        }.getOrNull()
    }

    private fun loadStates(): List<DownloadAccountState> {
        return DownloadService.entries
            .filterNot { it == DownloadService.UNKNOWN }
            .map { service ->
                val session = sessionFor(service)
                DownloadAccountState(
                    service = service,
                    isConnected = session?.cookiesText?.isNotBlank() == true,
                    updatedAt = session?.updatedAt?.takeIf { it > 0L },
                )
            }
    }

    private fun publish() {
        mutableAccounts.value = loadStates()
    }

    private fun saveSession(
        service: DownloadService,
        cookiesText: String,
        userAgent: String?,
    ): DownloadAccountState {
        val session = StoredAccountSession(
            cookiesText = cookiesText,
            userAgent = userAgent,
            updatedAt = System.currentTimeMillis(),
        )

        preferences.edit {
            putString(service.name, session.toJson().toString())
        }

        publish()
        return accounts.value.first { it.service == service }
    }

    private fun filterCookiesForService(service: DownloadService, raw: String): String? {
        return DownloadParsing.filterCookiesForService(service, raw)
    }

    private fun webViewDomainsFor(service: DownloadService): List<String> {
        val loginHost = runCatching { URL(service.loginUrl).host.lowercase() }.getOrNull()
        return listOfNotNull(loginHost)
            .plus(service.cookieDomains.map(String::lowercase))
            .distinct()
    }

    private fun defaultUserAgent(): String? {
        return runCatching { WebSettings.getDefaultUserAgent(context) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: System.getProperty("http.agent")?.takeIf { it.isNotBlank() }
    }

    data class StoredAccountSession(
        val cookiesText: String,
        val userAgent: String?,
        val updatedAt: Long,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("cookiesText", cookiesText)
            .putOpt("userAgent", userAgent)
            .put("updatedAt", updatedAt)
    }

    private companion object {
        const val PREFERENCES_NAME = "luxmusic_download_accounts"
    }
}
