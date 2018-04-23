package com.kenrui.packetbroker.others;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

public class MessageGeneratorToQueue implements Runnable {
    private int packetSizeLowerLimit;
    private int packetSizeUpperLimit;
    private BlockingQueue messageQueue = null;

    public MessageGeneratorToQueue(int packetSizeLowerLimit, int packetSizeUpperLimit, BlockingQueue messageQueue) {
        this.packetSizeLowerLimit = packetSizeLowerLimit;
        this.packetSizeUpperLimit = packetSizeUpperLimit;
        this.messageQueue = messageQueue;
    }

    @Override
    public void run() {
        while (true) {
            int randomNum = ThreadLocalRandom.current().nextInt(packetSizeLowerLimit, packetSizeUpperLimit);
            byte[] bytes = new byte[randomNum];
            new Random().nextBytes(bytes);
            try {
                messageQueue.put(bytes);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
