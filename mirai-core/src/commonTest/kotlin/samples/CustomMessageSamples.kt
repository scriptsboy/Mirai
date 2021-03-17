/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_OVERRIDE")

package samples

import kotlinx.serialization.Serializable
import net.mamoe.mirai.message.data.CustomMessage
import net.mamoe.mirai.message.data.CustomMessageMetadata


/**
 * 定义一个自定义消息类型.
 * 在消息链中加入这个元素, 即可像普通元素一样发送和接收 (自动解析).
 */
@Serializable
data class CustomMessageIdentifier(
    val identifier1: Long,
    val custom: String
) : CustomMessageMetadata() {
    // 可使用 JsonSerializerFactory 或 ProtoBufSerializerFactory
    companion object Factory : CustomMessage.ProtoBufSerializerFactory<CustomMessageIdentifier>(
        "myMessage.CustomMessageIdentifier"
    )

    override fun getFactory(): Factory = Factory
}