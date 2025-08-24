package org.kvxd.kp2p.conf

class DiscoveryConfig {

    internal val bootstrap = mutableListOf<String>()

    fun bootstrap(block: MutableList<String>.() -> Unit) {
        bootstrap.block()
    }
}
