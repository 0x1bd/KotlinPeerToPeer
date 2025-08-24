package org.kvxd.kp2p

import kotlinx.serialization.Serializable

@Serializable
enum class WireMsgType(val id: Byte) {

    PACKET(1),
    HANDSHAKE(2),
    GOSSIP(3)
}