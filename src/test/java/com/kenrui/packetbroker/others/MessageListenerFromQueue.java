package com.kenrui.packetbroker.others;

import com.kenrui.packetbroker.structures.ConnectionInfo;
import com.kenrui.packetbroker.structures.DecodedMessages;
import com.kenrui.packetbroker.helper.MessageProcessor;
import com.kenrui.packetbroker.utilities.UtilsFileHandler;

import java.nio.ByteBuffer;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.BlockingQueue;

public class MessageListenerFromQueue implements Runnable {
    private BlockingQueue messageQueue;
    private ConnectionInfo localServerEndPoint;
    private String encodingFilename;
    private byte[] bytes;
    private ZonedDateTime timeStamp, timeStampPrevious;
    private long seqNum = 1;

    public MessageListenerFromQueue(BlockingQueue messageQueue, ConnectionInfo localServerEndPoint, String encodingFilename) {
        this.messageQueue = messageQueue;
        this.localServerEndPoint = localServerEndPoint;
        this.encodingFilename = encodingFilename;
    }

    @Override
    public void run() {
        while (true) {
            try {
                bytes = (byte[]) messageQueue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            timeStamp = ZonedDateTime.now(ZoneOffset.UTC);

            // SeqNum is reset after midnight
            if (timeStampPrevious == null ||
                    timeStamp.toLocalDate().isAfter(timeStampPrevious.toLocalDate())) {
                seqNum = 1;
            } else {
                if (seqNum < Integer.MAX_VALUE) {
                    seqNum++;
                } else {
                    seqNum = 1;
                }
            }
            ByteBuffer byteBuffer = MessageProcessor.encode(localServerEndPoint, timeStamp, seqNum, bytes);
            timeStampPrevious = timeStamp;

            UtilsFileHandler.FileWriter(encodingFilename, byteBuffer);
            UtilsFileHandler.PrintFileToConsole(encodingFilename);

            System.out.println("----------------------------------------------------------------------------");

            ByteBuffer byteBufferReceive = UtilsFileHandler.FileReader(encodingFilename);

            try {
                DecodedMessages decodedMessages = MessageProcessor.decode(byteBufferReceive);
            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("============================================================================");
        }
    }
}
