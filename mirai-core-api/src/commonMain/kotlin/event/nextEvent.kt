/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("unused", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package net.mamoe.mirai.event

import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.BotEvent
import kotlin.coroutines.resume
import kotlin.reflect.KClass


/**
 * 挂起当前协程, 直到监听到事件 [E] 的广播并通过 [filter], 返回这个事件实例.
 *
 * @param timeoutMillis 超时. 单位为毫秒. `-1` 为不限制.
 * @param filter 过滤器. 返回 `true` 时表示得到了需要的实例. 返回 `false` 时表示继续监听
 *
 * @see EventChannel.subscribe 普通地监听一个事件
 * @see syncFromEvent 挂起当前协程, 并尝试从事件中同步一个值
 *
 * @throws TimeoutCancellationException 在超时后抛出.
 */
@JvmSynthetic
public suspend inline fun <reified E : Event> nextEvent(
    timeoutMillis: Long = -1,
    priority: EventPriority = EventPriority.MONITOR,
    crossinline filter: (E) -> Boolean = { true }
): E {
    require(timeoutMillis == -1L || timeoutMillis > 0) { "timeoutMillis must be -1 or > 0" }
    return withTimeoutOrCoroutineScope(timeoutMillis) {
        nextEventImpl(E::class, this, priority, filter)
    }
}


/**
 * 挂起当前协程, 直到监听到事件 [E] 的广播并通过 [filter], 返回这个事件实例.
 *
 * @param timeoutMillis 超时. 单位为毫秒.
 * @param filter 过滤器. 返回 `true` 时表示得到了需要的实例. 返回 `false` 时表示继续监听
 *
 * @see EventChannel.subscribe 普通地监听一个事件
 * @see syncFromEvent 挂起当前协程, 并尝试从事件中同步一个值
 *
 * @return 事件实例, 在超时后返回 `null`
 */
@JvmSynthetic
public suspend inline fun <reified E : Event> nextEventOrNull(
    timeoutMillis: Long,
    priority: EventPriority = EventPriority.MONITOR,
    crossinline filter: (E) -> Boolean = { true }
): E? {
    return withTimeoutOrNull(timeoutMillis) {
        nextEventImpl(E::class, this, priority, filter)
    }
}


@JvmSynthetic
@PublishedApi
internal suspend inline fun <E : Event> nextEventImpl(
    eventClass: KClass<E>,
    coroutineScope: CoroutineScope,
    priority: EventPriority,
    crossinline filter: (E) -> Boolean
): E = suspendCancellableCoroutine { cont ->
    val listener = coroutineScope.globalEventChannel()
        .parentJob(coroutineScope.coroutineContext[Job])
        .subscribe(eventClass, priority = priority) {
            if (!filter(this)) return@subscribe ListeningStatus.LISTENING

            try {
                cont.resume(this)
            } catch (e: Exception) {
            }
            return@subscribe ListeningStatus.STOPPED
        }

    cont.invokeOnCancellation {
        runCatching { listener.cancel("nextEvent outer scope cancelled", it) }
    }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "NOTHING_TO_INLINE")
@kotlin.internal.HidesMembers
@PublishedApi
internal inline fun <BaseEvent : Event> EventChannel<BaseEvent>.parentJob(job: Job?): EventChannel<BaseEvent> =
    if (job != null) parentJob(job) else this

@JvmSynthetic
@PublishedApi
internal suspend inline fun <E : BotEvent> nextBotEventImpl(
    bot: Bot,
    eventClass: KClass<E>,
    coroutineScope: CoroutineScope,
    priority: EventPriority
): E = suspendCancellableCoroutine { cont ->
    val listener = coroutineScope.globalEventChannel()
        .parentJob(coroutineScope.coroutineContext[Job])
        .subscribe(eventClass, priority = priority) {
            try {
                if (this.bot == bot) cont.resume(this)
            } catch (e: Exception) {
            }
            return@subscribe ListeningStatus.STOPPED
        }

    cont.invokeOnCancellation {
        runCatching { listener.cancel("nextEvent outer scope cancelled", it) }
    }
}

@JvmSynthetic
@PublishedApi
internal suspend inline fun <R> withTimeoutOrCoroutineScope(
    timeoutMillis: Long,
    useCoroutineScope: CoroutineScope? = null,
    noinline block: suspend CoroutineScope.() -> R
): R {
    require(timeoutMillis == -1L || timeoutMillis > 0) { "timeoutMillis must be -1 or > 0 " }

    return if (timeoutMillis == -1L) {
        if (useCoroutineScope == null) coroutineScope(block)
        else block(useCoroutineScope)
    } else {
        withTimeout(timeoutMillis, block)
    }
}

@JvmSynthetic
@PublishedApi
@Deprecated("For binary compatibility", level = DeprecationLevel.HIDDEN)
internal suspend inline fun <R> withTimeoutOrCoroutineScope(
    timeoutMillis: Long,
    noinline block: suspend CoroutineScope.() -> R
): R {
    require(timeoutMillis == -1L || timeoutMillis > 0) { "timeoutMillis must be -1 or > 0 " }

    return if (timeoutMillis == -1L) {
        coroutineScope(block)
    } else {
        withTimeout(timeoutMillis, block)
    }
}