package org.kvxd.kp2p.discovery

data class DiscoveredPeer(val nodeId: String, val host: String, val port: Int, val seenAt: Long, val via: String)
