package com.bc250.telemetry

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

private const val PORT = 8090
private const val TELEMETRY_PATH = "/api/telemetry"
private const val PREFS_NAME = "bc250_prefs"
private const val PREF_LAST_IP = "last_ip"

/** Talks to a single, already-known BC-250 host at the daemon's web port. */
class TelemetryClient(private val host: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(800, TimeUnit.MILLISECONDS)
        .readTimeout(800, TimeUnit.MILLISECONDS)
        .build()

    val baseUrl: String get() = "http://$host:$PORT"

    suspend fun fetchTelemetry(): Telemetry? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl$TELEMETRY_PATH").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                Telemetry.parse(body)
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Finds a BC-250 board on the local Wi-Fi network by probing hosts on the
 * current subnet for a live `web/server.py` instance (port 8090, valid
 * `/api/telemetry` JSON). Remembers the last successful IP in SharedPreferences
 * so subsequent launches reconnect instantly without rescanning.
 */
class DeviceDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(300, TimeUnit.MILLISECONDS)
        .readTimeout(400, TimeUnit.MILLISECONDS)
        .build()

    fun savedHost(): String? = prefs.getString(PREF_LAST_IP, null)

    private fun saveHost(host: String) {
        prefs.edit().putString(PREF_LAST_IP, host).apply()
    }

    /** Quick check that a specific host is still a valid, reachable BC-250. */
    suspend fun probe(host: String): Boolean = withContext(Dispatchers.IO) {
        isBc250(host)
    }

    private fun isBc250(host: String): Boolean {
        return try {
            val request = Request.Builder().url("http://$host:$PORT$TELEMETRY_PATH").build()
            probeClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body?.string() ?: return false
                body.contains("\"hardware\"")
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Scans the device's current /24 Wi-Fi subnet for a responding BC-250.
     * Runs up to 64 probes concurrently so a full sweep takes a couple of
     * seconds rather than minutes.
     */
    suspend fun scanSubnet(): String? = withContext(Dispatchers.IO) {
        val prefix = subnetPrefix() ?: return@withContext null
        val semaphore = Semaphore(64)
        coroutineScope {
            val jobs = (1..254).map { last ->
                async {
                    semaphore.withPermit {
                        val host = "$prefix.$last"
                        if (isBc250(host)) host else null
                    }
                }
            }
            val found = jobs.awaitAll().firstOrNull { it != null }
            found?.also { saveHost(it) }
        }
    }

    /** Tries the last known IP first, then falls back to a full subnet scan. */
    suspend fun discover(): String? {
        savedHost()?.let { last ->
            if (probe(last)) return last
        }
        return scanSubnet()
    }

    private fun subnetPrefix(): String? {
        val wifiManager = appContext.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        @Suppress("DEPRECATION")
        val ipInt = wifiManager.connectionInfo?.ipAddress ?: return null
        if (ipInt == 0) return null
        val bytes = intToBytes(ipInt)
        val ip = InetAddress.getByAddress(bytes).hostAddress ?: return null
        return ip.substringBeforeLast('.')
    }

    private fun intToBytes(ip: Int): ByteArray = byteArrayOf(
        (ip and 0xFF).toByte(),
        (ip shr 8 and 0xFF).toByte(),
        (ip shr 16 and 0xFF).toByte(),
        (ip shr 24 and 0xFF).toByte(),
    )
}
