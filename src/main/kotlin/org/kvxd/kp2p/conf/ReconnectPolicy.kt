package org.kvxd.kp2p.conf

data class ReconnectPolicy(
    val initialDelayMs: Long = 500,
    val maxDelayMs: Long = 30_000,
    val multiplier: Double = 1.5,
    val maxAttempts: Int = Int.MAX_VALUE
)