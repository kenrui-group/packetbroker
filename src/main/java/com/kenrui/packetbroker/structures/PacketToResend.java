package com.kenrui.packetbroker.structures;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores a packet and the list of clients that need to receive this packet.
 * This is used when a packet needed to be sent to a list of clients and some of the clients were not
 * able to receive due to slow consumer / dead client / bad connection, etc.
 */
public class PacketToResend {
    private ByteBuffer byteBuffer;
    private ConcurrentHashMap<SocketChannel, Boolean> remoteClientsSentCheck;

    /**
     * Creates a PacketToResend
     * @param byteBuffer
     * @param remoteClientsSentCheck
     */
    public PacketToResend(ByteBuffer byteBuffer, ConcurrentHashMap<SocketChannel, Boolean> remoteClientsSentCheck) {
        this.byteBuffer = byteBuffer;
        this.remoteClientsSentCheck = remoteClientsSentCheck;
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    public ConcurrentHashMap<SocketChannel, Boolean> getRemoteClientsSentCheck() {
        return remoteClientsSentCheck;
    }
}
