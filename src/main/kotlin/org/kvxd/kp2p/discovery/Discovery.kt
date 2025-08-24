package org.kvxd.kp2p.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.kvxd.kp2p.conf.DiscoveryConfig
import java.io.Closeable
import java.net.DatagramSocket
import java.net.MulticastSocket
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

class DiscoveryManager(private val config: DiscoveryConfig, private val scope: CoroutineScope, private val id: String) :
    Closeable {

    private val peers = ConcurrentHashMap<String, DiscoveredPeer>()
    private val _peersFlow = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val peersFlow: StateFlow<List<DiscoveredPeer>> = _peersFlow

    private var mdnsSocket: MulticastSocket? = null
    private var gossipSocket: DatagramSocket? = null
    private var running = true

    fun start() {
        config.bootstrap.forEach { addr -> registerBootstrap(addr) }
        scope.launch { pruningLoop() }
    }

    private fun registerBootstrap(addr: String) {
        try {
            val (h, p) = parseHostPort(addr)
            peers[id] = DiscoveredPeer(id, h, p, System.currentTimeMillis(), "bootstrap")
            _peersFlow.value = peers.values.toList()
        } catch (_: Exception) {
        }
    }

    private fun parseHostPort(s: String): Pair<String, Int> {
        val parts = s.split(':')
        return if (parts.size == 2) parts[0] to parts[1].toInt() else s to 0
    }

    private suspend fun pruningLoop() {
        while (running) {
            delay(8_000)
            val cutoff = System.currentTimeMillis() - 30_000
            var changed = false
            val it = peers.entries.iterator()
            while (it.hasNext()) {
                val e = it.next(); if (e.value.seenAt < cutoff) {
                    it.remove(); changed = true
                }
            }
            if (changed) _peersFlow.value = peers.values.toList()
        }
    }

    fun listPeers(): List<DiscoveredPeer> = peers.values.toList()

    override fun close() {
        running = false
        mdnsSocket?.close(); gossipSocket?.close()
    }
}