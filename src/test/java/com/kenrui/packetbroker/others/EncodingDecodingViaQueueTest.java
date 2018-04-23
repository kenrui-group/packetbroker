package com.kenrui.packetbroker.others;

import com.kenrui.packetbroker.structures.ConnectionInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class EncodingDecodingViaQueueTest {
    private static final String ENCODING_FILENAME = "C://Users//Titus//IdeaProjects//com.kenrui.packetbroker.PacketBroker//target//encoded-packets.sbe";
    private static final ConnectionInfo localServerEndPoint = new ConnectionInfo("127.0.0.1", (short) 10000, "campus.hk.kenrui.com", "Primary PacketBroker");
    private static List<ConnectionInfo> connectionInfoList = new ArrayList<>();
    private static BlockingQueue messageQueue = new ArrayBlockingQueue(1024, true);

    public static void main(String[] args) throws IOException {
        connectionInfoList.add(localServerEndPoint);

        final String encodingFilename = ENCODING_FILENAME;

        Long startTime = System.currentTimeMillis();
        System.out.println("Begin time " + startTime);

        MessageGeneratorToQueue messageGeneratorToQueue = new MessageGeneratorToQueue(10, 70, messageQueue);
        Thread messageGeneratorThread = new Thread(messageGeneratorToQueue);
        messageGeneratorThread.start();

        MessageListenerFromQueue messageListenerFromQueue = new MessageListenerFromQueue(messageQueue, localServerEndPoint, encodingFilename);
        Thread messageListenerThread = new Thread(messageListenerFromQueue);
        messageListenerThread.start();


        Long endTime = System.currentTimeMillis();
        System.out.println("End time " + endTime);
        Long totalTime = endTime - startTime;
//            System.out.println("Total time " + totalTime + " for " + loopCount + " encoding / decoding");
        System.out.println("Total time " + totalTime);


    }


}
