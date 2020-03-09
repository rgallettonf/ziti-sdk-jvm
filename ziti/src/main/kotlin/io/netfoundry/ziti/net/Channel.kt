/*
 * Copyright (c) 2018-2020 NetFoundry, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.netfoundry.ziti.net

import io.netfoundry.ziti.Errors
import io.netfoundry.ziti.ZitiException
import io.netfoundry.ziti.identity.Identity
import io.netfoundry.ziti.util.ZitiLog
import io.netfoundry.ziti.util.Logged
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.channels.Channel as Chan

internal class Channel(val peer: Transport) : Closeable, CoroutineScope, Logged by ZitiLog() {

    private val supervisor = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + supervisor

    private val closed = AtomicBoolean(false)
    private val sequencer = AtomicInteger(1)
    private val txChan = Chan<Message>(16)
    private val waiters = ConcurrentHashMap<Int, CompletableDeferred<Message>>()
    private val synchers = ConcurrentHashMap<Int, CompletableDeferred<Unit>>()

    private val receiverSeq = AtomicInteger(0)
    private val receivers = mutableMapOf<Int, ZitiConn>()

    internal fun registerReceiver(rec: ZitiConn): Int {
        val id = receiverSeq.incrementAndGet()
        synchronized(receivers) {
            receivers[id] = rec
        }
        return id
    }

    internal fun deregisterReceiver(id: Int) = synchronized(receivers) {
        receivers.remove(id)
    }

    private val closeListeners = mutableListOf<Function0<Unit>>()
    fun onClose(l: Function0<Unit>) {
        closeListeners.add(l)
    }

    init {
        launch { txer() }
        launch { rxer() }
    }

    override fun close() {
        for (l in closeListeners) {
            l()
        }

        supervisor.cancel()

        if (closed.compareAndSet(false, true)) {
            txChan.close()
            peer.close()
        }
    }

    internal suspend fun Send(msg: Message) {
        msg.seqNo = sequencer.getAndIncrement()
        txChan.send(msg)
    }

    /**
     * Sends the message and returns after the message has been written-n-flushed to underlying transport
     */
    internal suspend fun SendSynch(msg: Message) {
        CompletableDeferred<Unit>().apply {
            msg.seqNo = sequencer.getAndIncrement()
            synchers.put(msg.seqNo, this)
            txChan.send(msg)
            await()
        }
    }

    suspend fun SendAndWait(msg: Message): Message = CompletableDeferred<Message>().let {
        msg.seqNo = sequencer.getAndIncrement()
        waiters.put(msg.seqNo, it)
        txChan.send(msg)
        it.await()
    }

    suspend fun txer() {
        try {
            for (m in txChan) {
                d("sending m = ${m}")
                val syncher = synchers.remove(m.seqNo)
                try {
                    peer.write(m.toBytes())
                    syncher?.complete(Unit)
                } catch (ex: Throwable) {
                    waiters.remove(m.seqNo)?.completeExceptionally(ex)
                    syncher?.completeExceptionally(ex)
                }
            }
        } catch (ce: CancellationException) {
            d("txer(): cancelled")
        } catch (ex: Exception) {
            e("txer(): ${ex.localizedMessage}", ex)
            //
        } finally {
            d("txer() is done")
            close()
        }
    }

    suspend fun rxer() {
        try {
            val inbound = rx()
            for (m in inbound) {
                d("got m = ${m}")
                val waiter = waiters.remove(m.repTo)
                if (waiter != null) {
                    waiter.complete(m)
                } else {
                    val recId = m.getIntHeader(ZitiProtocol.Header.ConnId)
                    recId?.let {
                        val receiver = synchronized(receivers) {
                            receivers[it]
                        }
                        try {
                            receiver?.recChan?.send(m)
                        }
                        catch (ex: Exception) {
                            e(ex){"failed to dispatch"}
                        }
                    }
                }
            }
        } catch (eof: EOFException) {
            d("eof on rxer")
        } catch (jce: CancellationException) {
            d("rxer() cancelled")
        } catch (ie: Throwable) {
            e("rxer() exception", ie)
        } finally {
            d("rxer() is done")
            close()
        }
    }

    @ExperimentalCoroutinesApi
    fun rx(): ReceiveChannel<Message> = produce(this.coroutineContext, 16) {
        try {
            while (true) {
                val m = Message.readMessage(peer.getInput())
                send(m)
            }
        } catch (ex: Exception) {
            w{"rx() closed with $ex"}
            if (!closed.get()) {
                e("rx(): ${ex.localizedMessage}", ex)
                close(ex)
            } else {
                close()
            }
        }
    }

    companion object {
        val TAG = Channel::class.java.simpleName

        fun Dial(addr: String, id: Identity): Channel {
            try {
                val token = id.sessionToken
                if (token == null) {
                    throw IllegalStateException("no session token for connection")
                }

                val peer = Transport.dial(addr, id.sslContext())
                val ch = Channel(peer)

                val helloMsg = Message.newHello(id.name()).apply {
                    setHeader(ZitiProtocol.Header.SessionToken, token)
                }

                val reply = runBlocking { ch.SendAndWait(helloMsg) }

                if (reply.content != ZitiProtocol.ContentType.ResultType) {
                    peer.close()
                    throw IOException("Invalid response type")
                }

                val success = reply.getBoolHeader(ZitiProtocol.Header.ResultSuccess)
                if (!success) {
                    peer.close()
                    throw IOException(reply.body.toString(StandardCharsets.UTF_8))
                }
                return ch
            } catch (cex: ConnectException) {
                throw ZitiException(Errors.EdgeRouterUnavailable, cex)
            } catch (ex: Throwable) {
                throw ZitiException(Errors.WTF(ex.toString()), ex)
            }
        }
    }
}
