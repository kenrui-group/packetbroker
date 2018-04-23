package com.kenrui.packetbroker.capture;

import com.kenrui.packetbroker.helper.QueuePackets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pcap4j.core.*;

import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packet capture thread for interfaces on local host.
 */
public class PacketCapture implements Runnable {
    private PcapNetworkInterface nif;
    private ConcurrentHashMap<SocketChannel, Boolean> remoteClients;
    private QueuePackets queuePackets;
    private RawPacketListener listener;
    private Boolean dumpLocal, forwardLocalCapture;
    private static final Logger logger = LogManager.getLogger("PacketCapture");

    /**
     * Creates packet capture thread.  Uses PacketCaptureCallback per packet sniffed.
     * @param NIF_NAME Local interface to sniff.
     * @param remoteClients List of remote clients to send sniffed packets to.
     * @param queuePackets Queue to store packets sniffed.
     * @param dumpLocal Determine if we have configured an interface to dump packets locally.
     * @param forwardLocalCapture Determine if we forward packets captured locally.
     */
    public PacketCapture(String NIF_NAME, ConcurrentHashMap<SocketChannel, Boolean> remoteClients,
                         QueuePackets queuePackets, Boolean dumpLocal, Boolean forwardLocalCapture) {
        try {
            nif = Pcaps.getDevByName(NIF_NAME);
            if (nif == null) {
                return;
            }
        } catch (PcapNativeException e) {
            e.printStackTrace();
            return;
        }
        this.remoteClients = remoteClients;
        this.queuePackets = queuePackets;
        this.dumpLocal = dumpLocal;
        this.forwardLocalCapture = forwardLocalCapture;

        listener = new PacketCaptureCallback(this.remoteClients, this.queuePackets,
                this.dumpLocal, this.forwardLocalCapture);
    }

    @Override
    public void run() {
        PcapHandle handle = null;

        try {
            logger.info(nif.getName() + "(" + nif.getDescription() + ")");
//            todo: Allow snaplen and timeout to be configurable.
            handle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10);
        } catch (PcapNativeException e) {
            e.printStackTrace();
        }

        try {
//            todo: Review use of threadpool as a configurable option.  Luxury item and not much use case applicable.
//            ExecutorService pool = Executors.newCachedThreadPool();
//            handle.loop(-1, listener, pool);
//            pool.shutdown();
            handle.loop(-1, listener);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (PcapNativeException e) {
            e.printStackTrace();
        } catch (NotOpenException e) {
            e.printStackTrace();
        }

        handle.close();
    }
}