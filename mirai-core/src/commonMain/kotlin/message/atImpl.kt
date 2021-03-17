/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.internal.message

import kotlinx.io.core.buildPacket
import kotlinx.io.core.readBytes
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.AtAll


internal fun At.toJceData(group: Group?): ImMsgBody.Text {
    val text = "@${group?.members?.get(this.target)?.nameCardOrNick ?: target}"
    return ImMsgBody.Text(
        str = text,
        attr6Buf = buildPacket {
            // MessageForText$AtTroopMemberInfo
            writeShort(1) // const
            writeShort(0) // startPos
            writeShort(text.length.toShort()) // textLen
            writeByte(0) // flag, may=1
            writeInt(target.toInt()) // uin
            writeShort(0) // const
        }.readBytes()
    )
}


internal val atAllData = ImMsgBody.Elem(
    text = ImMsgBody.Text(
        str = AtAll.display,
        attr6Buf = buildPacket {
            // MessageForText$AtTroopMemberInfo
            writeShort(1) // const
            writeShort(0) // startPos
            writeShort(AtAll.display.length.toShort()) // textLen
            writeByte(1) // flag, may=1
            writeInt(0) // uin
            writeShort(0) // const
        }.readBytes()
    )
)
