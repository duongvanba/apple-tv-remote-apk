package dev.duongvan.atvremote.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

data class AtvDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val model: String? = null
)

/** mDNS discovery of Apple TVs advertising the Companion link service. */
class Discovery(context: Context) {

    companion object {
        private const val TAG = "Discovery"
        const val SERVICE_TYPE = "_companion-link._tcp"
    }

    private val nsdManager = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as NsdManager

    fun devices(): Flow<AtvDevice> = callbackFlow {
        val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
        val resolving = AtomicBoolean(false)

        fun resolveNext() {
            if (!resolving.compareAndSet(false, true)) return
            val next = resolveQueue.poll()
            if (next == null) {
                resolving.set(false)
                return
            }
            nsdManager.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "resolve failed for ${serviceInfo.serviceName}: $errorCode")
                    resolving.set(false)
                    resolveNext()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress
                    if (host != null) {
                        val model = serviceInfo.attributes["rpmd"]?.toString(Charsets.UTF_8)
                        trySend(
                            AtvDevice(
                                id = "$host:${serviceInfo.port}",
                                name = serviceInfo.serviceName,
                                host = host,
                                port = serviceInfo.port,
                                model = model
                            )
                        )
                    }
                    resolving.set(false)
                    resolveNext()
                }
            })
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "discovery started")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveQueue.add(serviceInfo)
                resolveNext()
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IllegalStateException("không quét được mDNS (mã $errorCode)"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
    }
}
