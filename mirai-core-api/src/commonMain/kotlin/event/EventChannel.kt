/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "MemberVisibilityCanBePrivate", "unused")

@file:JvmMultifileClass
@file:JvmName("EventChannelKt")


package net.mamoe.mirai.event

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.ConcurrencyKind.CONCURRENT
import net.mamoe.mirai.event.ConcurrencyKind.LOCKED
import net.mamoe.mirai.event.events.BotEvent
import net.mamoe.mirai.internal.event.GlobalEventListeners
import net.mamoe.mirai.internal.event.Handler
import net.mamoe.mirai.internal.event.ListenerRegistry
import net.mamoe.mirai.internal.event.registerEventHandler
import net.mamoe.mirai.utils.MiraiExperimentalApi
import net.mamoe.mirai.utils.MiraiLogger
import net.mamoe.mirai.utils.cast
import java.util.function.Consumer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.internal.LowPriorityInOverloadResolution
import kotlin.reflect.KClass

/**
 * 事件通道. 事件通道是监听事件的入口. **在不同的事件通道中可以监听到不同类型的事件**.
 *
 * [GlobalEventChannel] 是最大的通道: 所有的事件都可以在 [GlobalEventChannel] 监听到.
 * 通过 [Bot.eventChannel] 得到的通道只能监听到来自这个 [Bot] 的事件.
 *
 * ### 对通道的操作
 * - "缩窄" 通道: 通过 [EventChannel.filter]. 例如 `filter { it is BotEvent }` 得到一个只能监听到 [BotEvent] 的事件通道.
 * - 转换为 Kotlin 协程 [Channel]: [EventChannel.asChannel]
 * - 添加 [CoroutineContext]: [context], [parentJob], [parentScope], [exceptionHandler]
 *
 * ### 创建事件监听
 * - [EventChannel.subscribe] 创建带条件的一个事件监听器.
 * - [EventChannel.subscribeAlways] 创建一个总是监听事件的事件监听器.
 * - [EventChannel.subscribeOnce] 创建一个只监听单次的事件监听器.
 *
 * ### 获取事件通道
 * - 全局事件通道: [GlobalEventChannel]
 * - [BotEvent] 通道: [Bot.eventChannel]
 *
 * @see subscribe
 */
