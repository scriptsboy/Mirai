/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("INVISIBLE_MEMBER")

package net.mamoe.mirai.internal.network.protocol.packet.chat

import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.readBytes
import net.mamoe.mirai.event.events.BotInvitedJoinGroupRequestEvent
import net.mamoe.mirai.event.events.BotLeaveEvent
import net.mamoe.mirai.event.events.MemberJoinRequestEvent
import net.mamoe.mirai.event.events.NewFriendRequestEvent
import net.mamoe.mirai.internal.QQAndroidBot
import net.mamoe.mirai.internal.message.contextualBugReportException
import net.mamoe.mirai.internal.network.MultiPacketByIterable
import net.mamoe.mirai.internal.network.Packet
import net.mamoe.mirai.internal.network.QQAndroidClient
import net.mamoe.mirai.internal.network.protocol.data.proto.Structmsg
import net.mamoe.mirai.internal.network.protocol.packet.OutgoingPacketFactory
import net.mamoe.mirai.internal.network.protocol.packet.buildOutgoingUniPacket
import net.mamoe.mirai.internal.utils._miraiContentToString
import net.mamoe.mirai.internal.utils.io.serialization.loadAs
import net.mamoe.mirai.internal.utils.io.serialization.writeProtoBuf
import kotlin.math.max

internal class NewContact {

    internal object SystemMsgNewFriend :
        OutgoingPacketFactory<Packet?>("ProfileService.Pb.ReqSystemMsgNew.Friend") {

        operator fun invoke(client: QQAndroidClient) = buildOutgoingUniPacket(client) {
            writeProtoBuf(
                Structmsg.ReqSystemMsgNew.serializer(),
                Structmsg.ReqSystemMsgNew(
                    checktype = 2,
                    flag = Structmsg.FlagInfo(
                        frdMsgDiscuss2ManyChat = 1,
                        frdMsgGetBusiCard = 1,
                        frdMsgNeedWaitingMsg = 1,
                        frdMsgUint32NeedAllUnreadMsg = 1,
                        grpMsgMaskInviteAutoJoin = 1
                    ),
                    friendMsgTypeFlag = 1,
                    isGetFrdRibbon = false,
                    isGetGrpRibbon = false,
                    msgNum = 20,
                    version = 1000
                )
            )
        }


        override suspend fun ByteReadPacket.decode(bot: QQAndroidBot): Packet? {
            readBytes().loadAs(Structmsg.RspSystemMsgNew.serializer()).run {
                return friendmsgs.filter {
                    it.msgTime >= bot.client.syncingController.latestMsgNewFriendTime
                }.mapNotNull { struct ->
                    if (!bot.client.syncingController.systemMsgNewFriendCacheList.addCache(
                            QQAndroidClient.MessageSvcSyncData.SystemMsgNewSyncId(
                                struct.msgSeq,
                                struct.msgTime
                            )
                        )
                    ) { // duplicate
                        return@mapNotNull null
                    }
                    struct.msg?.run {
                        NewFriendRequestEvent(
                            bot,
                            struct.msgSeq,
                            msgAdditional,
                            struct.reqUin,
                            groupCode,
                            reqUinNick
                        )
                    }
                }.let { packets ->
                    when {
                        packets.isEmpty() -> null
                        packets.size == 1 -> packets[0]
                        else -> MultiPacketByIterable(packets)
                    }
                }.also {
                    bot.client.syncingController.run {
                        latestMsgNewFriendTime = max(latestMsgNewFriendTime, friendmsgs.maxOfOrNull { it.msgTime } ?: 0)
                    }
                }
            }
        }

        internal object Action : OutgoingPacketFactory<Nothing?>("ProfileService.Pb.ReqSystemMsgAction.Friend") {

            operator fun invoke(
                client: QQAndroidClient,
                eventId: Long,
                fromId: Long,
                accept: Boolean,
                blackList: Boolean = false
            ) =
                buildOutgoingUniPacket(client) {
                    writeProtoBuf(
                        Structmsg.ReqSystemMsgAction.serializer(),
                        Structmsg.ReqSystemMsgAction(
                            actionInfo = Structmsg.SystemMsgActionInfo(
                                type = if (accept) 2 else 3,
                                addFrdSNInfo = Structmsg.AddFrdSNInfo(),
                                msg = "",
                                remark = "",
                                blacklist = !accept && blackList
                            ),
                            msgSeq = eventId,
                            reqUin = fromId,
                            srcId = 6,
                            subSrcId = 7,
                            subType = 1
                        )
                    )
                }

            override suspend fun ByteReadPacket.decode(bot: QQAndroidBot) = null
        }
    }


