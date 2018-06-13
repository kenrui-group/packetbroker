package com.kenrui.packetbroker.structures;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pcap4j.core.*;
import org.pcap4j.packet.*;
import org.pcap4j.packet.namednumber.EtherType;
import org.pcap4j.util.ByteArrays;
import org.pcap4j.util.MacAddress;

public class EthernetPausePacket {
    public static final String PAUSE_DESTINATION = "01:80:C2:00:00:01";
    public static final String PAUSE_SOURCE = "00:00:00:00:00:00";
    public static final EtherType FLOW_CONTROL = new EtherType((short) 0x8808, "FLOW_CONTROL");
    public static final byte[] OP_CODE = {(byte) 0x00, (byte) 0x01};

    // 0xFFFF results in the following pause time
    // 10Gbps - 3.355ms
    // 1Gbps - 33.55ms
    public static final byte[] QUANTA_MAX = {(byte) 0xFF, (byte) 0xFF};
    public static final byte[] PAYLOAD = ByteArrays.concatenate(OP_CODE, QUANTA_MAX);
    private PcapNetworkInterface nif;
    private static final Logger logger = LogManager.getLogger("EthernetPausePacket");
    private PcapHandle sendHandle = null;
    private Packet pausePacket = null;

    public EthernetPausePacket(String NIF_NAME) {
        try {
            nif = Pcaps.getDevByName(NIF_NAME);
            if (nif == null) {
                return;
            }
        } catch (PcapNativeException e) {
            e.printStackTrace();
            return;
        }

        EthernetPacket.Builder ethernetBuilder = new EthernetPacket.Builder();
        MacAddress dstMacAddr = MacAddress.getByName(PAUSE_DESTINATION, ":");
        MacAddress srcMacAddr = MacAddress.getByName(PAUSE_SOURCE, ":");

        ethernetBuilder.dstAddr(dstMacAddr).srcAddr(srcMacAddr).type(FLOW_CONTROL).paddingAtBuild(true);
        ethernetBuilder.payloadBuilder(new UnknownPacket.Builder().rawData(PAYLOAD));


        try {
            sendHandle
                    = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10);
        } catch (PcapNativeException e) {
            e.printStackTrace();
        }

        pausePacket = ethernetBuilder.build();
    }

    public void sendPauseFrame() {
        try {
            sendHandle.sendPacket(pausePacket);
        } catch (PcapNativeException e) {
            e.printStackTrace();
        } catch (NotOpenException e) {
            e.printStackTrace();
        }
    }

    // This main method is not expected to be used other than to test the fact that pause frames can be sent in isolation
    public static void main(String[] args) {
        EthernetPausePacket epp = new EthernetPausePacket("\\Device\\NPF_{E65881B3-3E99-4651-91BB-7AF41DA17D01}");
        epp.sendPauseFrame();
    }
}
