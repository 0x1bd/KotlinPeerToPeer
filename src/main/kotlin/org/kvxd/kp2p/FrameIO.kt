package org.kvxd.kp2p

import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object FrameIO {

    private const val HEADER_SIZE = 2 + 1 + 1 + 4
    fun writeFrame(dos: DataOutputStream, type: WireMsgType, payload: ByteArray) {
        require(payload.size <= 64 * 1024) { "payload too large" }
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
            .putShort(Const.MAGIC).put(Const.VERSION).put(type.id).putInt(payload.size).array()
        dos.write(header); dos.write(payload); dos.flush()
    }

    fun readFrame(dis: DataInputStream): Pair<WireMsgType, ByteArray> {
        val magic = dis.readUnsignedShort().toShort(); if (magic != Const.MAGIC) throw IllegalStateException("bad magic")
        val version =
            dis.readUnsignedByte().toByte(); if (version != Const.VERSION) throw IllegalStateException("bad version")
        val t = dis.readUnsignedByte();
        val len = dis.readInt();
        val payload = dis.readNBytes(len)
        val mt = WireMsgType.values().firstOrNull { it.id.toInt() == t } ?: throw IllegalStateException("unknown type")
        return mt to payload
    }
}