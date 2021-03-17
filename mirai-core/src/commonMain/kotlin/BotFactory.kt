/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */
@file:Suppress(
    "FunctionName", "INAPPLICABLE_JVM_NAME", "DEPRECATION_ERROR", "DeprecatedCallableAddReplaceWith",
    "OverridingDeprecatedMember"
)

package net.mamoe.mirai.internal

import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.utils.BotConfiguration

/**
 * QQ for Android
 */
@Deprecated(
    "BotFactoryImpl was accidentally made public, use BotFactory.INSTANCE instead",
    ReplaceWith("BotFactory", "net.mamoe.mirai.BotFactory"),
    DeprecationLevel.WARNING,
)
public object BotFactoryImpl : BotFactory {

    /**
     * 使用指定的 [配置][configuration] 构造 [Bot] 实例
     */
    override fun newBot(qq: Long, password: String, configuration: BotConfiguration): Bot {
        return QQAndroidBot(BotAccount(qq, password), configuration)
    }

    /**
     * 使用指定的 [配置][configuration] 构造 [Bot] 实例
     */
    override fun newBot(
        qq: Long,
        passwordMd5: ByteArray,
        configuration: BotConfiguration
    ): Bot = QQAndroidBot(BotAccount(qq, passwordMd5), configuration)
}