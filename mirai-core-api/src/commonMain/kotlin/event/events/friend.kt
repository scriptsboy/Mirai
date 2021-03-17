/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:JvmMultifileClass
@file:JvmName("BotEventsKt")
@file:Suppress("FunctionName", "unused", "DEPRECATION_ERROR")

package net.mamoe.mirai.event.events

import net.mamoe.kjbb.JvmBlockingBridge
import net.mamoe.mirai.Bot
import net.mamoe.mirai.Mirai
import net.mamoe.mirai.contact.Friend
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.event.AbstractEvent
import net.mamoe.mirai.internal.network.Packet
import net.mamoe.mirai.utils.MiraiInternalApi
import java.util.concurrent.atomic.AtomicBoolean


/**
 * 好友昵称改变事件. 目前仅支持解析 (来自 PC 端的修改).
 */
public data class FriendRemarkChangeEvent internal constructor(
    public override val friend: Friend,
    public val oldRemark: String,
    public val newRemark: String
) : FriendEvent, Packet, AbstractEvent(), FriendInfoChangeEvent

/**
 * 成功添加了一个新好友的事件
 */
public data class FriendAddEvent @MiraiInternalApi constructor(
    /**
     * 新好友. 已经添加到 [Bot.friends]
     */
    public override val friend: Friend
) : FriendEvent, Packet, AbstractEvent(), FriendInfoChangeEvent

/**
 * 好友已被删除或主动删除的事件.
 */
public data class FriendDeleteEvent internal constructor(
    public override val friend: Friend
) : FriendEvent, Packet, AbstractEvent(), FriendInfoChangeEvent

/**
 * 一个账号请求添加机器人为好友的事件
 */
@Suppress("DEPRECATION")
public data class NewFriendRequestEvent internal constructor(
    public override val bot: Bot,
    /**
     * 事件唯一识别号
     */
    public val eventId: Long,
    /**
     * 申请好友消息
     */
    public val message: String,
    /**
     * 请求人 [User.id]
     */
    public val fromId: Long,
    /**
     * 来自群 [Group.id], 其他途径时为 0
     */
    public val fromGroupId: Long,
    /**
     * 群名片或好友昵称
     */
    public val fromNick: String
) : BotEvent, Packet, AbstractEvent(), FriendInfoChangeEvent {
    @JvmField
    internal val responded: AtomicBoolean = AtomicBoolean(false)

    /**
     * @return 申请人来自的群. 当申请人来自其他途径申请时为 `null`
     */
    public val fromGroup: Group? = if (fromGroupId == 0L) null else bot.getGroup(fromGroupId)

    @JvmBlockingBridge
    public suspend fun accept(): Unit = Mirai.acceptNewFriendRequest(this)

    @JvmBlockingBridge
    public suspend fun reject(blackList: Boolean = false): Unit = Mirai.rejectNewFriendRequest(this, blackList)
}


/**
 * [Friend] 头像被修改. 在此事件广播前就已经修改完毕.
 */
public data class FriendAvatarChangedEvent internal constructor(
    public override val friend: Friend
) : FriendEvent, Packet, AbstractEvent()

/**
 * [Friend] 昵称改变事件, 在此事件广播时好友已经完成改名
 * @see BotNickChangedEvent
 */
public data class FriendNickChangedEvent internal constructor(
    public override val friend: Friend,
    public val from: String,
    public val to: String
) : FriendEvent, Packet, AbstractEvent(), FriendInfoChangeEvent

/**
 * 好友输入状态改变的事件，当开始输入文字、退出聊天窗口或清空输入框时会触发此事件
 */
public data class FriendInputStatusChangedEvent internal constructor(
    public override val friend: Friend,
    public val inputting: Boolean

) : FriendEvent, Packet, AbstractEvent()