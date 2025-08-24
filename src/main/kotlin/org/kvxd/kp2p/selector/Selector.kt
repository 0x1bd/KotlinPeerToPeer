package org.kvxd.kp2p.selector

import org.kvxd.kp2p.P2PNode
import org.kvxd.kp2p.Peer

interface Selector {

    suspend fun select(node: P2PNode, min: Int, max: Int): List<Peer>
}