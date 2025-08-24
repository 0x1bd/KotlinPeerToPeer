package org.kvxd.kp2p.selector

import org.kvxd.kp2p.P2PNode
import org.kvxd.kp2p.Peer
import kotlin.math.max
import kotlin.math.min

object Selectors {

    fun random(min: Int = 1, max: Int = min) = object : Selector {
        init {
            require(min >= 1 && max >= min)
        }

        override suspend fun select(node: P2PNode, min: Int, max: Int): List<Peer> {
            val all = node.connectedPeers(); if (all.size < min) throw IllegalStateException("not enough peers")
            val take = (min..min(max, all.size)).random()
            return all.shuffled().take(take)
        }
    }

    fun roundRobin() = object : Selector {
        private var idx = 0
        override suspend fun select(node: P2PNode, min: Int, max: Int): List<Peer> {
            val all = node.connectedPeers(); if (all.isEmpty()) return emptyList()
            val take = min(max, max(min, 1))
            val out = mutableListOf<Peer>()
            repeat(take) {
                out += all[idx % all.size]
                idx++
            }
            return out
        }
    }
}