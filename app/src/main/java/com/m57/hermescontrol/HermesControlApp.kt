package com.m57.hermescontrol

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.remote.AndroidAttachmentReader
import com.m57.hermescontrol.data.remote.AndroidNetworkMonitor
import com.m57.hermescontrol.data.remote.DashboardSessionTokenRefresher
import com.m57.hermescontrol.data.remote.HermesHttpConfig
import com.m57.hermescontrol.data.remote.OkHttpProvider
import com.m57.hermescontrol.data.remote.createHermesHttpClient
import com.m57.hermescontrol.data.remote.installAttachmentReader
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.OkHttpWsTransport
import com.m57.hermescontrol.ui.analytics.AnalyticsPreloader
import com.m57.hermescontrol.util.AndroidLogSink
import com.m57.hermescontrol.util.HermesLog
import io.kseongbin.stacktrace.CrashLogger
import io.kseongbin.stacktrace.CrashLoggerConfig

class HermesControlApp :
    Application(),
    ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Likivik patch: capture crashes + ANRs to an app-scoped dir so they can
        // be uploaded for diagnosis (no logcat/ADB available on-device).
        CrashLogger.initialize(
            this,
            CrashLoggerConfig(
                enableCrashDetection = true,
                enableAnrDetection = true,
                maxLogFiles = 20,
                logFilePrefix = "hermes_app",
            ),
        )
        AuthManager.init(this)
        // Shared code logs through the :shared HermesLog facade; install the
        // android.util.Log-backed sink (and its BuildConfig.DEBUG flag) here.
        HermesLog.install(AndroidLogSink)
        // Only Android can read a content:// attachment; install the reader here so
        // shared chat code can request the bytes without holding a Context.
        installAttachmentReader(AndroidAttachmentReader(this))
        // Issue #478: guarantee a "Default" profile is always selected so there is no
        // separate standalone/default code path anywhere in the app.
        AuthManager.ensureDefaultProfile()
        AndroidNetworkMonitor.init(this)
        HermesWsClient.installNetworkMonitor(AndroidNetworkMonitor)
        HermesWsClient.installAuth(AuthManager)
        HermesWsClient.installTransport(OkHttpWsTransport(OkHttpProvider.websocket))
        HermesWsClient.installHttpClient(
            createHermesHttpClient(OkHttpProvider.probe, HermesHttpConfig.Probe),
        )
        HermesWsClient.installLoopbackTokenRefresher { DashboardSessionTokenRefresher.refresh() }
        // Issue #537 follow-up (A): preload analytics in the background after launch
        // so the tab renders instantly when opened (the usage endpoint is slow on a
        // cold backend). Fire-and-forget; never blocks UI startup.
        AnalyticsPreloader.preload(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader
            .Builder(this)
            .okHttpClient(OkHttpProvider.publicMedia)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }.crossfade(true)
            .build()
}
