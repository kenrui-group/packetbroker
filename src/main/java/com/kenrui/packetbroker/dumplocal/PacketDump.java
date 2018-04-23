package com.kenrui.packetbroker.dumplocal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pcap4j.core.*;
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode;

import java.util.concurrent.BlockingQueue;

/**
 * Packet dump thread to put locally captured or remotely captured packets on local interface.
 */
public class PacketDump implements Runnable {
    private PcapNetworkInterface nif;
    private PcapHandle sendHandle;
    private BlockingQueue messageQueueToLocalDump;
    private static final int SNAPLEN = 65536;
    private static final int READ_TIMEOUT = 10;
    private static final Logger logger = LogManager.getLogger("PacketDump");

    /**
     * Creates a packet dump thread.
     * @param messageQueueToLocalDump  Queue with aggregated packets from local or remote.
     * @param NIF_NAME Interface where packet capture or decoding appliance is connected to.
     */
    public PacketDump(BlockingQueue messageQueueToLocalDump, String NIF_NAME) {
        this.messageQueueToLocalDump = messageQueueToLocalDump;
        try {
            nif = Pcaps.getDevByName(NIF_NAME);
            if (nif == null) {
                return;
            }

            // todo: Allow snaplen and timeout to be configurable.
            sendHandle = nif.openLive(SNAPLEN, PromiscuousMode.PROMISCUOUS, READ_TIMEOUT);
        } catch (PcapNativeException e) {
            e.printStackTrace();
            return;
        }
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                byte[] packet = (byte[]) messageQueueToLocalDump.take();
                logger.trace("Dumping packet on local interface" + nif.getName() + "(" + nif.getDescription() + ")");
                sendHandle.sendPacket(packet);
            } catch (InterruptedException | NotOpenException | PcapNativeException e) {
                e.printStackTrace();
            }
        }
    }
}
