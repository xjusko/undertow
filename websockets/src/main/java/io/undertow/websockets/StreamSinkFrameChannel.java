
/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.websockets;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.xnio.ChannelListener.Setter;
import org.xnio.ChannelListener.SimpleSetter;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class StreamSinkFrameChannel implements StreamSinkChannel {

    private final WebSocketFrameType type;
    protected final StreamSinkChannel channel;
    protected final WebSocketChannel wsChannel;
    final SimpleSetter<StreamSinkFrameChannel> closeSetter = new SimpleSetter<StreamSinkFrameChannel>();
    final SimpleSetter<StreamSinkFrameChannel> writeSetter = new SimpleSetter<StreamSinkFrameChannel>();

    private volatile boolean closed;
    private final AtomicBoolean writesDone = new AtomicBoolean();
    protected final long payloadSize;
    private final Object writeWaitLock = new Object();
    private int waiters = 0;
    private boolean suspendWrites;
    private int rsv;
    private boolean finalFragment = true;
    private boolean written;

    public StreamSinkFrameChannel(StreamSinkChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type, long payloadSize) {
        this.channel = channel;
        this.wsChannel = wsChannel;
        this.type = type;
        this.payloadSize = payloadSize;
    }

    @Override
    public Setter<? extends StreamSinkChannel> getWriteSetter() {
        return writeSetter;
    }

    /**
     * Return the RSV for the extension. Default is 0.
     */
    public int getRsv() {
        return rsv;
    }

    /**
     * Return <code>true</code> if this {@link StreamSinkFrameChannel} is the final fragement
     */
    public boolean isFinalFragment() {
        return finalFragment;
    }

    /**
     * Set if this {@link StreamSinkFrameChannel} is the final fragement. 
     * 
     * This can only be set before any write or transfer operations where passed
     * to the wrapped {@link StreamSinkChannel}, after that an {@link IllegalStateException} will be thrown.
     * 
     * @param finalFragment
     */
    public void setFinalFragment(boolean finalFragment) {
        if (written) {
            throw new IllegalStateException("Can only be set before anything is written");
        }
        this.finalFragment = finalFragment;
    }

    /**
     * Set the RSV which is used for extensions. 
     * 
     * This can only be set before any write or transfer operations where passed
     * to the wrapped {@link StreamSinkChannel}, after that an {@link IllegalStateException} will be thrown.
     * 
     * @param rsv
     */
    public void setRsv(int rsv) {
        if (written) {
            throw new IllegalStateException("Can only be set before anything is written");
        }
        this.rsv = rsv;
    }

    /**
     * Mark this channel as active
     */
    protected final void active() {
        if (suspendWrites) {
            channel.suspendWrites();
        } else {
            channel.resumeWrites();
        }

        // now notify the waiter
        synchronized (writeWaitLock) {
            if (waiters > 0) {
                writeWaitLock.notifyAll();
            }
        }
    }

    /**
     * Return the {@link WebSocketFrameType} for which the {@link StreamSinkFrameChannel} was obtained.
     */
    public WebSocketFrameType getType() {
        return type;
    }

    @Override
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    @Override
    public final void close() throws IOException {
        if (!closed) {
            closed = true;
            flush();
            if (close0()) {
                complete();
            }
        }
    }


    /**
     * Mark this {@link StreamSinkFrameChannel} as complete
     */
    protected final void complete() {
        wsChannel.complete(this);
    }

    /**
     * Gets called on {@link #close()}. If this returns <code>true<code> the {@link #recycle()} method will be triggered automaticly.
     *
     * @return complete          <code>true</code> if the {@link StreamSinkFrameChannel} is ready for close.
     * @throws IOException Get thrown if an problem during the close operation is detected
     */
    protected abstract boolean close0() throws IOException;

    @Override
    public final long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        checkClosed();
        if (!isActive()) {
            return 0;
        }
        written = true;

        return write0(srcs, offset, length);
    }

    /**
     * @see {@link StreamSinkChannel#write(ByteBuffer[], int, int)}
     */
    protected abstract long write0(ByteBuffer[] srcs, int offset, int length) throws IOException;

    @Override
    public final long write(ByteBuffer[] srcs) throws IOException {
        checkClosed();
        if (!isActive()) {
            return 0;
        }
        written = true;

        return write0(srcs);
    }

    /**
     * @see StreamSinkChannel#write(ByteBuffer[])
     */
    protected abstract long write0(ByteBuffer[] srcs) throws IOException;

    @Override
    public final int write(ByteBuffer src) throws IOException {
        checkClosed();
        if (!isActive()) {
            return 0;
        }
        written = true;

        return write0(src);
    }

    /**
     * @see StreamSinkChannel#write(ByteBuffer)
     */
    protected abstract int write0(ByteBuffer src) throws IOException;


    @Override
    public final long transferFrom(FileChannel src, long position, long count) throws IOException {
        checkClosed();
        if (!isActive()) {
            return 0;
        }
        written = true;

        return transferFrom0(src, position, count);
    }

    /**
     * @see StreamSinkChannel#transferFrom(FileChannel, long, long)
     */
    protected abstract long transferFrom0(FileChannel src, long position, long count) throws IOException;


    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        checkClosed();
        if (!isActive()) {
            return 0;
        }
        written = true;

        return transferFrom0(source, count, throughBuffer);
    }

    /**
     * @see StreamSinkChannel#transferFrom(StreamSourceChannel, long, ByteBuffer)
     */
    protected abstract long transferFrom0(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException;


    @Override
    public boolean isOpen() {
        return !closed && channel.isOpen();
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return channel.supportsOption(option);
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return channel.getOption(option);
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        return channel.setOption(option, value);
    }

    @Override
    public Setter<? extends StreamSinkChannel> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public void suspendWrites() {
        if (isActive()) {
            channel.suspendWrites();
        } else {
            suspendWrites = true;
        }
    }


    @Override
    public void resumeWrites() {
        if (isActive()) {
            channel.suspendWrites();
        } else {
            suspendWrites = false;
        }
    }

    /**
     * Return <code>true</code> if this {@link StreamSinkFrameChannel} is currently in use.
     */
    protected final boolean isActive() {
        return wsChannel.isActive(this);
    }

    @Override
    public boolean isWriteResumed() {
        if (isActive()) {
            return channel.isWriteResumed();
        } else {
            return !suspendWrites;
        }
    }


    @Override
    public void wakeupWrites() {
        if (isActive()) {
            channel.wakeupWrites();
        }
        ChannelListeners.invokeChannelListener(this, writeSetter.get());

    }


    @Override
    public void shutdownWrites() throws IOException {
        if (writesDone.compareAndSet(false, true)) {
            flush();
        }
    }


    @Override
    public void awaitWritable() throws IOException {
        if (isActive()) {
            channel.awaitWritable();
        } else {
            try {
                synchronized (writeWaitLock) {
                    waiters++;
                    try {
                        writeWaitLock.wait();
                    } finally {
                        waiters--;
                    }
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }


    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        if (isActive()) {
            channel.awaitWritable(time, timeUnit);
        } else {
            try {
                synchronized (writeWaitLock) {
                    waiters++;
                    try {
                        writeWaitLock.wait(timeUnit.toMillis(time));
                    } finally {
                        waiters--;
                    }
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }


    @Override
    public XnioExecutor getWriteThread() {
        return channel.getWriteThread();
    }


    @Override
    public boolean flush() throws IOException {
        if (isActive()) {
            return channel.flush();
        }
        return false;
    }

    /**
     * Throws an {@link IOException} if the {@link #isOpen()} returns <code>false</code>
     */
    protected final void checkClosed() throws IOException {
        if (!isOpen()) {
            throw new IOException("Channel already closed");
        }
    }
}
