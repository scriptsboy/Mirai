/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.internal.network.protocol.packet


import kotlinx.io.core.BytePacketBuilder
import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.buildPacket
import kotlinx.io.core.writeFully
import net.mamoe.mirai.internal.QQAndroidBot
import net.mamoe.mirai.internal.network.Packet
import net.mamoe.mirai.internal.network.handler.QQAndroidBotNetworkHandler
import net.mamoe.mirai.internal.network.QQAndroidClient
import net.mamoe.mirai.internal.utils.io.encryptAndWrite
import net.mamoe.mirai.internal.utils.io.writeHex
import net.mamoe.mirai.internal.utils.io.writeIntLVPacket

@kotlin.Suppress("unused")
internal class OutgoingPacketWithRespType<R : Packet?> constructor(
    name: String?,
    commandName: String,
    sequenceId: Int,
    delegate: ByteReadPacket
) : OutgoingPacket(name, commandName, sequenceId, delegate)

internal open class OutgoingPacket constructor(
    name: String?,
    val commandName: String,
    val sequenceId: Int,
    val delegate: ByteReadPacket
) {
    val name: String = name ?: commandName
}

internal suspend inline fun <E : Packet> OutgoingPacketWithRespType<E>.sendAndExpect(
    network: QQAndroidBotNetworkHandler,
    timeoutMillis: Long = 5000,
    retry: Int = 2
): E = network.run {
    return (this@sendAndExpect as OutgoingPacket).sendAndExpect(timeoutMillis, retry)
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@kotlin.internal.LowPriorityInOverloadResolution
internal suspend inline fun <E : Packet> OutgoingPacket.sendAndExpect(
    network: QQAndroidBotNetworkHandler,
    timeoutMillis: Long = 5000,
    retry: Int = 2
): E = network.run {
    return this@sendAndExpect.sendAndExpect(timeoutMillis, retry)
}

internal suspend inline fun <E : Packet> OutgoingPacketWithRespType<E>.sendAndExpect(
    bot: QQAndroidBot,
    timeoutMillis: Long = 5000,
    retry: Int = 2
): E = bot.network.run {
    return (this@sendAndExpect as OutgoingPacket).sendAndExpect(timeoutMillis, retry)
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@kotlin.internal.LowPriorityInOverloadResolution
internal suspend inline fun <E : Packet> OutgoingPacket.sendAndExpect(
    bot: QQAndroidBot,
    timeoutMillis: Long = 5000,
    retry: Int = 2
): E = bot.network.run {
    return this@sendAndExpect.sendAndExpect(timeoutMillis, retry)
}


internal val KEY_16_ZEROS = ByteArray(16)
internal val EMPTY_BYTE_ARRAY = ByteArray(0)

@Suppress("DuplicatedCode")
internal inline fun <R : Packet?> OutgoingPacketFactory<R>.buildOutgoingUniPacket(
    client: QQAndroidClient,
    bodyType: Byte = 1, // 1: PB?
    name: String? = this.commandName,
    commandName: String = this.commandName,
    key: ByteArray = client.wLoginSigInfo.d2Key,
    extraData: ByteReadPacket = BRP_STUB,
    sequenceId: Int = client.nextSsoSequenceId(),
    body: BytePacketBuilder.(sequenceId: Int) -> Unit
): OutgoingPacketWithRespType<R> {

    return OutgoingPacketWithRespType(name, commandName, sequenceId, buildPacket {
        writeIntLVPacket(lengthOffset = { it + 4 }) {
            writeInt(0x0B)
            writeByte(bodyType)
            writeInt(sequenceId)
            writeByte(0)
            client.uin.toString().let {
                writeInt(it.length + 4)
                writeStringUtf8(it)
            }
            encryptAndWrite(key) {
                writeUniPacket(commandName, client.outgoingPacketSessionId, extraData) {
                    body(sequenceId)
                }
            }
        }
    })
}


internal inline fun <R : Packet?> IncomingPacketFactory<R>.buildResponseUniPacket(
    client: QQAndroidClient,
    bodyType: Byte = 1, // 1: PB?
    name: String? = this.responseCommandName,
    commandName: String = this.responseCommandName,
    key: ByteArray = client.wLoginSigInfo.d2Key,
    extraData: ByteReadPacket = BRP_STUB,
    sequenceId: Int = client.nextSsoSequenceId(),
    body: BytePacketBuilder.(sequenceId: Int) -> Unit
): OutgoingPacketWithRespType<R> {
    @Suppress("DuplicatedCode")
    return OutgoingPacketWithRespType(name, commandName, sequenceId, buildPacket {
        writeIntLVPacket(lengthOffset = { it + 4 }) {
            writeInt(0x0B)
            writeByte(bodyType)
            writeInt(sequenceId)
            writeByte(0)
            client.uin.toString().let {
                writeInt(it.length + 4)
                writeStringUtf8(it)
            }
            encryptAndWrite(key) {
                writeUniPacket(commandName, client.outgoingPacketSessionId, extraData) {
                    body(sequenceId)
                }
            }
        }
    })
}


private inline fun BytePacketBuilder.writeUniPacket(
    commandName: String,
    unknownData: ByteArray,
    extraData: ByteReadPacket = BRP_STUB,
    body: BytePacketBuilder.() -> Unit
) {
    writeIntLVPacket(lengthOffset = { it + 4 }) {
        commandName.let {
            writeInt(it.length + 4)
            writeStringUtf8(it)
        }

        writeInt(4 + 4)
        writeFully(unknownData) //  02 B0 5B 8B

        if (extraData === BRP_STUB) {
            writeInt(0x04)
        } else {
            writeInt((extraData.remaining + 4).toInt())
            writePacket(extraData)
        }
    }

    // body
    writeIntLVPacket(lengthOffset = { it + 4 }, builder = body)
}

internal val NO_ENCRYPT: ByteArray = ByteArray(0)

/**
 * com.tencent.qphone.base.util.CodecWarpper#encodeRequest(int, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, byte[], int, int, java.lang.String, byte, byte, byte, byte[], byte[], boolean)
 */
internal inline fun <R : Packet?> OutgoingPacketFactory<R>.buildLoginOutgoingPacket(
    client: QQAndroidClient,
    bodyType: Byte,
    extraData: ByteArray = EMPTY_BYTE_ARRAY,
    name: String? = null,
    commandName: String = this.commandName,
    key: ByteArray = KEY_16_ZEROS,
    body: BytePacketBuilder.(sequenceId: Int) -> Unit
): OutgoingPacketWithRespType<R> {
    val sequenceId: Int = client.nextSsoSequenceId()

    return OutgoingPacketWithRespType(name, commandName, sequenceId, buildPacket {
        writeIntLVPacket(lengthOffset = { it + 4 }) {
            writeInt(0x00_00_00_0A)
            writeByte(bodyType)
            extraData.let {
                writeInt(it.size + 4)
                writeFully(it)
            }
            writeByte(0x00)

            client.uin.toString().let {
                writeInt(it.length + 4)
                writeStringUtf8(it)
            }

            if (key === NO_ENCRYPT) {
                body(sequenceId)
            } else {
                encryptAndWrite(key) { body(sequenceId) }
            }
        }
    })
}

private inline val BRP_STUB get() = ByteReadPacket.Empty


internal inline fun BytePacketBuilder.writeSsoPacket(
    client: QQAndroidClient,
    subAppId: Long,
    commandName: String,
    extraData: ByteReadPacket = BRP_STUB,
    unknownHex: String = "01 00 00 00 00 00 00 00 00 00 01 00",
    sequenceId: Int,
    body: BytePacketBuilder.() -> Unit
) {

    /* send
     * 00 00 00 78
     * 00 00 94 90
     * 20 02 ED BD
     * 20 02 ED BD
     * 01 00 00 00 00 00 00 00 00 00 01 00
     * 00 00 00 04
     * 00 00 00 13 48 65 61 72 74 62 65 61 74 2E 41 6C 69 76 65
     * 00 00 00 08 59 E7 DF 4F
     * 00 00 00 13 38 36 35 31 36 36 30 32 36 34 34 36 39 32 35
     * 00 00 00 04
     * 00 22 7C 34 36 30 30 30 31 39 31 39 38 37 36 30 32 36 7C 41 38 2E 32 2E 30 2E 32 37 66 36 65 61 39 36
     * 00 00 00 04
     *
     * 00 00 00 04
     */
    writeIntLVPacket(lengthOffset = { it + 4 }) {
        writeInt(sequenceId)
        writeInt(subAppId.toInt())
        writeInt(subAppId.toInt())
        writeHex(unknownHex)
        if (extraData === BRP_STUB || extraData.remaining == 0L) {
            // fast-path
            writeInt(0x04)
        } else {
            writeInt((extraData.remaining + 4).toInt())
            writePacket(extraData)
        }
        commandName.let {
            writeInt(it.length + 4)
            writeStringUtf8(it)
        }

        writeInt(4 + 4)
        writeFully(client.outgoingPacketSessionId) //  02 B0 5B 8B

        client.device.imei.let {
            writeInt(it.length + 4)
            writeStringUtf8(it)
        }

        writeInt(4)

        client.ksid.let {
            writeShort((it.size + 2).toShort())
            writeFully(it)
        }

        writeInt(4)
    }

    // body
    writeIntLVPacket(lengthOffset = { it + 4 }, builder = body)
}

internal fun BytePacketBuilder.writeOicqRequestPacket(
    client: QQAndroidClient,
    encryptMethod: EncryptMethod,
    commandId: Int,
    bodyBlock: BytePacketBuilder.() -> Unit
) {
    val body = encryptMethod.makeBody(client, bodyBlock)
    writeByte(0x02) // head
    writeShort((27 + 2 + body.remaining).toShort()) // orthodox algorithm
    writeShort(client.protocolVersion)
    writeShort(commandId.toShort())
    writeShort(1) // const??
    writeInt(client.uin.toInt())
    writeByte(3) // originally const
    writeByte(encryptMethod.id.toByte())
    writeByte(0) // const8_always_0
    writeInt(2) // originally const
    writeInt(client.appClientVersion)
    writeInt(0) // constp_always_0

    writePacket(body)

    writeByte(0x03) // tail
}