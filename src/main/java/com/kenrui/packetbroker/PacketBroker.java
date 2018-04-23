package com.kenrui.packetbroker;

import com.kenrui.packetbroker.capture.PacketCapture;
import com.kenrui.packetbroker.clientserver.TunnelClient;
import com.kenrui.packetbroker.clientserver.TunnelServer;
import com.kenrui.packetbroker.config.AppConfig;
import com.kenrui.packetbroker.dumplocal.PacketDump;
import com.kenrui.packetbroker.helper.QueuePackets;
import com.kenrui.packetbroker.resend.ResendPacket;
import com.kenrui.packetbroker.structures.ConnectionInfo;
import com.kenrui.packetbroker.utilities.SystemInfo;
import com.typesafe.config.ConfigObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;

import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ContextConfiguration(classes=AppConfig.class)
public class PacketBroker {
    private static final Logger logger = LogManager.getLogger("PacketBroker");

    @Autowired public int queueSizeRemoteClients;
    @Autowired public int queueSizeLocalDump;
    @Autowired public int queueSizeResend;
    @Autowired public BlockingQueue messageQueueToRemoteClients;
    @Autowired public BlockingQueue messageQueueToLocalDump;
    @Autowired public BlockingQueue packetsToResendQueue;
    @Autowired public String interfaceLocalCapture;
    @Autowired public String interfaceLocalDump;
    @Autowired public String interfaceResend;
    @Autowired public Boolean forwardLocalCapture;
    @Autowired public Boolean forwardRemoteCapture;
    @Autowired public ConnectionInfo localServerEndpoint;
    @Autowired public QueuePackets queuePackets;
    @Autowired public List<? extends ConfigObject> remoteServerList;
    @Autowired public List<ConnectionInfo> remoteServers;
    @Autowired public Boolean resend;
    @Autowired public Boolean dumpLocal;
    @Autowired public ConcurrentHashMap<SocketChannel, Boolean> remoteClients;

    public PacketBroker() {

    }

    public static void main(String[] args) {
        try {
            ApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);

            logger.info("Started Packet Broker");

            SystemInfo.printAvailableCores();
            SystemInfo.printMemory();
            SystemInfo.getInterfacesDetail();

            PacketBroker packetBroker = context.getBean(PacketBroker.class);

            TunnelServer tunnelServer = context.getBean(TunnelServer.class);
            Thread tunnelServerThread = new Thread(tunnelServer);
            tunnelServerThread.setName("tunnelServer");
            tunnelServerThread.start();
            logger.info("Started tunnel server on " + tunnelServerThread.getName() + " listening on " + packetBroker.localServerEndpoint.getPort() + ".");

            /**
             * Start capture on interface NIF_NAME
             * This will start PacketCaptureCallback to put packets on
             * both messageQueueToLocalDump and messageQueueToRemoteClients
             */
            PacketCapture packetCapture = context.getBean(PacketCapture.class);
            Thread packetCaptureThread = new Thread(packetCapture);
            packetCaptureThread.setName("packetCapture");
            packetCaptureThread.start();
            logger.info("Started packet capture on " + packetCaptureThread.getName() + " to receive packets from local PacketBroker");

            Thread packetDumpThread;
            if (packetBroker.dumpLocal == Boolean.TRUE) {
                PacketDump packetDump = context.getBean(PacketDump.class);
                packetDumpThread = new Thread(packetDump);
                packetDumpThread.start();
                logger.info("Started packet dump on " + packetDumpThread.getName() + " to send packets to local interface.");
            }

            /**
             * Start tunnel client connections to remote tunnel servers
             * todo:  This require enhancement to handle remote servers not ready and have to try again.
             */
            TunnelClient tunnelClient = context.getBean(TunnelClient.class);
            Thread tunnelClientThread = new Thread(tunnelClient);
            tunnelClientThread.setName("tunnelClient");
            tunnelClientThread.start();
            logger.info("Started tunnel client on " + tunnelClientThread.getName());


            /**
             * Start thread to resend packets that were not successful when first captured due to
             * slow consumer / dead client / bad connection
             */
            Thread resendPacketHandlerThread;
            if (packetBroker.resend == Boolean.TRUE) {
                ResendPacket resendPacket = context.getBean(ResendPacket.class);
                resendPacketHandlerThread = new Thread(resendPacket);
                resendPacketHandlerThread.setName("resendPacket");
                resendPacketHandlerThread.start();
                logger.info("Started resend packet handler on " + resendPacketHandlerThread.getName());
            }

            while (true) {
                logger.info(tunnelServerThread.getName() + " isAlive: " + tunnelServerThread.isAlive());
                logger.info(packetCaptureThread.getName() + " isAlive: " + packetCaptureThread.isAlive());
                logger.info(tunnelClientThread.getName() + " isAlive: " + tunnelClientThread.isAlive());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
