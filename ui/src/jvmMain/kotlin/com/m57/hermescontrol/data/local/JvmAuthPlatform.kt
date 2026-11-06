package com.m57.hermescontrol.data.local

import androidx.datastore.core.DataStoreFactory
import com.m57.hermescontrol.data.config.ServerStore
import com.m57.hermescontrol.data.config.serverStoreStorage
import com.m57.hermescontrol.data.remote.CookiePlatform
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.security.SecretStore
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties

/**
 * The desktop side of [AuthPlatform].
 *
 * Mirrors the Android and browser shapes: the server store keeps profiles on
 * disk, the secret store keeps tokens in a `0600` file in the app config
 * directory, and the cookie platform does the same for per-server session
 * cookies. Nothing is encrypted here — the honest desktop equivalent of the
 * Keystore is a file only the owner can read, which is what `0600` buys.
 */
internal class JvmAuthPlatform : AuthPlatform {
    override fun createServerStore(scope: CoroutineScope): ServerStore =
        ServerStore(
            DataStoreFactory.create(
                storage = serverStoreStorage(),
                migrations = emptyList(),
            ),
            scope,
        )

    override fun createSecretStore(): SecretStore = AuthManager.secretStoreFactory?.invoke() ?: JvmSecretStore()

    override fun initializeCookieStore(initialServerId: String) {
        // Nothing to prepare: the cookie platform reads its file lazily.
    }

    override val cookies: CookiePlatform = JvmCookiePlatform()
}

/** Tokens in a `0600` properties file under the app config directory. */
internal class JvmSecretStore : SecretStore {
    private val file: Path = pylonConfigDir().resolve("secrets.properties")
    private val values: Properties = loadProperties(file)

    override fun getString(logicalKey: String): String? = values.getProperty(logicalKey)

    override fun putString(
        logicalKey: String,
        value: String?,
    ) {
        if (value == null) values.remove(logicalKey) else values.setProperty(logicalKey, value)
        storeProperties(file, values)
    }

    override fun deletePrefix(prefix: String) {
        values.stringPropertyNames().filter { it.startsWith(prefix) }.forEach(values::remove)
        storeProperties(file, values)
    }
}

/** Per-server session cookies in a `0600` properties file. */
internal class JvmCookiePlatform : CookiePlatform {
    private val file: Path = pylonConfigDir().resolve("cookies.properties")
    private val values: Properties = loadProperties(file)
    private var current: String? = null

    override fun getSessionCookie(): String? = current?.let { values.getProperty(it) }

    override fun setSessionCookie(
        cookie: String?,
        endpoint: ServerEndpoint,
    ) {
        val id = current ?: return
        if (cookie == null) values.remove(id) else values.setProperty(id, cookie)
        storeProperties(file, values)
    }

    override fun pruneServerCache() {
        val keep = setOfNotNull(current)
        values.stringPropertyNames().filterNot { it in keep }.forEach(values::remove)
        storeProperties(file, values)
    }

    override fun currentServerOrNull(): String? = current

    override fun useStore(serverId: String) {
        current = serverId
    }
}

/** `$XDG_CONFIG_HOME/pylon`, falling back to `~/.config/pylon`. */
internal fun pylonConfigDir(): Path {
    val base =
        System.getenv("XDG_CONFIG_HOME")
            ?.takeIf { it.isNotBlank() }
            ?.let { Path.of(it) }
            ?: Path.of(System.getProperty("user.home"), ".config")
    val dir = base.resolve("pylon")
    Files.createDirectories(dir)
    return dir
}

private fun loadProperties(file: Path): Properties {
    val props = Properties()
    if (Files.exists(file)) {
        runCatching { Files.newInputStream(file).use(props::load) }
    }
    return props
}

/** Write through a temp file, then move it into place with owner-only permissions. */
private fun storeProperties(
    file: Path,
    props: Properties,
) {
    runCatching {
        val dir = file.parent
        Files.createDirectories(dir)
        val tmp = Files.createTempFile(dir, "pylon", ".tmp")
        Files.newOutputStream(tmp).use { props.store(it, null) }
        runCatching {
            Files.setPosixFilePermissions(
                tmp,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}
