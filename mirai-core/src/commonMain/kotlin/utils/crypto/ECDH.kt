/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.internal.utils.crypto

import net.mamoe.mirai.utils.chunkedHexToBytes

internal expect interface ECDHPrivateKey

internal expect interface ECDHPublicKey {
    fun getEncoded(): ByteArray
}

internal expect class ECDHKeyPairImpl : ECDHKeyPair

internal interface ECDHKeyPair {
    val privateKey: ECDHPrivateKey
    val publicKey: ECDHPublicKey

    /**
     * 私匙和固定公匙([initialPublicKey]) 计算得到的 shareKey
     */
    val initialShareKey: ByteArray

    object DefaultStub : ECDHKeyPair {
        val defaultPublicKey = "020b03cf3d99541f29ffec281bebbd4ea211292ac1f53d7128".chunkedHexToBytes()
        val defaultShareKey = "4da0f614fc9f29c2054c77048a6566d7".chunkedHexToBytes()

        override val privateKey: Nothing get() = error("stub!")
        override val publicKey: Nothing get() = error("stub!")
        override val initialShareKey: ByteArray get() = defaultShareKey
    }
}

/**
 * 椭圆曲线密码, ECDH 加密
 */
internal expect class ECDH(keyPair: ECDHKeyPair) {
    val keyPair: ECDHKeyPair

    /**
     * 由 [keyPair] 的私匙和 [peerPublicKey] 计算 shareKey
     */
    fun calculateShareKeyByPeerPublicKey(peerPublicKey: ECDHPublicKey): ByteArray

    companion object {
        val isECDHAvailable: Boolean

        /**
         * 由完整的 publicKey ByteArray 得到 [ECDHPublicKey]
         */
        fun constructPublicKey(key: ByteArray): ECDHPublicKey

        /**
         * 生成随机密匙对
         */
        fun generateKeyPair(): ECDHKeyPair

        /**
         * 由一对密匙计算 shareKey
         */
        fun calculateShareKey(privateKey: ECDHPrivateKey, publicKey: ECDHPublicKey): ByteArray
    }

    override fun toString(): String
}

@Suppress("FunctionName")
internal expect fun ECDH(): ECDH

// gen by p-256
//3059301306072A8648CE3D020106082A8648CE3D03010703420004FA540CB3F755D0A6572338777A4D0BEAFA86664D53040B27331CBF1B7F3C226CE8A1C05EFA9028F85510B103D8175172895C9F9FE4C80A47894BCA2BE569BFCB
//3059301306072A8648CE3D020106082A8648CE3D03010703420004949D41D7C14B92F0CB94B6232FB87BA51B0D5AB661FBAF95599A97472FFC4F50BC8CEC5865E79DB3782459A6E9A2298954CD198A25274CEEA8F925342D763D62

/*
// p-256
    get() = ECDH.constructPublicKey(
        ("3059301306072A8648CE3D020106082A8648CE3D03010703420004" +
                "EBCA94D733E399B2DB96EACDD3F69A8BB0F74224E2B44E3357812211D2E62EFB" +
                "C91BB553098E25E33A799ADC7F76FEB208DA7C6522CDB0719A305180CC54A82E"
                ).chunkedHexToBytes()
    )
* */


// this is for old curve
internal val initialPublicKey
    get() = ECDH.constructPublicKey("3046301006072A8648CE3D020106052B8104001F03320004928D8850673088B343264E0C6BACB8496D697799F37211DEB25BB73906CB089FEA9639B4E0260498B51A992D50813DA8".chunkedHexToBytes())

private val head1 = "302E301006072A8648CE3D020106052B8104001F031A00".chunkedHexToBytes()
private val head2 = "3046301006072A8648CE3D020106052B8104001F03320004".chunkedHexToBytes()
internal fun ByteArray.adjustToPublicKey(): ECDHPublicKey {
    val head = if (this.size < 30) head1 else head2

    return ECDH.constructPublicKey(head + this)
}