/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.data

import net.mamoe.mirai.contact.User

/**
 * 用户详细资料
 *
 * @see User.queryProfile
 * @suppress 使用这个接口是稳定的，但继承不稳定。将来可能会有新的属性添加。
 * @since 2.1
 */
public interface UserProfile {
    public val nickname: String
    public val email: String
    public val age: Int
    public val qLevel: Int
    public val sex: Sex

    /**
     * 个性签名
     */
    public val sign: String

    public enum class Sex {
        MALE,
        FEMALE,

        /** 保密 */
        UNKNOWN;
    }
}
