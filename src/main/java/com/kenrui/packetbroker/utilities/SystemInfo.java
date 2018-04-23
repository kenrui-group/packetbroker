package com.kenrui.packetbroker.utilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pcap4j.core.PcapAddress;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;
import org.pcap4j.util.LinkLayerAddress;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

public class SystemInfo {
    private static final Logger logger = LogManager.getLogger("PacketBroker");

    public static void printAvailableCores() {
        StringBuilder processorInfoString = new StringBuilder("Processor info").append(System.lineSeparator());
        processorInfoString.append("-------------------------------").append(System.lineSeparator());
        processorInfoString.append("Processor identifier: " + System.getenv("PROCESSOR_IDENTIFIER")).append(System.lineSeparator());
        processorInfoString.append("Processor architecture: " + System.getenv("PROCESSOR_ARCHITECTURE")).append(System.lineSeparator());
        processorInfoString.append("Cores: " + Runtime.getRuntime().availableProcessors()).append(System.lineSeparator());

        logger.info(processorInfoString.append(System.lineSeparator()).toString());
    }

    public static void printMemory() {
        StringBuilder memoryInfoString = new StringBuilder("Memory info").append(System.lineSeparator());
        memoryInfoString.append("-------------------------------").append(System.lineSeparator());
        memoryInfoString.append("Free memory: " + Runtime.getRuntime().freeMemory() / 1024 / 1024 + "MB").append(System.lineSeparator());
        memoryInfoString.append("Max memory: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + "MB").append(System.lineSeparator());
        memoryInfoString.append("Total memory: " + Runtime.getRuntime().totalMemory() / 1024 / 1024 + "MB").append(System.lineSeparator());

        logger.info(memoryInfoString.append(System.lineSeparator()).toString());
    }

    public static void getInterfaces() {
        try {
            // Need to use NetworkInterface.getSubInterfaces() to get virtual interfaces per interface
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();

            while (e.hasMoreElements()) {
                NetworkInterface ni = e.nextElement();
                logger.info("Net interface: " + ni.getName() + " - " + ni.getDisplayName());

                Enumeration<InetAddress> e2 = ni.getInetAddresses();

                while (e2.hasMoreElements()) {
                    InetAddress ip = e2.nextElement();
                    logger.info("IP address: " + ip.toString());
                }
                logger.info("");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void getInterfacesDetail() throws PcapNativeException {
        List<PcapNetworkInterface> interfaceList = Pcaps.findAllDevs();

        StringBuilder interfaceListString = new StringBuilder("Interface info").append(System.lineSeparator());
        interfaceListString.append("-------------------------------").append(System.lineSeparator());
        for (PcapNetworkInterface interfaceItem : interfaceList) {
            interfaceListString.append("Interface description: " + interfaceItem.getDescription()).append(System.lineSeparator());
            interfaceListString.append("Libpcap name: " + interfaceItem.getName()).append(System.lineSeparator());
            List<PcapAddress> pcapAddresses = interfaceItem.getAddresses();
            for (PcapAddress pcapAddress : pcapAddresses) {
                if (pcapAddress.getNetmask() != null) {
                    interfaceListString.append("IP: " + pcapAddress.getAddress()).append("\t\t\t\t\t\t");
                    interfaceListString.append("Netmask: " + pcapAddress.getNetmask()).append(System.lineSeparator());
                } else {
                    interfaceListString.append("IP: " + pcapAddress.getAddress()).append(System.lineSeparator());
                }
            }

            List<LinkLayerAddress> linkLayerAddressesList = interfaceItem.getLinkLayerAddresses();
            for (LinkLayerAddress llAddress : linkLayerAddressesList) {
                interfaceListString.append("HwAddr: " + llAddress.toString()).append(System.lineSeparator()).append(System.lineSeparator());
            }
        }

        logger.info(interfaceListString.append(System.lineSeparator()).toString());

    }
}
