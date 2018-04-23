package com.kenrui.packetbroker.capture;

import com.kenrui.packetbroker.helper.QueuePackets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pcap4j.core.RawPacketListener;

import java.nio.channels.SocketChannel;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Call back used by packet capture thread.
 */
public class PacketCaptureCallback implements RawPacketListener {
    private ConcurrentHashMap<SocketChannel, Boolean> remoteClients;
    private QueuePackets queuePackets;
    private StringBuffer capturedMessage;
    private Boolean dumpLocal, forwardLocalCapture;
    private static final Logger logger = LogManager.getLogger("PacketCapture");

    /**
     * Creates call back for processing packet captured locally
     * @param remoteClients List of remote clients to indicate if there are any remote clients to send packets to.
     * @param queuePackets Helper class to put packets on queues for either local dump or sending to remote clients.
     * @param dumpLocal Determine if we have configured an interface to dump packets locally.
     * @param forwardLocalCapture Determine if we need to packets captured locally to remote clients.
     */
    public PacketCaptureCallback(ConcurrentHashMap<SocketChannel, Boolean> remoteClients,
                                 QueuePackets queuePackets, Boolean dumpLocal, Boolean forwardLocalCapture) {
        this.remoteClients = remoteClients;
        this.queuePackets = queuePackets;
        this.dumpLocal = dumpLocal;
        this.forwardLocalCapture = forwardLocalCapture;
    }

    /**
     * Call back method for each sniffed packet.
     * Each packet needs to be sent to two queues.  Deep copy is performed before queueing to ensure
     * there is no accidental modification to referenced packet before consumption is finished on both queues.
     * messageQueueToLocalDump - used for dumping on local interface
     * messageQueueToRemoteClients - used for sending to remote clients via tunnel
     * @param packet
     */
    @Override
    public void gotPacket(byte[] packet) {
        // Timestamps each packet sniffed
        ZonedDateTime timeStamp = ZonedDateTime.now(ZoneOffset.UTC);

        // A thread pool may have been used to call libpcap's loop function so this callback can run in multiple threads.
        // Building the message string and printing it out at the end guarantees that the messages from different threads will not interleave.
//        capturedMessage.delete(0, capturedMessage.length());
//        capturedMessage.append(timeStamp)
//                .append(" - ")
//                .append(Thread.currentThread().getName())
//                .append(" - Captured ")
//                .append(packet.length)
//                .append(" bytes - ")
//                .append(ByteArrays.toHexString(packet, " "));

        // Put packet on messageQueueToLocalDump only if a local interface has been configured for local dump
        if (dumpLocal == Boolean.TRUE) {
            queuePackets.PutOnLocalQueue(packet);
        }

        // Put packet on messageQueueToRemoteClients only if there are remote clients and configured to do so
        if (!remoteClients.isEmpty() && forwardLocalCapture == Boolean.TRUE) {
            queuePackets.PutOnRemoteQueue(packet);
        } else {
//            capturedMessage.append("\n  No clients to send packets to");
        }

//        logger.trace(capturedMessage);
    }
}

