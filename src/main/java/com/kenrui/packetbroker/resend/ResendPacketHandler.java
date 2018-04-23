package com.kenrui.packetbroker.resend;

import com.kenrui.packetbroker.structures.PacketToResend;
import com.kenrui.packetbroker.utilities.PacketUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler for processing each packet that needs to be resent to remote clients.
 */
@Component
@Scope("prototype")
public class ResendPacketHandler implements Callable<String> {
    private PacketUtils packetUtils;
    private BlockingQueue packetsToResendQueue;
    private Selector selector;
    private PacketToResend packetToResend = null;
    private int threadTimeOut;
    ConcurrentHashMap<SocketChannel, Boolean> remoteClientsSentCheck;
    private static final Logger logger = LogManager.getLogger("ResendPacketHandler");

    public void setThreadTimeOut(int threadTimeOut) {
        this.threadTimeOut = threadTimeOut;
    }

    /**
     * Creates handler for PacketToResend object.
     *
     * @param packetsToResendQueue Queue with list of PacketToResend objects, which contain packets to resend, and remote clients for each packet.
     * @param threadTimeOut        Timeout for attempt to resend each packet before it is moved to the Dead Letter Queue.
     * @param selector             Standard NIO Selector to be passed in.
     * @param packetUtils          Utility class for packets.
     */
    @Autowired
    public ResendPacketHandler(BlockingQueue packetsToResendQueue, int threadTimeOut, Selector selector, PacketUtils packetUtils) {
        this.packetsToResendQueue = packetsToResendQueue;
        this.threadTimeOut = threadTimeOut;
        this.selector = selector;
        this.packetUtils = packetUtils;
    }

    @Override
    public String call() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                callableTask();
            }
        } catch (Exception e) {
            logger.error(Thread.currentThread().getName() + " " + e);
        }
        return null;
    }

    protected void callableTask() throws InterruptedException, IOException {
        // Handler thread will keep running and checking if there are packets to be resent
        // Following line will block if there are no packets on queue to be resent
        packetToResend = (PacketToResend) packetsToResendQueue.take();
        if (packetToResend != null) {
            ByteBuffer byteBuffer = packetToResend.getByteBuffer();
            remoteClientsSentCheck = packetToResend.getRemoteClientsSentCheck();

            for (ConcurrentHashMap.Entry<SocketChannel, Boolean> entry : remoteClientsSentCheck.entrySet()) {
                // Only process those channels that have not been sent the packet
                if (entry.getValue() == Boolean.FALSE) {
                    SocketChannel socketChannel = entry.getKey();
                    try {
                        socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                        socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                        socketChannel.configureBlocking(false);
                        socketChannel.register(selector, SelectionKey.OP_WRITE);
                        logger.debug("Preparing to resend packet to " + socketChannel.getRemoteAddress());
                    } catch (ClosedChannelException e) {
                        logger.error(e);
                    } catch (IOException e) {
                        logger.error(e);
                    }
                }
            }

            try {
                selector.selectNow();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            // Resend packet as long as there are outstanding clients and within retry time out period
            long resendTimerStartTime = System.currentTimeMillis();
            while (keys.hasNext() && (System.currentTimeMillis() - resendTimerStartTime < threadTimeOut)) {
                SelectionKey key = keys.next();
                if (!key.isValid()) {
                    continue;
                }

                if (key.isWritable()) {
                    SocketChannel channel = (SocketChannel) key.channel();
                    // Try to send to remote client
                    packetUtils.sendPacketAndUpdateStatus(remoteClientsSentCheck, byteBuffer, channel, logger);
                }
            }

            // By now if we still have outstanding clients we need to move packet to Dead Letter Queue
            if (remoteClientsSentCheck.containsValue(Boolean.FALSE)) {
                packetUtils.putOnDLQ(remoteClientsSentCheck, byteBuffer, logger);
            }
        }
    }
}
