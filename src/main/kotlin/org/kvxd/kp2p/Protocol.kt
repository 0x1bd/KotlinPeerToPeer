package org.kvxd.kp2p

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@OptIn(ExperimentalSerializationApi::class)
class Protocol internal constructor(internal val serializersModule: SerializersModule) {

    val cbor = Cbor {
        serializersModule = this@Protocol.serializersModule
    }

    fun encodePacket(packet: Packet): ByteArray =
        cbor.encodeToByteArray(packet)

    fun decodePacket(byteArray: ByteArray): Packet =
        cbor.decodeFromByteArray(byteArray)

    fun encodeNodeId(nodeId: String): ByteArray =
        cbor.encodeToByteArray(nodeId)

    fun decodeNodeId(byteArray: ByteArray): String =
        cbor.decodeFromByteArray(byteArray)
}

inline fun <reified T : Packet> PolymorphicModuleBuilder<Packet>.register() {
    subclass(T::class)
}

fun protocolBuilder(block: PolymorphicModuleBuilder<Packet>.() -> Unit): Protocol {
    val module = SerializersModule {
        polymorphic(Packet::class) {
            block()
        }
    }

    return Protocol(module)
}