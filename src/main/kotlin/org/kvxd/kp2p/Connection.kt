package org.kvxd.kp2p

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

@OptIn(InternalSerializationApi::class)
internal class Connection(
    private val node: P2PNode,
    private val socket: Socket,
    private val protocol: Protocol,
    private val scope: CoroutineScope
) {

    private val logger = KotlinLogging.logger("Connection ${node.nodeId}")

    private val dos = DataOutputStream(socket.getOutputStream())
    private val dis = DataInputStream(socket.getInputStream())
    private val packets = MutableSharedFlow<Packet>(extraBufferCapacity = Int.MAX_VALUE)
    private var readJob: Job? = null

    val isOpen: Boolean
        get() = socket.isConnected

    fun startReaderLoop() {
        readJob = scope.launch {
            try {
                while (true) {
                    val (type, payload) = FrameIO.readFrame(dis)
                    if (type != WireMsgType.PACKET) continue
                    val packet = protocol.decodePacket(payload)
                    logger.debug { "Received packet: ${packet::class.simpleName}" }
                    packets.emit(packet)
                }
            } catch (_: Throwable) {
                // stream closed or error
            } finally {
                close()
            }
        }
    }

    fun sendHandshake(nodeId: String) {
        val bytes = protocol.encodeNodeId(nodeId)
        FrameIO.writeFrame(dos, WireMsgType.HANDSHAKE, bytes)
    }

    fun receiveHandshake(): String {
        val (type, payload) = FrameIO.readFrame(dis)
        if (type != WireMsgType.HANDSHAKE) throw IllegalStateException("Expected handshake")
        return protocol.decodeNodeId(payload)
    }

    fun send(packet: Packet) {
        val bytes = protocol.encodePacket(packet)
        FrameIO.writeFrame(dos, WireMsgType.PACKET, bytes)
        logger.debug { "Received packet: ${packet::class.simpleName}" }
    }

    fun incomingPackets(): SharedFlow<Packet> = packets.asSharedFlow()

    fun close() {
        runCatching { socket.close() }
        readJob?.cancel()
    }
}