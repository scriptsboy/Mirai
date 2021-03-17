/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.utils

import kotlinx.io.core.toByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.File

@Serializable
public class DeviceInfo(
    public val display: ByteArray,
    public val product: ByteArray,
    public val device: ByteArray,
    public val board: ByteArray,
    public val brand: ByteArray,
    public val model: ByteArray,
    public val bootloader: ByteArray,
    public val fingerprint: ByteArray,
    public val bootId: ByteArray,
    public val procVersion: ByteArray,
    public val baseBand: ByteArray,
    public val version: Version,
    public val simInfo: ByteArray,
    public val osType: ByteArray,
    public val macAddress: ByteArray,
    public val wifiBSSID: ByteArray,
    public val wifiSSID: ByteArray,
    public val imsiMd5: ByteArray,
    public val imei: String,
    public val apn: ByteArray
) {
    public val androidId: ByteArray get() = display
    public val ipAddress: ByteArray get() = byteArrayOf(192.toByte(), 168.toByte(), 1, 123)

    init {
        require(imsiMd5.size == 16) { "Bad `imsiMd5.size`. Required 16, given ${imsiMd5.size}." }
    }

    @Transient
    @MiraiInternalApi
    public val guid: ByteArray = generateGuid(androidId, macAddress)

    @Serializable
    public class Version(
        public val incremental: ByteArray = "5891938".toByteArray(),
        public val release: ByteArray = "10".toByteArray(),
        public val codename: ByteArray = "REL".toByteArray(),
        public val sdk: Int = 29
    )

    public companion object {

        /**
         * 加载一个设备信息. 若文件不存在或为空则随机并创建一个设备信息保存.
         */
        @JvmOverloads
        @JvmStatic
        @JvmName("from")
        public fun File.loadAsDeviceInfo(
            json: Json = Json
        ): DeviceInfo {
            if (!this.exists() || this.length() == 0L) {
                return random().also {
                    this.writeText(json.encodeToString(serializer(), it))
                }
            }
            return json.decodeFromString(serializer(), this.readText())
        }


        @JvmStatic
        public fun random(): DeviceInfo {
            return DeviceInfo(
                display = "MIRAI.${getRandomString(6, '0'..'9')}.001".toByteArray(),
                product = "mirai".toByteArray(),
                device = "mirai".toByteArray(),
                board = "mirai".toByteArray(),
                brand = "mamoe".toByteArray(),
                model = "mirai".toByteArray(),
                bootloader = "unknown".toByteArray(),
                fingerprint = "mamoe/mirai/mirai:10/MIRAI.200122.001/${getRandomIntString(7)}:user/release-keys".toByteArray(),
                bootId = generateUUID(getRandomByteArray(16).md5()).toByteArray(),
                procVersion = "Linux version 3.0.31-${getRandomString(8)} (android-build@xxx.xxx.xxx.xxx.com)".toByteArray(),
                baseBand = byteArrayOf(),
                version = Version(),
                simInfo = "T-Mobile".toByteArray(),
                osType = "android".toByteArray(),
                macAddress = "02:00:00:00:00:00".toByteArray(),
                wifiBSSID = "02:00:00:00:00:00".toByteArray(),
                wifiSSID = "<unknown ssid>".toByteArray(),
                imsiMd5 = getRandomByteArray(16).md5(),
                imei = getRandomIntString(15),
                apn = "wifi".toByteArray()
            )
        }
    }
}

public fun DeviceInfo.generateDeviceInfoData(): ByteArray {
    @Serializable
    class DevInfo(
        @ProtoNumber(1) val bootloader: ByteArray,
        @ProtoNumber(2) val procVersion: ByteArray,
        @ProtoNumber(3) val codename: ByteArray,
        @ProtoNumber(4) val incremental: ByteArray,
        @ProtoNumber(5) val fingerprint: ByteArray,
        @ProtoNumber(6) val bootId: ByteArray,
        @ProtoNumber(7) val androidId: ByteArray,
        @ProtoNumber(8) val baseBand: ByteArray,
        @ProtoNumber(9) val innerVersion: ByteArray
    )

    return ProtoBuf.encodeToByteArray(
        DevInfo.serializer(), DevInfo(
            bootloader,
            procVersion,
            version.codename,
            version.incremental,
            fingerprint,
            bootId,
            androidId,
            baseBand,
            version.incremental
        )
    )
}

/**
 * Defaults "%4;7t>;28<fc.5*6".toByteArray()
 */
@Suppress("RemoveRedundantQualifierName") // bug
private fun generateGuid(androidId: ByteArray, macAddress: ByteArray): ByteArray =
    (androidId + macAddress).md5()


/*
fun DeviceInfo.toOidb0x769DeviceInfo() : Oidb0x769.DeviceInfo = Oidb0x769.DeviceInfo(
    brand = brand.encodeToString(),
    model = model.encodeToString(),
    os = Oidb0x769.OS(
        version = version.release.encodeToString(),
        sdk = version.sdk.toString(),
        kernel = version.kernel
    )
)
*/