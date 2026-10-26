package com.m57.hermescontrol.ui.authlogin

import com.m57.hermescontrol.data.remote.CookieManager
import com.m57.hermescontrol.data.remote.OkHttpProvider
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.remote.resolve
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Android keeps the historical transport: the same probe client, the same 10 s
 * timeouts, and `CookieManager.beginAuthentication()` so the persistent jar
 * accepts the `Set-Cookie` this response carries. #470 depends on that; the
 * migration must not change it.
 */
internal actual suspend fun performPasswordLogin(
    endpoint: ServerEndpoint,
    jsonBody: String,
): Int {
    val loginClient =
        OkHttpProvider.probe
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    val request =
        Request
            .Builder()
            .url(endpoint.resolve("auth/password-login"))
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

    CookieManager.beginAuthentication()
    return loginClient.newCall(request).execute().use { response -> response.code }
}
