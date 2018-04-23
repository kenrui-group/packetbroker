package com.kenrui.packetbroker.utilities;

import org.apache.logging.log4j.Logger;
import org.pcap4j.util.ByteArrays;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PacketUtils {

    public PacketUtils() {
    }

    public byte[] getRandomPacket(int packetSizeLowerLimit, int packetSizeUpperLimit) {
        int randomNum = ThreadLocalRandom.current().nextInt(packetSizeLowerLimit, packetSizeUpperLimit);
        byte[] bytes = new byte[randomNum];
        new Random().nextBytes(bytes);

        return bytes;
    }

    public static byte[] byteBufferToArray(ByteBuffer inputByteBuffer) {
        int length = inputByteBuffer.limit();
        int pos = inputByteBuffer.position();

        byte[] outputByteArray = new byte[length];
        inputByteBuffer.get(outputByteArray, 0, length);

        inputByteBuffer.position(pos);

        return outputByteArray;
    }

    public void sendPacketAndUpdateStatus(ConcurrentHashMap<SocketChannel, Boolean> remoteClientsSentCheck,
                                                 ByteBuffer byteBuffer, SocketChannel channel, Logger logger) {
        try {
            logger.debug("Sending packet to " + channel.getRemoteAddress());
            int written = 0;
            byte[] packet = new byte[byteBuffer.limit()];
            int pos = byteBuffer.position();
            byteBuffer.get(packet);
            byteBuffer.position(pos);
            logger.debug("Sending " + ByteArrays.toHexString(packet, " "));
            while (byteBuffer.hasRemaining()) {
                written = written + channel.write(byteBuffer);
            }
            logger.debug("Sent "+ written + " bytes");
        } catch (AsynchronousCloseException e) {
            remoteClientsSentCheck.remove(channel);
            logger.error(e);
        } catch (ClosedChannelException e) {
            remoteClientsSentCheck.remove(channel);
            logger.error(e);
        } catch (IOException e) {
            logger.error(e);
        }

        // Update check card for the specific remote client that has been sent the packet
        remoteClientsSentCheck.replace(channel, Boolean.TRUE);

        // Rewind byteBuffer in case there is more than one remote client to be sent the same
        // packet on this round of SelectionKeys
        byteBuffer.position(0);
    }

    // todo: implement DLQ
    public void putOnDLQ(ConcurrentHashMap<SocketChannel, Boolean> remoteClientsSentCheck, ByteBuffer byteBuffer, Logger logger) throws IOException {
        byte[] packet = new byte[byteBuffer.limit()];
        int pos = byteBuffer.position();
        byteBuffer.get(packet);
        byteBuffer.position(pos);

        StringBuilder deadClientList = new StringBuilder("Unable to send ");
        deadClientList.append(ByteArrays.toHexString(packet, " ")).append(System.lineSeparator());
        deadClientList.append("Following are likely dead clients:").append(System.lineSeparator());
        for (ConcurrentHashMap.Entry<SocketChannel, Boolean> entry : remoteClientsSentCheck.entrySet()) {
            // Only process those channels that have not been sent the packet
            if (entry.getValue() == Boolean.FALSE) {
                SocketChannel socketChannel = entry.getKey();
                deadClientList.append(socketChannel.getRemoteAddress().toString()).append(System.lineSeparator());
            }
        }

        logger.warn(deadClientList.toString());
    }
}
