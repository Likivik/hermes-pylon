# `runCatching` inventory — production source sets

Generated 2026-11-17 from the tree at the head of `main`. **Read-only artefact:** it classifies nothing behaviourally and changes no code.

- total sites: **99**
- logged: **4**
- fire-and-forget: **68**
- silent fallback: **18**
- needs review: **9**

## Policy buckets

- **review**: 34
- **log-and-continue**: 26
- **MUST-NOT-BE-SILENT (auth/session)**: 21
- **MUST-NOT-BE-SILENT (transport/IO)**: 10
- **typed fallback + log**: 8

Rules: auth/session/token paths are `MUST-NOT-BE-SILENT` regardless of shape; transport/IO follows; media decode is expected `log-and-continue`.

## Sites

| file:line | shape | policy | code |
|---|---|---|---|
| `app/src/main/java/com/m57/hermescontrol/MainActivity.kt:30` | SILENT-IGNORED | review | `runCatching { AuthManager.getAppLanguage() }` |
| `shared/src/commonMain/kotlin/com/m57/hermescontrol/data/model/Profile.kt:34` | SILENT-FALLBACK | typed fallback + log | `return runCatching { json.decodeFromJsonElement<BotRosterMeta>(element) }.getOrN` |
| `shared/src/commonMain/kotlin/com/m57/hermescontrol/data/model/StatusResponse.kt:78` | SILENT-FALLBACK | typed fallback + log | `return runCatching {` |
| `shared/src/commonMain/kotlin/com/m57/hermescontrol/data/model/StatusResponse.kt:98` | SILENT-FALLBACK | typed fallback + log | `return runCatching {` |
| `shared/src/commonMain/kotlin/com/m57/hermescontrol/data/remote/AuthPayloads.kt:36` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching {` |
| `shared/src/commonMain/kotlin/com/m57/hermescontrol/data/ws/HermesWsClient.kt:287` | SILENT-REVIEW | MUST-NOT-BE-SILENT (transport/IO) | `val alive = runCatching { ping(LIVENESS_PROBE_TIMEOUT_MS) }.isSuccess` |
| `shared/src/commonMain/kotlin/com/m57/hermescontrol/data/ws/HermesWsClient.kt:358` | LOGGED | MUST-NOT-BE-SILENT (transport/IO) | `runCatching { auth.isAutoReconnect() }` |
| `shared/src/commonMain/kotlin/com/m57/hermescontrol/data/ws/HermesWsClient.kt:1580` | LOGGED | MUST-NOT-BE-SILENT (transport/IO) | `runCatching {` |
| `shared/src/commonMain/kotlin/com/m57/hermescontrol/data/ws/KtorWsTransport.kt:259` | SILENT-IGNORED | MUST-NOT-BE-SILENT (transport/IO) | `runCatching { established.close(CloseReason(code.toShort(), reason)) }` |
| `shared/src/commonMain/kotlin/com/m57/hermescontrol/ui/chat/MediaTypes.kt:76` | SILENT-IGNORED | log-and-continue | `runCatching {` |
| `shared/src/commonMain/kotlin/com/m57/hermescontrol/ui/config/ConfigFormData.kt:202` | SILENT-FALLBACK | typed fallback + log | `val parsed = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(te` |
| `shared/src/commonMain/kotlin/com/m57/hermescontrol/util/HermesLog.kt:164` | LOGGED | review | `runCatching { sink.record(record) }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/data/config/ServerStoreMigration.kt:55` | SILENT-IGNORED | review | `runCatching { ThemePreference.valueOf(themePrefString!!) }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/data/config/ServerStoreMigration.kt:62` | SILENT-IGNORED | review | `runCatching { ThemePreset.valueOf(themePresetString!!) }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/data/remote/GatewayFileClient.kt:199` | SILENT-REVIEW | MUST-NOT-BE-SILENT (transport/IO) | `return runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.get` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/data/remote/SeekableGatewayMediaSession.kt:108` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching { Math.addExact(position, byteCount.toLong() - 1L) }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/chat/AndroidAttachmentOpener.kt:80` | SILENT-REVIEW | review | `val viewAttempt = runCatching { openWithView(parsed, attachment.mimeType) }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/chat/AndroidAttachmentOpener.kt:85` | SILENT-IGNORED | review | `runCatching {` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/chat/ImageBytesResolver.kt:62` | SILENT-IGNORED | log-and-continue | `runCatching {` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/chat/ImageBytesResolver.kt:122` | SILENT-IGNORED | log-and-continue | `runCatching { Base64.getDecoder().decode(data) }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/chat/ImageBytesResolver.kt:137` | SILENT-IGNORED | log-and-continue | `runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytesLi` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/chat/ImageBytesResolver.kt:149` | SILENT-IGNORED | log-and-continue | `runCatching {` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/chat/MediaImageStore.kt:54` | SILENT-IGNORED | log-and-continue | `runCatching { resolver.delete(uri, null, null) }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/chat/MediaImageStore.kt:63` | SILENT-IGNORED | log-and-continue | `runCatching { resolver.delete(uri, null, null) }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/chat/SpeechInputHelper.kt:24` | SILENT-IGNORED | review | `runCatching {` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/files/FilesPlatformAndroid.kt:37` | SILENT-FALLBACK | MUST-NOT-BE-SILENT (transport/IO) | `val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/files/FilesPlatformAndroid.kt:68` | SILENT-IGNORED | MUST-NOT-BE-SILENT (transport/IO) | `runCatching {` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/mcp/ExternalUrlLauncherAndroid.kt:16` | SILENT-IGNORED | review | `runCatching {` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/media/MediaPlaybackAndroid.kt:115` | SILENT-FALLBACK | log-and-continue | `pendingSurface?.let { runCatching { created.setDisplay(it) } }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/media/MediaPlaybackAndroid.kt:125` | SILENT-IGNORED | log-and-continue | `runCatching { created.start() }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/media/MediaPlaybackAndroid.kt:132` | SILENT-FALLBACK | log-and-continue | `val position = runCatching { created.currentPosition }.getOrNull() ?: return` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/media/MediaPlaybackAndroid.kt:139` | SILENT-IGNORED | log-and-continue | `runCatching { created.setDisplay(surface) }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/media/MediaPlaybackAndroid.kt:148` | SILENT-IGNORED | log-and-continue | `runCatching { created.start() }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/media/MediaPlaybackAndroid.kt:153` | SILENT-REVIEW | log-and-continue | `player?.let { runCatching { it.pause() } }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/media/MediaPlaybackAndroid.kt:158` | SILENT-FALLBACK | log-and-continue | `player?.let { runCatching { it.seekTo(positionMillis) } }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/media/MediaPlaybackAndroid.kt:165` | SILENT-IGNORED | log-and-continue | `runCatching { created.stop() }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/media/MediaPlaybackAndroid.kt:166` | SILENT-IGNORED | log-and-continue | `runCatching { created.reset() }` |
| `ui/src/androidMain/kotlin/com/m57/hermescontrol/ui/media/MediaPlaybackAndroid.kt:167` | SILENT-IGNORED | log-and-continue | `runCatching { created.release() }` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/data/config/ServerStore.kt:42` | SILENT-IGNORED | review | `runCatching { dataStore.data.first() }.onSuccess { loaded ->` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/data/local/AuthManager.kt:139` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching { store.stateFlow.first() }` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/data/local/AuthManager.kt:142` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching { publishTokenLocked(getTokenInternal(storage)) }` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/data/local/ChatMessageMapper.kt:44` | SILENT-IGNORED | review | `runCatching {` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/data/remote/CookieManager.kt:49` | SILENT-FALLBACK | typed fallback + log | `val target = runCatching { Url(url) }.getOrNull() ?: return ""` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/data/remote/CookieSerialization.kt:92` | SILENT-FALLBACK | typed fallback + log | `val parsed = runCatching { parseServerSetCookieHeader(header) }.getOrNull() ?: r` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/authlogin/AuthLoginViewModel.kt:150` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching {` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/authlogin/AuthLoginViewModel.kt:170` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching {` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/billing/BillingViewModel.kt:114` | SILENT-IGNORED | review | `runCatching { BillingRepository.previewSubscription(subscriptionTypeId) }` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/billing/BillingViewModel.kt:146` | SILENT-IGNORED | review | `runCatching { BillingRepository.upgradeSubscription(subscriptionTypeId) }` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/billing/BillingViewModel.kt:190` | SILENT-IGNORED | review | `runCatching { BillingRepository.changeSubscription(SubscriptionChangeRequest(sub` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/billing/BillingViewModel.kt:229` | SILENT-IGNORED | review | `runCatching { BillingRepository.resumeSubscription() }` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/chat/ChatViewModel.kt:352` | SILENT-IGNORED | review | `runCatching { authStore.isGatedMode() }` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/chat/ChatViewModel.kt:3920` | SILENT-IGNORED | review | `runCatching {` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/chat/ChatViewModel.kt:4153` | SILENT-REVIEW | review | `} ?: runCatching {` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/chat/ChatViewModel.kt:4195` | SILENT-REVIEW | review | `} ?: runCatching {` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/chat/rail/SessionRail.kt:355` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching { emojiFromChar(iconText) }.getOrNull()` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/common/BotAvatar.kt:110` | SILENT-REVIEW | log-and-continue | `return runCatching { Color(0xFF000000L or color.drop(1).toLong(16)) }.getOrDefau` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/connect/ConnectViewModel.kt:148` | SILENT-IGNORED | review | `runCatching {` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/connect/ConnectViewModel.kt:212` | SILENT-IGNORED | review | `runCatching { ServerEndpoint.parseForBuild(state.baseUrl) }.getOrNull()` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/diagnostics/DiagnosticsViewModel.kt:72` | SILENT-IGNORED | review | `runCatching { diagnosticsEnvironment() }.getOrElse {` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/diagnostics/DiagnosticsViewModel.kt:76` | SILENT-IGNORED | review | `runCatching {` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/diagnostics/DiagnosticsViewModel.kt:79` | SILENT-FALLBACK | typed fallback + log | `val endpoint = runCatching { GatewayPlatform.endpoint() }` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/diagnostics/DiagnosticsViewModel.kt:100` | SILENT-FALLBACK | typed fallback + log | `val endpoint = runCatching { GatewayPlatform.endpoint() }.getOrNull()` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/sessions/SessionsScreen.kt:270` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching { searchSnippetJson.parseToJsonElement(withoutHighlightMarkers) }.ge` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/settings/SettingsViewModel.kt:212` | SILENT-IGNORED | review | `runCatching {` |
| `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/settings/SettingsViewModel.kt:329` | SILENT-IGNORED | review | `runCatching {` |
| `ui/src/desktopWebMain/kotlin/com/m57/hermescontrol/data/remote/MediaStore.desktopWeb.kt:25` | SILENT-IGNORED | log-and-continue | `runCatching {` |
| `ui/src/desktopWebMain/kotlin/com/m57/hermescontrol/ui/media/HermesImageBytesActual.kt:19` | SILENT-FALLBACK | log-and-continue | `val bitmap = remember(data) { runCatching { SkiaImage.makeFromEncoded(data).toCo` |
| `ui/src/desktopWebMain/kotlin/com/m57/hermescontrol/ui/media/HermesRemoteImage.desktopWeb.kt:31` | SILENT-IGNORED | log-and-continue | `runCatching {` |
| `ui/src/jvmAndroidMain/kotlin/com/m57/hermescontrol/data/config/ServerStoreBlocking.jvmAndroid.kt:10` | SILENT-REVIEW | review | `): T = runCatching { runBlocking { flow.first() } }.getOrDefault(fallback)` |
| `ui/src/jvmAndroidMain/kotlin/com/m57/hermescontrol/ui/mcp/AuthorizationUrlActual.kt:6` | SILENT-FALLBACK | MUST-NOT-BE-SILENT (auth/session) | `val uri = runCatching { URI(raw) }.getOrNull() ?: return false` |
| `ui/src/jvmMain/kotlin/com/m57/hermescontrol/data/local/JvmAuthPlatform.kt:109` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching { Files.newInputStream(file).use(props::load) }` |
| `ui/src/jvmMain/kotlin/com/m57/hermescontrol/data/local/JvmAuthPlatform.kt:119` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching {` |
| `ui/src/jvmMain/kotlin/com/m57/hermescontrol/data/local/JvmAuthPlatform.kt:124` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching {` |
| `ui/src/jvmMain/kotlin/com/m57/hermescontrol/data/local/PreferencesStoreJvm.kt:41` | SILENT-IGNORED | review | `runCatching {` |
| `ui/src/jvmMain/kotlin/com/m57/hermescontrol/ui/files/FilesPlatformJvm.kt:33` | SILENT-IGNORED | MUST-NOT-BE-SILENT (transport/IO) | `runCatching { PickedFile(file.name, mimeType.ifBlank { "application/octet-stream` |
| `ui/src/jvmMain/kotlin/com/m57/hermescontrol/ui/files/FilesPlatformJvm.kt:49` | SILENT-IGNORED | MUST-NOT-BE-SILENT (transport/IO) | `runCatching {` |
| `ui/src/jvmMain/kotlin/com/m57/hermescontrol/ui/logs/LogBundleSharingJvm.kt:13` | SILENT-IGNORED | review | `runCatching {` |
| `ui/src/jvmMain/kotlin/com/m57/hermescontrol/ui/mcp/ExternalUrlLauncherJvm.kt:20` | SILENT-IGNORED | review | `runCatching {` |
| `ui/src/jvmMain/kotlin/com/m57/hermescontrol/ui/media/MediaActions.jvm.kt:19` | SILENT-FALLBACK | log-and-continue | `return runCatching {` |
| `ui/src/jvmMain/kotlin/com/m57/hermescontrol/ui/media/MediaActions.jvm.kt:34` | LOGGED | log-and-continue | `return runCatching {` |
| `ui/src/jvmMain/kotlin/com/m57/hermescontrol/ui/media/MediaActions.jvm.kt:83` | SILENT-IGNORED | log-and-continue | `runCatching { base64Decode(model.substring(comma + 1)) }.getOrNull()` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/data/local/PreferencesStoreWasm.kt:70` | SILENT-IGNORED | review | `runCatching {` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/data/local/WebAuthPlatform.kt:54` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching {` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/data/local/WebAuthPlatform.kt:67` | SILENT-FALLBACK | MUST-NOT-BE-SILENT (auth/session) | `val stored = runCatching { jsSessionGet(PREFIX + logicalKey) }.getOrNull() ?: re` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/data/local/WebAuthPlatform.kt:78` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching { jsSessionRemove(PREFIX + logicalKey) }` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/data/local/WebAuthPlatform.kt:81` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching { jsSessionSet(PREFIX + logicalKey, value) }` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/data/local/WebAuthPlatform.kt:87` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching {` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/data/local/WebAuthPlatform.kt:109` | SILENT-FALLBACK | MUST-NOT-BE-SILENT (auth/session) | `val stored = runCatching { jsSessionGet(COOKIE_PREFIX + id) }.getOrNull() ?: ret` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/data/local/WebAuthPlatform.kt:121` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching { jsSessionRemove(COOKIE_PREFIX + id) }` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/data/local/WebAuthPlatform.kt:124` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching { jsSessionSet(COOKIE_PREFIX + id, cookie) }` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/ui/Main.kt:166` | SILENT-IGNORED | review | `runCatching {` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/ui/Main.kt:171` | SILENT-IGNORED | review | `runCatching { fontResolver.preload(FontFamily.Default) }` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/ui/Main.kt:172` | SILENT-IGNORED | review | `runCatching { fontResolver.preload(iconSubsetFamily) }` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/ui/Main.kt:463` | SILENT-IGNORED | review | `runCatching {` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/ui/authlogin/SignOutWasmJs.kt:23` | SILENT-IGNORED | MUST-NOT-BE-SILENT (auth/session) | `runCatching { window.sessionStorage.setItem("pylon.logout", "1") }` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/ui/files/FilesPlatformWasm.kt:115` | SILENT-IGNORED | MUST-NOT-BE-SILENT (transport/IO) | `runCatching { onPicked(PickedFile(name = name, mimeType = mime, bytes = decode(b` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/ui/logs/LogBundleSharingWasm.kt:16` | SILENT-REVIEW | review | `{ text -> jsClipboardAvailable() && runCatching { jsWriteClipboard(text) }.getOr` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/ui/media/MediaActions.wasm.kt:19` | SILENT-FALLBACK | log-and-continue | `return runCatching {` |
| `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/ui/media/MediaActions.wasm.kt:80` | SILENT-IGNORED | log-and-continue | `runCatching { base64Decode(model.substring(comma + 1)) }.getOrNull()` |

## What this list does not tell you

- Whether a swallowed failure is safe to swallow — that is the per-site judgement the classification pass makes.
- Whether the block guards something the user can see; shape alone cannot separate a cosmetic decode retry from a session refresh.
- The 2 empty `catch` blocks are not `runCatching`: `PreferencesStoreWasm.kt:31` is JavaScript inside a Kotlin raw string, `AuthManager.kt:550` is a coroutine cancel. Both benign.
## Classification status (updated 2026-11-17)

**Resolved in code:**
- `WebAuthPlatform.kt` — 8 sites: writes check the bridge result and log; reads simplified. Root cause was the ignored `false`, not the `runCatching`.
- `JvmAuthPlatform.kt` — 3 sites: read/write failures log; the swallowed `setPosixFilePermissions` failure (security-relevant) now warns.
- `ui/src/wasmJsMain/.../Main.kt` — 4 sites: failed session adoption logs (the banner shape); 3 landing preloads documented as intentional.
- `HermesWsClient.kt` + `KtorWsTransport.kt` — 4 sites: `isGatedMode()`/`isAutoReconnect()` failures log (they silently flipped the handshake mode); heartbeat probe and close-echo marked intentional.
- `ChatViewModel.kt` — 4 sites: the connect guard's auth-mode fallback logs (issue #640 shape); both privilege prompts log; the bulk request marked handled.
- `DiagnosticsViewModel.kt` — 4 sites: store-fact and endpoint reads log; the other two already surfaced a verdict to the panel.

**Classified as handled, no change needed:**
- `BillingViewModel.kt` — 4 sites, each with `.onFailure` that sets `errorMessage` for the UI.

**Remaining:**
- `ImageBytesResolver.kt` (4), `MediaPlaybackAndroid.kt` (10, expected log-and-continue), and the 34 "review" sites where shape alone cannot decide.

## Classification closed (2026-11-27)

Every production site is now classified in code, so the budget is **0** and the ratchet only moves
down. Two forms are used, and each site carries the one that is true of it:

- a log call inside the failure path, where nothing else would have told anyone;
- `inventory: handled — …` where the failure is returned, rendered, or written to state;
- `inventory: intentional-ignore — …` where no actor could respond to it.

The count went 99 → 73 → 0 over that pass; 73 were unclassified when the gate first went red.

