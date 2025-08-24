package org.kvxd.kp2p

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.kvxd.kp2p.conf.DiscoveryConfig
import org.kvxd.kp2p.conf.ReconnectPolicy
import org.kvxd.kp2p.discovery.DiscoveryManager
import org.kvxd.kp2p.selector.Selector
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Peer internal constructor(
    val nodeId: String,
    val address: String,
    val port: Int,
    internal val conn: Connection
) {

    fun send(packet: Packet) = conn.send(packet)
    fun receive(): Flow<Packet> = conn.incomingPackets()
}

class P2PNode private constructor(
    private val listenPort: Int,
    private val protocol: Protocol,
    private val discoveryConfig: DiscoveryConfig,
    private val reconnectPolicy: ReconnectPolicy
) : Closeable {

    val nodeId: String = UUID.randomUUID().toString()

    private val logger = KotlinLogging.logger("P2PNode $nodeId")

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val discovery = DiscoveryManager(discoveryConfig, scope, nodeId)
    private val peers = ConcurrentHashMap<String, Peer>()
    private val conns = ConcurrentHashMap<String, Connection>()
    private val listeners = ConcurrentLinkedQueue<suspend (Peer, Packet) -> Unit>()
    private val _connectedCount = MutableStateFlow(0)
    val connectedCount: StateFlow<Int> = _connectedCount

    companion object {

        fun create(
            listenPort: Int = 0,
            protocol: Protocol,
            discovery: DiscoveryConfig = DiscoveryConfig(),
            reconnectPolicy: ReconnectPolicy = ReconnectPolicy()
        ): P2PNode = P2PNode(listenPort, protocol, discovery, reconnectPolicy)
    }

    fun start() {
        discovery.start()
        startListener()
        scope.launch { bootstrapAndMaintain() }
    }

    private fun startListener() {
        scope.launch(Dispatchers.IO) {
            ServerSocket().use { ss ->
                ss.reuseAddress = true
                ss.bind(InetSocketAddress("0.0.0.0", listenPort))
                while (isActive) {
                    val sock = ss.accept()
                    handleIncoming(sock)
                }
            }
        }
    }

    private fun handleIncoming(sock: Socket) {
        val conn = Connection(this, sock, protocol, scope)
        scope.launch {
            try {
                conn.sendHandshake(nodeId)
                val remoteNodeId = conn.receiveHandshake()
                registerPeer(remoteNodeId, sock, conn)
                conn.startReaderLoop()
                conn.incomingPackets().collect {
                    dispatchPacket(peers[remoteNodeId]!!, it)
                }
            } catch (e: Exception) {
                if (!conn.isOpen)
                    logger.error(e) { "Incoming connection failed" }
            } finally {
                unregisterPeer(conn)
            }
        }
    }

    private fun registerPeer(peerId: String, sock: Socket, conn: Connection) {
        // Close any existing connection to this peer
        conns[peerId]?.close()
        val peer = Peer(peerId, sock.inetAddress.hostAddress, sock.port, conn)
        peers[peerId] = peer
        conns[peerId] = conn
        _connectedCount.value = peers.size
    }

    private fun unregisterPeer(conn: Connection) {
        val peerId = conns.entries.find { it.value == conn }?.key ?: return
        peers.remove(peerId)
        conns.remove(peerId)
        _connectedCount.value = peers.size
        conn.close()
    }

    private fun dispatchPacket(peer: Peer, packet: Packet) {
        listeners.forEach { l -> scope.launch { l(peer, packet) } }
    }

    private suspend fun bootstrapAndMaintain() {
        discovery.peersFlow.collect { list ->
            list.forEach { dp ->
                if (!peers.containsKey(dp.nodeId)) {
                    launchReconnectLoop(dp.nodeId, dp.host, dp.port)
                }
            }
        }
    }

    private fun launchReconnectLoop(peerId: String, host: String, port: Int) {
        scope.launch {
            var attempt = 0
            var delayMs = reconnectPolicy.initialDelayMs
            while (isActive) {
                try {
                    val sock = Socket().apply { connect(InetSocketAddress(host, port), 3000) }
                    val conn = Connection(this@P2PNode, sock, protocol, scope)

                    conn.sendHandshake(nodeId)
                    val remoteNodeId = conn.receiveHandshake()

                    registerPeer(remoteNodeId, sock, conn)
                    conn.startReaderLoop()
                    conn.incomingPackets().collect { dispatchPacket(peers[remoteNodeId]!!, it) }

                    // Connection successful, break retry loop
                    break
                } catch (e: Exception) {
                    // Retry with backoff
                    attempt++
                    if (attempt >= reconnectPolicy.maxAttempts) break
                    delay(delayMs)
                    delayMs = (delayMs * reconnectPolicy.multiplier).toLong()
                        .coerceAtMost(reconnectPolicy.maxDelayMs)
                }
            }
        }
    }

    fun connectedPeers(): List<Peer> = peers.values.toList()

    suspend fun awaitConnections(min: Int, timeoutMS: Duration = 5.seconds) {
        if (min <= 0) return
        withTimeout(timeoutMS) {
            connectedCount
                .filter { it >= min }
                .first()
        }
    }

    fun select(selector: Selector, min: Int = 1, max: Int = min, action: suspend (Peer) -> Unit) {
        scope.launch {
            try {
                val chosen = selector.select(this@P2PNode, min, max); chosen.forEach { launch { action(it) } }
            } catch (_: Exception) {
            }
        }
    }

    fun addListener(handler: suspend (Peer, Packet) -> Unit) {
        listeners += handler
    }

    override fun close() {
        runCatching { scope.cancel() }
        conns.values.forEach { it.close() }
        peers.clear()
        conns.clear()
    }
}