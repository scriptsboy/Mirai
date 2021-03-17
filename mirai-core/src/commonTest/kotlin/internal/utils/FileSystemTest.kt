/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */


package net.mamoe.mirai.internal.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class FileSystemTest {

    private val fs = FileSystem

    @Test
    fun testLegitimacy() {
        fs.checkLegitimacy("a")
        assertFailsWith<IllegalArgumentException> { fs.checkLegitimacy("a:") }
        assertFailsWith<IllegalArgumentException> { fs.checkLegitimacy("?a") }
    }

    @Test
    fun testNormalize() {
        assertEquals("/", fs.normalize("/"))
        assertEquals("/", fs.normalize("\\"))
        assertEquals("/foo", fs.normalize("/foo"))
        assertEquals("/foo", fs.normalize("\\foo"))
        assertEquals("foo", fs.normalize("foo"))
        assertEquals("foo/", fs.normalize("foo/"))

        assertEquals("/bar", fs.normalize("\\foo", "/bar"))
        assertEquals("/foo/bar", fs.normalize("\\foo", "bar"))
    }
}