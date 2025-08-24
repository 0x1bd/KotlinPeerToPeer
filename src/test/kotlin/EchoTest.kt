import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.Serializable
import org.kvxd.kp2p.P2PNode
import org.kvxd.kp2p.Packet
import org.kvxd.kp2p.conf.DiscoveryConfig
import org.kvxd.kp2p.protocolBuilder
import org.kvxd.kp2p.register
import kotlin.test.Test
import kotlin.test.assertTrue

class EchoTest {

    @Serializable
    data class MessagePacket(
        val text: String
    ) : Packet

    @Test
    fun echoTest(): Unit = runBlocking {
        val portA = 50510
        val portB = 50511

        val packetReceived = CompletableDeferred<Packet>()

        val protocol = protocolBuilder { register<MessagePacket>() }

        val discA = DiscoveryConfig().apply { bootstrap { add("127.0.0.1:$portB") } }

        val nodeA = P2PNode.create(listenPort = portA, protocol = protocol, discovery = discA)
        val nodeB = P2PNode.create(listenPort = portB, protocol = protocol)

        nodeA.addListener { peer, packet -> peer.send(packet) }
        nodeB.addListener { _, packet -> packetReceived.complete(packet) }

        nodeA.start()
        nodeB.start()

        nodeB.awaitConnections(1)
        nodeA.awaitConnections(1)

        nodeB.connectedPeers().firstOrNull()?.send(MessagePacket("Hello, World!"))

        val echoPacket = packetReceived.await()

        assertTrue { echoPacket is MessagePacket }
        assertTrue { (echoPacket as MessagePacket).text == "Hello, World!" }

        nodeA.close()
        nodeB.close()
    }
}