    internal object SystemMsgNewGroup :
        OutgoingPacketFactory<Packet?>("ProfileService.Pb.ReqSystemMsgNew.Group") {

        operator fun invoke(client: QQAndroidClient) = buildOutgoingUniPacket(client) {
            writeProtoBuf(
                Structmsg.ReqSystemMsgNew.serializer(),
                Structmsg.ReqSystemMsgNew(
                    checktype = 3,
                    flag = Structmsg.FlagInfo(
                        frdMsgDiscuss2ManyChat = 1,
                        frdMsgGetBusiCard = 0,
                        frdMsgNeedWaitingMsg = 1,
                        frdMsgUint32NeedAllUnreadMsg = 1,
                        grpMsgGetC2cInviteJoinGroup = 1,
                        grpMsgMaskInviteAutoJoin = 1,
                        grpMsgGetDisbandedByAdmin = 1,
                        grpMsgGetOfficialAccount = 1,
                        grpMsgGetPayInGroup = 1,
                        grpMsgGetQuitPayGroupMsgFlag = 1,
                        grpMsgGetTransferGroupMsgFlag = 1,
                        grpMsgHiddenGrp = 1,
                        grpMsgKickAdmin = 1,
                        grpMsgNeedAutoAdminWording = 1,
                        grpMsgNotAllowJoinGrpInviteNotFrd = 1,
                        grpMsgSupportInviteAutoJoin = 1,
                        grpMsgWordingDown = 1
                    ),
                    friendMsgTypeFlag = 1,
                    isGetFrdRibbon = false,
                    isGetGrpRibbon = false,
                    msgNum = 5,
                    version = 1000
                )
            )
        }


        override suspend fun ByteReadPacket.decode(bot: QQAndroidBot): Packet? {
            fun handleStruct(struct: Structmsg.StructMsg): Packet? {
                return struct.msg?.run {
                    when (subType) {
                        1 -> { // 处理被邀请入群 或 处理成员入群申请
                            when (groupMsgType) {
                                1 -> {
                                    // 成员申请入群
                                    MemberJoinRequestEvent(
                                        bot, struct.msgSeq, msgAdditional,
                                        struct.reqUin, groupCode, groupName, reqUinNick
                                    )
                                }
                                2 -> {
                                    // Bot 被邀请入群
                                    BotInvitedJoinGroupRequestEvent(
                                        bot, struct.msgSeq, actionUin,
                                        groupCode, groupName, actionUinNick
                                    )
                                }
                                22 -> {
                                    // 成员邀请入群
                                    MemberJoinRequestEvent(
                                        bot, struct.msgSeq, msgAdditional,
                                        struct.reqUin, groupCode, groupName, reqUinNick, actionUin
                                    )
                                }
                                else -> throw contextualBugReportException(
                                    "parse SystemMsgNewGroup, subType=1",
                                    this._miraiContentToString(),
                                    additional = "并尽量描述此时机器人是否正被邀请加入群, 或者是有有新群员加入此群"
                                )
                            }
                        }
                        2 -> { // 被邀请入群, 自动同意, 不需处理

//                            val group = bot.getNewGroup(groupCode) ?: return null
//                            val invitor = group[actionUin]
//
//                            BotJoinGroupEvent.Invite(invitor)
                            null
                        }
                        3 -> { // 已被请他管理员处理
                            null
                        }
                        5 -> {
                            val group = bot.getGroup(groupCode) ?: return null
                            when (groupMsgType) {
                                3 -> {
                                    // https://github.com/mamoe/mirai/issues/651
                                    // msgDescribe=将你设置为管理员
                                    // msgTitle=管理员设置
                                    null
                                }
                                13 -> { // 成员主动退出, 机器人是管理员, 接到通知
                                    // 但无法获取是哪个成员.
                                    null
                                }
                                7 -> { // 机器人被踢
                                    val operator = group[actionUin] ?: return null
                                    BotLeaveEvent.Kick(operator)
                                }
                                else -> {
                                    throw contextualBugReportException(
                                        "解析 NewContact.SystemMsgNewGroup, subType=5, groupMsgType=$groupMsgType",
                                        this._miraiContentToString(),
                                        null,
                                        "并描述此时机器人是否被踢出群等"
                                    )
                                }
                            }
                        }
                        else -> throw contextualBugReportException(
                            "解析 NewContact.SystemMsgNewGroup, subType=$subType, groupMsgType=$groupMsgType",
                            forDebug = this._miraiContentToString(),
                            additional = "并尽量描述此时机器人是否正被邀请加入群, 或者是有有新群员加入此群"
                        )
                    }
                }
            }

            return readBytes().loadAs(Structmsg.RspSystemMsgNew.serializer()).run {
                groupmsgs.filter {
                    it.msgTime >= bot.client.syncingController.latestMsgNewGroupTime
                }.mapNotNull { struct ->
                    if (!bot.client.syncingController.systemMsgNewGroupCacheList.addCache(
                            QQAndroidClient.MessageSvcSyncData.SystemMsgNewSyncId(
                                struct.msgSeq,
                                struct.msgTime
                            )
                        )
                    ) { // duplicate
                        return@mapNotNull null
                    }
                    handleStruct(struct)
                }.let { packets ->
                    when {
                        packets.isEmpty() -> null
                        packets.size == 1 -> packets[0]
                        else -> MultiPacketByIterable(packets)
                    }
                }.also {
                    bot.client.syncingController.run {
                        latestMsgNewGroupTime = max(latestMsgNewGroupTime, groupmsgs.maxOfOrNull { it.msgTime } ?: 0)
                    }
                }
            }
        }

        internal object Action : OutgoingPacketFactory<Nothing?>("ProfileService.Pb.ReqSystemMsgAction.Group") {

            operator fun invoke(
                client: QQAndroidClient,
                eventId: Long,
                fromId: Long,
                groupId: Long,
                isInvited: Boolean,
                accept: Boolean?,
                blackList: Boolean = false,
                message: String = ""
            ) =
                buildOutgoingUniPacket(client) {
                    writeProtoBuf(
                        Structmsg.ReqSystemMsgAction.serializer(),
                        Structmsg.ReqSystemMsgAction(
                            actionInfo = Structmsg.SystemMsgActionInfo(
                                type = when (accept) {
                                    null -> 14 // ignore
                                    true -> 11 // accept
                                    false -> 12 // reject
                                },
                                groupCode = groupId,
                                msg = message,
                                remark = "",
                                blacklist = blackList
                            ),
                            groupMsgType = if (isInvited) 2 else 1,
                            language = 1000,
                            msgSeq = eventId,
                            reqUin = fromId,
                            srcId = 3,
                            subSrcId = if (isInvited) 10016 else 31,
                            subType = 1
                        )
                    )
                }

            override suspend fun ByteReadPacket.decode(bot: QQAndroidBot) = null
        }
    }
}
