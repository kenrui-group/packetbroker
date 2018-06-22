package com.kenrui.packetbroker.helper;

import com.kenrui.packetbroker.structures.ConnectionInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pcap4j.util.ByteArrays;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Helper class to put packets on queues for either local dump or sending to remote clients.
 */
public class QueuePackets {
    private ZonedDateTime timeStamp = null;
    private LocalDate previousDate = null;
    private BlockingQueue messageQueueToLocalDump;
    private BlockingQueue messageQueueToRemoteClients;
    private ConnectionInfo localServerEndpoint;
    private long seqNum = 1;
    private QueueSizeChecker queueSizeChecker;
    private static final Logger logger = LogManager.getLogger("QueuePackets");

    /**
     * Creates QueuePackets class.
     *
     * @param messageQueueToLocalDump         Queue for storing messages destined for local dump.
     * @param messageQueueToRemoteClients     Queue for storing messages destined for remote clients.
     * @param localServerEndpoint             Metadata describing this hop for encoding with messages destined for remote clients.
     * @param queueSizeChecker                Helper class to check if packets queued has exceeded high water mark and pause frame needs to be sent.
     */
    public QueuePackets(BlockingQueue messageQueueToLocalDump, BlockingQueue messageQueueToRemoteClients,
                        ConnectionInfo localServerEndpoint, QueueSizeChecker queueSizeChecker) {
        this.messageQueueToLocalDump = messageQueueToLocalDump;
        this.messageQueueToRemoteClients = messageQueueToRemoteClients;
        this.localServerEndpoint = localServerEndpoint;
        this.queueSizeChecker = queueSizeChecker;
    }

    /**
     * Queues messages for local dump.
     *
     * @param itemToQueue
     */
    public void PutOnLocalQueue(byte[] itemToQueue) {
        // Perform deep copy
        byte[] itemToQueueCloned = new byte[itemToQueue.length];
        for (int i = 0; i < itemToQueue.length; i++) {
            itemToQueueCloned[i] = itemToQueue[i];
        }

        // Put new cloned item to queue after deep copy
        try {
            queueSizeChecker.checkQueue(messageQueueToLocalDump); // Check if ethernet pause frame needs to be sent
            // Packets of 100, 200, 400, 800, 1600, 9038 bytes in size takes 0.08us, 0.16us, 0.32us, 0.64us, 1.28us, 7.2304us to send at 10Gbps
            // We are offering up to twice as much time to queue after which it is likely due to queue being full
            Boolean queueStatus = messageQueueToLocalDump.offer(itemToQueueCloned, 15, TimeUnit.MICROSECONDS);
            if (!queueStatus) {
                logger.error("Local queue has " + messageQueueToLocalDump.remainingCapacity() + " remaining capacity available.  Following packet is discarded:\n" + ByteArrays.toHexString(itemToQueueCloned, " "));
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Encodes and queue messages for remote clients.
     *
     * @param itemToQueue
     */
    public void PutOnRemoteQueue(byte[] itemToQueue) {
        // Perform deep copy
        byte[] itemToQueueCloned = new byte[itemToQueue.length];
        for (int i = 0; i < itemToQueue.length; i++) {
            itemToQueueCloned[i] = itemToQueue[i];
        }

        timeStamp = ZonedDateTime.now(ZoneOffset.UTC);

        // SeqNum is reset after midnight
        if (previousDate == null ||
                timeStamp.toLocalDate().isAfter(previousDate)) {
            seqNum = 1;
            previousDate = timeStamp.toLocalDate();
        } else {
            // We will rather unexpectedly reset the seqNum rather than blowing up if we happens to have so many
            // packets per day to exceed the Long.MAX_VALUE value.
            if (seqNum < Long.MAX_VALUE) {
                seqNum++;
            } else {
                seqNum = 1;
            }
        }

        // Encode packet after deep copy
        ByteBuffer byteBuffer = MessageProcessor.encode(localServerEndpoint, timeStamp, seqNum, itemToQueueCloned);

        // Put new cloned & encoded item to queue
        try {
            queueSizeChecker.checkQueue(messageQueueToRemoteClients); // Check if ethernet pause frame needs to be sent
            // Packets of 100, 200, 400, 800, 1600, 9038 bytes in size takes 0.08us, 0.16us, 0.32us, 0.64us, 1.28us, 7.2304us to send at 10Gbps
            // We are offering up to twice as much time to queue after which it is likely due to queue being full
            Boolean queueStatus = messageQueueToRemoteClients.offer(byteBuffer, 15, TimeUnit.MICROSECONDS);
            if (!queueStatus) {
                logger.error("Remote queue has " + messageQueueToLocalDump.remainingCapacity() + " remaining capacity available.  Following packet is discarded:\n" + ByteArrays.toHexString(itemToQueueCloned, " "));
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
