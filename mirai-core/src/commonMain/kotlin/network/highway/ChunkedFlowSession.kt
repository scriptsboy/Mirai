/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.internal.network.highway

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.core.Closeable
import net.mamoe.mirai.utils.runBIO
import net.mamoe.mirai.utils.toLongUnsigned
import net.mamoe.mirai.utils.withUse
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal class ChunkedFlowSession<T>(
    private val input: InputStream,
    private val buffer: ByteArray,
    private val callback: Highway.ProgressionCallback? = null,
    private val mapper: (buffer: ByteArray, size: Int, offset: Long) -> T,
) : Closeable {
    override fun close() {
        input.close()
    }

    private var offset = AtomicLong(0L)

    internal suspend inline fun useAll(crossinline block: suspend (T) -> Unit) {
        contract { callsInPlace(block, InvocationKind.UNKNOWN) }
        withUse {
            while (true) {
                val size = runBIO { input.read(buffer) }
                if (size == -1) return
                block(mapper(buffer, size, offset.getAndAdd(size.toLongUnsigned())))
                callback?.onProgression(offset.get())
            }
        }
    }

    internal suspend fun asFlow(): Flow<T> = flow { useAll { emit(it) } } // 'single thread' producer
}