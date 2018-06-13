package com.kenrui.packetbroker.helper;

import com.kenrui.packetbroker.structures.EthernetPausePacket;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class QueueSizeChecker {
    private int highWaterMark;
    private int highWaterMarkInPackets;
    private final int PACKETS_PER_PAUSEFRAME = 464;  // Max Pause Time Per Ethernet Pause Frame at 10Gbps = 3.355 ms = 0.003355 second = 464.01 packets
    private EthernetPausePacket ethernetPausePacket;

    public QueueSizeChecker(int highWaterMark, EthernetPausePacket ethernetPausePacket) {
        this.highWaterMark = highWaterMark;
        this.highWaterMarkInPackets = PACKETS_PER_PAUSEFRAME * this.highWaterMark;
        this.ethernetPausePacket = ethernetPausePacket;
    }

    public void checkQueue(BlockingQueue queueName) {
        if (queueName.remainingCapacity() <= highWaterMarkInPackets) {
            ethernetPausePacket.sendPauseFrame();
        }
    }
}
