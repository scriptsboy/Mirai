/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */
package  net.mamoe.mirai.message.data

import net.mamoe.mirai.utils.safeCast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue


internal class TestConstrainSingleMessage : ConstrainSingle, Any() {
    companion object Key : AbstractMessageKey<TestConstrainSingleMessage>({ it.safeCast() })

    override fun toString(): String = "<TestConstrainSingleMessage#${super.hashCode()}>"
    override fun contentToString(): String = ""

    override val key: MessageKey<TestConstrainSingleMessage>
        get() = Key
}


internal class ConstrainSingleTest {


    @Test
    fun testSinglePlusChain() {
        val result = PlainText("te") + buildMessageChain {
            add(TestConstrainSingleMessage())
            add("st")
        }
        assertEquals(3, result.size)
        assertEquals(result.contentToString(), "test")
    }

    @Test
    fun testSinglePlusChainConstrain() {
        val chain = buildMessageChain {
            add(TestConstrainSingleMessage())
            add("st")
        }
        val result = TestConstrainSingleMessage() + chain
        assertEquals(chain, result)
        assertEquals(2, result.size)
        assertEquals(result.contentToString(), "st")
        assertTrue { result.first() is TestConstrainSingleMessage }
    }

    @Test
    fun testChainPlusSingle() {
        val new = TestConstrainSingleMessage()

        val result = buildMessageChain {
            add(" ")
            add(Face(Face.OK))
            add(TestConstrainSingleMessage())
            add(
                PlainText("ss")
                        + " "
            )
        } + buildMessageChain {
            add(PlainText("p "))
            add(new)
            add(PlainText("test"))
        }

        assertEquals(7, result.size)
        assertEquals(" [OK]ss p test", result.contentToString())
        result as MessageChainImpl
        assertSame(new, result.delegate.toTypedArray()[2])
    }

    @Test // net.mamoe.mirai/message/data/MessageChain.kt:441
    fun testConstrainSingleInSequence() {
        val last = TestConstrainSingleMessage()
        val sequence: Sequence<SingleMessage> = sequenceOf(
            TestConstrainSingleMessage(),
            TestConstrainSingleMessage(),
            last
        )

        val result = sequence.constrainSingleMessages()
        assertEquals(result.count(), 1)
        assertSame(result.single(), last)
    }

    @Test // net.mamoe.mirai/message/data/MessageChain.kt:441
    fun testConstrainSingleOrderInSequence() {
        val last = TestConstrainSingleMessage()
        val sequence: Sequence<SingleMessage> = sequenceOf(
            TestConstrainSingleMessage(), // last should replace here
            PlainText("test"),
            TestConstrainSingleMessage(),
            last
        )

        val result = sequence.constrainSingleMessages()
        assertEquals(result.count(), 2)
        assertSame(result.first(), last)
    }


    @Test
    fun testConversions() {
        val lastSingle = TestConstrainSingleMessage()
        val list: List<SingleMessage> = listOf(
            PlainText("test"),
            TestConstrainSingleMessage(),
            TestConstrainSingleMessage(),
            PlainText("foo"),
            TestConstrainSingleMessage(),
            lastSingle
        )

        // Collection<SingleMessage>.asMessageChain()
        assertEquals("test${lastSingle}foo", list.toMessageChain().toString())

        // Collection<Message>.asMessageChain()
        @Suppress("USELESS_CAST")
        assertEquals(
            "test${lastSingle}foo",
            list.map { it as Message }.toMessageChain().toString()
        )

        // Iterable<SingleMessage>.asMessageChain()
        assertEquals("test${lastSingle}foo", list.asIterable().toMessageChain().toString())

        // Iterable<Message>.asMessageChain()
        @Suppress("USELESS_CAST")
        assertEquals(
            "test${lastSingle}foo",
            list.map { it as Message }.asIterable().toMessageChain().toString()
        )

        // Sequence<SingleMessage>.asMessageChain()
        assertEquals("test${lastSingle}foo", list.asSequence().toMessageChain().toString())

        // Sequence<Message>.asMessageChain()
        @Suppress("USELESS_CAST")
        assertEquals(
            "test${lastSingle}foo",
            list.map { it as Message }.asSequence().toMessageChain().toString()
        )
    }
}