public open class EventChannel<out BaseEvent : Event> @JvmOverloads internal constructor(
    public val baseEventClass: KClass<out BaseEvent>,
    /**
     * 此事件通道的默认 [CoroutineScope.coroutineContext]. 将会被添加给所有注册的事件监听器.
     */
    public val defaultCoroutineContext: CoroutineContext = EmptyCoroutineContext
) {

    /**
     * 创建事件监听并将监听结果发送在 [Channel]. 将返回值 [Channel] [关闭][Channel.close] 时将会同时关闭事件监听.
     *
     * 标注 [ExperimentalCoroutinesApi] 是因为使用了 [Channel.invokeOnClose]
     *
     * @param capacity Channel 容量. 详见 [Channel] 构造.
     *
     * @see subscribeAlways
     * @see Channel
     */
    @MiraiExperimentalApi
    @ExperimentalCoroutinesApi
    public fun asChannel(
        capacity: Int = Channel.RENDEZVOUS,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        concurrency: ConcurrencyKind = CONCURRENT,
        priority: EventPriority = EventPriority.NORMAL,
    ): Channel<out BaseEvent> {
        val channel = Channel<BaseEvent>(capacity)
        val listener = subscribeAlways(baseEventClass, coroutineContext, concurrency, priority) { channel.send(it) }
        channel.invokeOnClose {
            if (it != null) listener.completeExceptionally(it)
            else listener.complete()
        }

        return channel
    }

    // region transforming operations

    /**
     * 添加一个过滤器. 过滤器将在收到任何事件之后, 传递给通过 [EventChannel.subscribe] 注册的监听器之前调用.
     *
     * 若 [filter] 返回 `true`, 该事件将会被传给监听器. 否则将会被忽略, **监听器继续监听**.
     *
     * ## 线性顺序
     * 多个 [filter] 的处理是线性且有顺序的. 若一个 [filter] 已经返回了 `false` (代表忽略这个事件), 则会立即忽略, 而不会传递给后续过滤器.
     *
     * 示例:
     * ```
     * GlobalEventChannel // GlobalEventChannel 会收到全局所有事件, 事件类型是 Event
     *     .filterIsInstance<BotEvent>() // 过滤, 只接受 BotEvent
     *     .filter { event: BotEvent ->
     *         // 此时的 event 一定是 BotEvent
     *         event.bot.id == 123456 // 再过滤 event 的 bot.id
     *     }
     *     .subscribeAlways { event: BotEvent ->
     *         // 现在 event 是 BotEvent, 且 bot.id == 123456
     *     }
     * ```
     *
     * ## 过滤器挂起
     * [filter] 允许挂起协程. **过滤器的挂起将被认为是事件监听器的挂起**.
     *
     * 过滤器挂起是否会影响事件处理,
     * 取决于 [subscribe] 时的 [ConcurrencyKind] 和 [EventPriority].
     *
     * ## 过滤器异常处理
     * 若 [filter] 抛出异常, 将被包装为 [ExceptionInEventChannelFilterException] 并重新抛出.
     *
     * @see filterIsInstance 过滤指定类型的事件
     */
    @JvmSynthetic
    public fun filter(filter: suspend (event: BaseEvent) -> Boolean): EventChannel<BaseEvent> {
        val parent = this
        return object : EventChannel<BaseEvent>(baseEventClass, defaultCoroutineContext) {
            private inline val innerThis get() = this

            override fun <E : Event> (suspend (E) -> ListeningStatus).intercepted(): suspend (E) -> ListeningStatus {
                val thisIntercepted: suspend (E) -> ListeningStatus = { ev ->
                    val filterResult = try {
                        @Suppress("UNCHECKED_CAST")
                        baseEventClass.isInstance(ev) && filter(ev as BaseEvent)
                    } catch (e: Throwable) {
                        if (e is ExceptionInEventChannelFilterException) throw e // wrapped by another filter
                        throw ExceptionInEventChannelFilterException(ev, innerThis, cause = e)
                    }
                    if (filterResult) this@intercepted.invoke(ev)
                    else ListeningStatus.LISTENING
                }
                return parent.intercept(thisIntercepted)
            }
        }
    }

    /**
     * [EventChannel.filter] 的 Java 版本.
     *
     * 添加一个过滤器. 过滤器将在收到任何事件之后, 传递给通过 [EventChannel.subscribe] 注册的监听器之前调用.
     *
     * 若 [filter] 返回 `true`, 该事件将会被传给监听器. 否则将会被忽略, **监听器继续监听**.
     *
     * ## 线性顺序
     * 多个 [filter] 的处理是线性且有顺序的. 若一个 [filter] 已经返回了 `false` (代表忽略这个事件), 则会立即忽略, 而不会传递给后续过滤器.
     *
     * 示例:
     * ```
     * GlobalEventChannel // GlobalEventChannel 会收到全局所有事件, 事件类型是 Event
     *     .filterIsInstance(BotEvent.class) // 过滤, 只接受 BotEvent
     *     .filter(event ->
     *         // 此时的 event 一定是 BotEvent
     *         event.bot.id == 123456 // 再过滤 event 的 bot.id
     *     )
     *     .subscribeAlways(event -> {
     *         // 现在 event 是 BotEvent, 且 bot.id == 123456
     *     })
     * ```
     *
     * ## 过滤器阻塞
     * [filter] 允许阻塞线程. **过滤器的阻塞将被认为是事件监听器的阻塞**.
     *
     * 过滤器阻塞是否会影响事件处理,
     * 取决于 [subscribe] 时的 [ConcurrencyKind] 和 [EventPriority].
     *
     * ## 过滤器异常处理
     * 若 [filter] 抛出异常, 将被包装为 [ExceptionInEventChannelFilterException] 并重新抛出.
     *
     * @see filterIsInstance 过滤指定类型的事件
     *
     * @since 2.2
     */
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    @kotlin.internal.LowPriorityInOverloadResolution
    public fun filter(filter: (event: BaseEvent) -> Boolean): EventChannel<BaseEvent> {
        return filter { runInterruptible { filter(it) } }
    }

    /**
     * 过滤事件的类型. 返回一个只包含 [E] 类型事件的 [EventChannel]
     * @see filter 获取更多信息
     */
    @JvmSynthetic
    public inline fun <reified E : Event> filterIsInstance(): EventChannel<E> =
        filterIsInstance(E::class)

    /**
     * 过滤事件的类型. 返回一个只包含 [E] 类型事件的 [EventChannel]
     * @see filter 获取更多信息
     */
    public fun <E : Event> filterIsInstance(kClass: KClass<out E>): EventChannel<E> {
        return filter { kClass.isInstance(it) }.cast()
    }

    /**
     * 过滤事件的类型. 返回一个只包含 [E] 类型事件的 [EventChannel]
     * @see filter 获取更多信息
     */
    public fun <E : Event> filterIsInstance(clazz: Class<out E>): EventChannel<E> =
        filterIsInstance(clazz.kotlin)


    /**
     * 创建一个新的 [EventChannel], 该 [EventChannel] 包含 [`this.coroutineContext`][defaultCoroutineContext] 和添加的 [coroutineContexts].
     * [coroutineContexts] 会覆盖 [defaultCoroutineContext] 中的重复元素.
     *
     * 此操作不会修改 [`this.coroutineContext`][defaultCoroutineContext], 只会创建一个新的 [EventChannel].
     */
    public fun context(vararg coroutineContexts: CoroutineContext): EventChannel<BaseEvent> {
        val origin = this
        return object : EventChannel<BaseEvent>(
            baseEventClass,
            coroutineContexts.fold(this.defaultCoroutineContext) { acc, element -> acc + element }
        ) {
            override fun <E : Event> (suspend (E) -> ListeningStatus).intercepted(): suspend (E) -> ListeningStatus {
                return origin.intercept(this)
            }
        }
    }

    /**
     * 创建一个新的 [EventChannel], 该 [EventChannel] 包含 [this.coroutineContext][defaultCoroutineContext] 和添加的 [coroutineExceptionHandler]
     * @see context
     */
    @LowPriorityInOverloadResolution
    public fun exceptionHandler(coroutineExceptionHandler: CoroutineExceptionHandler): EventChannel<BaseEvent> {
        return context(coroutineExceptionHandler)
    }

    /**
     * 创建一个新的 [EventChannel], 该 [EventChannel] 包含 [`this.coroutineContext`][defaultCoroutineContext] 和添加的 [coroutineExceptionHandler]
     * @see context
     */
    public fun exceptionHandler(coroutineExceptionHandler: (exception: Throwable) -> Unit): EventChannel<BaseEvent> {
        return context(CoroutineExceptionHandler { _, throwable ->
            coroutineExceptionHandler(throwable)
        })
    }

    /**
     * 将 [coroutineScope] 作为这个 [EventChannel] 的父作用域.
     *
     * 实际作用为创建一个新的 [EventChannel],
     * 该 [EventChannel] 包含 [`this.coroutineContext`][defaultCoroutineContext] 和添加的 [CoroutineScope.coroutineContext],
     * 并以 [CoroutineScope] 中 [Job] (如果有) [作为父 Job][parentJob]
     *
     * @see parentJob
     * @see context
     *
     * @see CoroutineScope.globalEventChannel `GlobalEventChannel.parentScope()` 的扩展
     */
    public fun parentScope(coroutineScope: CoroutineScope): EventChannel<BaseEvent> {
        return context(coroutineScope.coroutineContext)
    }

    /**
     * 指定协程父 [Job]. 之后在此 [EventChannel] 下创建的事件监听器都会成为 [job] 的子任务, 当 [job] 被取消时, 所有的事件监听器都会被取消.
     *
     * 注意: 监听器不会失败 ([Job.cancel]). 监听器处理过程的异常都会被捕获然后交由 [CoroutineExceptionHandler] 处理, 因此 [job] 不会因为子任务监听器的失败而被取消.
     *
     * @see parentScope
     * @see context
     */
    public fun parentJob(job: Job): EventChannel<BaseEvent> {
        return context(job)
    }

    // endregion

    // region subscribe

    /**
     * 创建一个事件监听器, 监听事件通道中所有 [E] 及其子类事件.
     *
     * 每当 [事件广播][Event.broadcast] 时, [handler] 都会被执行.
     *
     *
     * ## 创建监听
     * 调用本函数:
     * ```
     * eventChannel.subscribe<E> { /* 会收到此通道中的所有是 E 的事件 */ }
     * ```
     *
     * ## 生命周期
     *
     * ### 通过协程作用域管理监听器
     * 本函数将会创建一个 [Job], 成为 [parentJob] 中的子任务. 可创建一个 [CoroutineScope] 来管理所有的监听器:
     * ```
     * val scope = CoroutineScope(SupervisorJob())
     *
     * val scopedChannel = eventChannel.parentScope(scope) // 将协程作用域 scope 附加到这个 EventChannel
     *
     * scopedChannel.subscribeAlways<MemberJoinEvent> { /* ... */ } // 启动监听, 监听器协程会作为 scope 的子任务
     * scopedChannel.subscribeAlways<MemberMuteEvent> { /* ... */ } // 启动监听, 监听器协程会作为 scope 的子任务
     *
     * scope.cancel() // 停止了协程作用域, 也就取消了两个监听器
     * ```
     *
     * 这个函数返回 [Listener], 它是一个 [CompletableJob]. 它会成为 [parentJob] 或 [parentScope] 的一个 [子任务][Job]
     *
     * ### 停止监听
     * 如果 [handler] 返回 [ListeningStatus.STOPPED] 监听器将被停止.
     *
     * 也可以通过 [subscribe] 返回值 [Listener] 的 [Listener.complete]
     *
     * ## 监听器调度
     * 监听器会被创建一个协程任务, 语义上在 [parentScope] 下运行.
     * 通过 Kotlin [默认协程调度器][Dispatchers.Default] 在固定的全局共享线程池里执行, 除非有 [coroutineContext] 指定.
     *
     * 默认在 [handler] 中不能处理阻塞任务. 阻塞任务将会阻塞一个 Kotlin 全局协程调度线程并可能导致严重问题.
     * 请通过 `withContext(Dispatchers.IO) { }` 等方法执行阻塞工作.
     *
     * ## 异常处理
     * - 当参数 [handler] 处理抛出异常时, 将会按如下顺序寻找 [CoroutineExceptionHandler] 处理异常:
     *   1. 参数 [coroutineContext]
     *   2. [EventChannel.defaultCoroutineContext]
     *   3. [Event.broadcast] 调用者的 [coroutineContext]
     *   4. 若事件为 [BotEvent], 则从 [BotEvent.bot] 获取到 [Bot], 进而在 [Bot.coroutineContext] 中寻找
     *   5. 若以上四个步骤均无法获取 [CoroutineExceptionHandler], 则使用 [MiraiLogger.Companion] 通过日志记录. 但这种情况理论上不应发生.
     *
     *
     * 事件处理时抛出异常不会停止监听器.
     *
     * 建议在事件处理中 (即 [handler] 里) 处理异常,
     * 或在参数 [coroutineContext] 中添加 [CoroutineExceptionHandler], 或通过 [EventChannel.exceptionHandler].
     *
     * ## 并发安全性
     * 基于 [concurrency] 参数, 事件监听器可以被允许并行执行.
     *
     * - 若 [concurrency] 为 [ConcurrencyKind.CONCURRENT], [handler] 可能被并行调用, 需要保证并发安全.
     * - 若 [concurrency] 为 [ConcurrencyKind.LOCKED], [handler] 会被 [Mutex] 限制.
     *
     * @param coroutineContext 在 [defaultCoroutineContext] 的基础上, 给事件监听协程的额外的 [CoroutineContext].
     * @param concurrency 并发类型. 查看 [ConcurrencyKind]
     * @param priority  监听优先级，优先级越高越先执行
     * @param handler 事件处理器. 在接收到事件时会调用这个处理器. 其返回值意义参考 [ListeningStatus]. 其异常处理参考上文
     *
     * @return 监听器实例. 此监听器已经注册到指定事件上, 在事件广播时将会调用 [handler]
     *
     * @see syncFromEvent 挂起当前协程, 监听一个事件, 并尝试从这个事件中**同步**一个值
     * @see asyncFromEvent 异步监听一个事件, 并尝试从这个事件中获取一个值.
     *
     * @see nextEvent 挂起当前协程, 直到监听到事件 [E] 的广播, 返回这个事件实例.
     *
     * @see selectMessages 以 `when` 的语法 '选择' 即将到来的一条消息.
     * @see whileSelectMessages 以 `when` 的语法 '选择' 即将到来的所有消息, 直到不满足筛选结果.
     *
     * @see subscribeAlways 一直监听
     * @see subscribeOnce   只监听一次
     *
     * @see subscribeMessages       监听消息 DSL
     */
    @JvmSynthetic
    public inline fun <reified E : Event> subscribe(
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        concurrency: ConcurrencyKind = LOCKED,
        priority: EventPriority = EventPriority.NORMAL,
        noinline handler: suspend E.(E) -> ListeningStatus
    ): Listener<E> = subscribe(E::class, coroutineContext, concurrency, priority, handler)

    /**
     * 与 [subscribe] 的区别是接受 [eventClass] 参数, 而不使用 `reified` 泛型. 通常推荐使用具体化类型参数.
     *
     * @return 监听器实例. 此监听器已经注册到指定事件上, 在事件广播时将会调用 [handler]
     * @see subscribe
     */
    @JvmSynthetic
    public fun <E : Event> subscribe(
        eventClass: KClass<out E>,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        concurrency: ConcurrencyKind = LOCKED,
        priority: EventPriority = EventPriority.NORMAL,
        handler: suspend E.(E) -> ListeningStatus
    ): Listener<E> = subscribeInternal(
        eventClass,
        createListener(coroutineContext, concurrency, priority) { it.handler(it); }
    )

    /**
     * 创建一个事件监听器, 监听事件通道中所有 [E] 及其子类事件.
     * 每当 [事件广播][Event.broadcast] 时, [handler] 都会被执行.
     *
     * 可在任意时候通过 [Listener.complete] 来主动停止监听.
     *
     * @param concurrency 并发类型默认为 [CONCURRENT]
     * @param coroutineContext 在 [defaultCoroutineContext] 的基础上, 给事件监听协程的额外的 [CoroutineContext]
     * @param priority 处理优先级, 优先级高的先执行
     *
     * @return 监听器实例. 此监听器已经注册到指定事件上, 在事件广播时将会调用 [handler]
     *
     * @see subscribe 获取更多说明
     */
    @JvmSynthetic
    public inline fun <reified E : Event> subscribeAlways(
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        concurrency: ConcurrencyKind = CONCURRENT,
        priority: EventPriority = EventPriority.NORMAL,
        noinline handler: suspend E.(E) -> Unit
    ): Listener<E> = subscribeAlways(E::class, coroutineContext, concurrency, priority, handler)


    /**
     * @see subscribe
     * @see subscribeAlways
     */
    @JvmSynthetic
    public fun <E : Event> subscribeAlways(
        eventClass: KClass<out E>,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        concurrency: ConcurrencyKind = CONCURRENT,
        priority: EventPriority = EventPriority.NORMAL,
        handler: suspend E.(E) -> Unit
    ): Listener<E> = subscribeInternal(
        eventClass,
        createListener(coroutineContext, concurrency, priority) { it.handler(it); ListeningStatus.LISTENING }
    )

    /**
     * 创建一个事件监听器, 监听事件通道中所有 [E] 及其子类事件.
     * 每当 [事件广播][Event.broadcast] 时, [handler] 都会被执行.
     *
     * 可在任意时候通过 [Listener.complete] 来主动停止监听.
     *
     * @param coroutineContext 在 [defaultCoroutineContext] 的基础上, 给事件监听协程的额外的 [CoroutineContext]
     * @param priority 处理优先级, 优先级高的先执行
     *
     * @see subscribe 获取更多说明
     */
    @JvmSynthetic
    public inline fun <reified E : Event> subscribeOnce(
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        priority: EventPriority = EventPriority.NORMAL,
        noinline handler: suspend E.(E) -> Unit
    ): Listener<E> = subscribeOnce(E::class, coroutineContext, priority, handler)

    /**
     * @see subscribeOnce
     */
    public fun <E : Event> subscribeOnce(
        eventClass: KClass<out E>,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        priority: EventPriority = EventPriority.NORMAL,
        handler: suspend E.(E) -> Unit
    ): Listener<E> = subscribeInternal(
        eventClass,
        createListener(coroutineContext, LOCKED, priority) { it.handler(it); ListeningStatus.STOPPED }
    )

    // endregion

    /**
     * 注册 [ListenerHost] 中的所有 [EventHandler] 标注的方法到这个 [EventChannel]. 查看 [EventHandler].
     *
     * @param coroutineContext 在 [defaultCoroutineContext] 的基础上, 给事件监听协程的额外的 [CoroutineContext]
     *
     * @see subscribe
     * @see EventHandler
     * @see ListenerHost
     */
    @JvmOverloads
    public fun registerListenerHost(
        host: ListenerHost,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
    ) {
        for (method in host.javaClass.declaredMethods) {
            method.getAnnotation(EventHandler::class.java)?.let {
                method.registerEventHandler(host, this, it, coroutineContext)
            }
        }
    }

    // region Java API

    /**
     * Java API. 查看 [subscribeAlways] 获取更多信息.
     *
     * ```java
     * eventChannel.subscribeAlways(GroupMessageEvent.class, (event) -> { });
     * ```
     *
     * @see subscribe
     * @see subscribeAlways
     */
    @JvmOverloads
    @LowPriorityInOverloadResolution
    public fun <E : Event> subscribeAlways(
        eventClass: Class<out E>,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        concurrency: ConcurrencyKind = CONCURRENT,
        priority: EventPriority = EventPriority.NORMAL,
        handler: Consumer<E>
    ): Listener<E> = subscribeInternal(
        eventClass.kotlin,
        createListener(coroutineContext, concurrency, priority) { event ->
            runInterruptible(Dispatchers.IO) { handler.accept(event) }
            ListeningStatus.LISTENING
        }
    )

    /**
     * Java API. 查看 [subscribe] 获取更多信息.
     *
     * ```java
     * eventChannel.subscribe(GroupMessageEvent.class, (event) -> {
     *     return ListeningStatus.LISTENING;
     * });
     * ```
     *
     * @see subscribe
     */
    @JvmOverloads
    @LowPriorityInOverloadResolution
    public fun <E : Event> subscribe(
        eventClass: Class<out E>,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        concurrency: ConcurrencyKind = CONCURRENT,
        priority: EventPriority = EventPriority.NORMAL,
        handler: java.util.function.Function<E, ListeningStatus>
    ): Listener<E> = subscribeInternal(
        eventClass.kotlin,
        createListener(coroutineContext, concurrency, priority) { event ->
            runInterruptible(Dispatchers.IO) { handler.apply(event) }
        }
    )

    /**
     * Java API. 查看 [subscribeOnce] 获取更多信息.
     *
     * ```java
     * eventChannel.subscribeOnce(GroupMessageEvent.class, (event) -> { });
     * ```
     *
     * @see subscribe
     * @see subscribeOnce
     */
    @JvmOverloads
    @LowPriorityInOverloadResolution
    public fun <E : Event> subscribeOnce(
        eventClass: Class<out E>,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        concurrency: ConcurrencyKind = CONCURRENT,
        priority: EventPriority = EventPriority.NORMAL,
        handler: Consumer<E>
    ): Listener<E> = subscribeInternal(
        eventClass.kotlin,
        createListener(coroutineContext, concurrency, priority) { event ->
            runInterruptible(Dispatchers.IO) { handler.accept(event) }
            ListeningStatus.STOPPED
        }
    )

    // endregion

    // region impl

    /**
     * 由子类实现，可以为 handler 包装一个过滤器等. 每个 handler 都会经过此函数处理.
     */
    @MiraiExperimentalApi
    protected open fun <E : Event> (suspend (E) -> ListeningStatus).intercepted(): (suspend (E) -> ListeningStatus) {
        return this
    }

    private fun <E : Event> intercept(listener: (suspend (E) -> ListeningStatus)): suspend (E) -> ListeningStatus {
        return listener.intercepted()
    }

    private fun <L : Listener<E>, E : Event> subscribeInternal(eventClass: KClass<out E>, listener: L): L {
        with(GlobalEventListeners[listener.priority]) {
            @Suppress("UNCHECKED_CAST")
            val node = ListenerRegistry(listener as Listener<Event>, eventClass)
            add(node)
            listener.invokeOnCompletion {
                this.remove(node)
            }
        }
        return listener
    }


    @Suppress("FunctionName")
    private fun <E : Event> createListener(
        coroutineContext: CoroutineContext,
        concurrencyKind: ConcurrencyKind,
        priority: EventPriority = EventPriority.NORMAL,
        handler: suspend (E) -> ListeningStatus
    ): Listener<E> {
        val context = this.defaultCoroutineContext + coroutineContext
        return Handler(
            parentJob = context[Job],
            subscriberContext = context,
            handler = handler.intercepted(),
            concurrencyKind = concurrencyKind,
            priority = priority
        )
    }

    // endregion
}