package com.kenrui.packetbroker.config;

import com.kenrui.packetbroker.capture.PacketCapture;
import com.kenrui.packetbroker.clientserver.TunnelClient;
import com.kenrui.packetbroker.clientserver.TunnelServer;
import com.kenrui.packetbroker.dumplocal.PacketDump;
import com.kenrui.packetbroker.helper.QueuePackets;
import com.kenrui.packetbroker.helper.QueueSizeChecker;
import com.kenrui.packetbroker.resend.ResendPacket;
import com.kenrui.packetbroker.resend.ResendPacketHandler;
import com.kenrui.packetbroker.structures.ConnectionInfo;
import com.kenrui.packetbroker.structures.EthernetPausePacket;
import com.kenrui.packetbroker.utilities.PacketUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@ComponentScan("com.kenrui.packetbroker")
public class AppConfig {

//        Config defaultConfig = ConfigFactory.parseResources("defaults.conf");
    Config defaultConfig = ConfigFactory.parseFile(new File("configs/defaults.conf"));

    @Bean
    public int queueSizeRemoteClients() {
        return defaultConfig.getInt("queueSizes.remoteClients");
    }

    @Bean
    public int queueSizeLocalDump() {
        return defaultConfig.getInt("queueSizes.localDump");
    }

    @Bean
    public int queueSizeResend() {
        return defaultConfig.getInt("queueSizes.resend");
    }

    @Bean
    public int highWaterMark() {return defaultConfig.getInt("queueSizes.highWaterMark"); }

    @Bean
    public QueueSizeChecker queueSizeChecker() {
        return new QueueSizeChecker(highWaterMark(), new EthernetPausePacket(interfaceLocalCapture()));
    }

    @Bean
    public BlockingQueue messageQueueToRemoteClients() {
        return new ArrayBlockingQueue(queueSizeRemoteClients(), true);
    }

    @Bean
    public BlockingQueue messageQueueToLocalDump() {
        return new ArrayBlockingQueue(queueSizeLocalDump(), true);
    }

    @Bean
    public String interfaceLocalCapture() {
        return defaultConfig.getString("interfaces.localCapture");
    }

    @Bean
    public String interfaceLocalDump() {
        return defaultConfig.getString("interfaces.localDump");
    }

    @Bean
    public String interfaceResend() {
        return defaultConfig.getString("interfaces.resend");
    }

    @Bean
    public Boolean forwardLocalCapture() {
        return defaultConfig.getBoolean("forward.localCapture");
    }

    @Bean
    public Boolean forwardRemoteCapture() {
        return defaultConfig.getBoolean("forward.remoteCapture");
    }

    @Bean
    public ConnectionInfo localServerEndpoint() {
        return new ConnectionInfo(defaultConfig.getString("localServer.ip"),
                defaultConfig.getInt("localServer.port"),
                defaultConfig.getString("localServer.hostname"),
                defaultConfig.getString("localServer.description"));
    }

    // Helper class to put packets on queues
    @Bean
    public QueuePackets queuePackets() {
        return new QueuePackets(messageQueueToLocalDump(), messageQueueToRemoteClients(), localServerEndpoint(), queueSizeChecker());
    }

    @Bean
    public List<? extends ConfigObject> remoteServerList() {
        return defaultConfig.getObjectList("remoteServers");
    }

    // This list defines a list of remote servers to connect to
    @Bean
    public List<ConnectionInfo> remoteServers() {
        List<ConnectionInfo> remoteServers = Collections.synchronizedList(new ArrayList<>());
        for (ConfigObject remoteServer : remoteServerList()) {
            ConnectionInfo remoteServerEndpoint =
                    new ConnectionInfo(remoteServer.toConfig().getString("ip"),
                            remoteServer.toConfig().getInt("port"),
                            remoteServer.toConfig().getString("hostname"),
                            remoteServer.toConfig().getString("description"));
            remoteServers.add(remoteServerEndpoint);
        }
        return remoteServers;
    }

    /**
     * This map is used to store all the incoming client connections so we know where to send the packets to.
     * It will be passed to both PacketCapture's call back (PacketCaptureCallback) and TunnelServer.
     * TunnelServer will add an entry per remote client.
     * PacketCaptureCallback will check if there are remote clients before deciding whether or not to
     * put captured packet on a queue to be sent out by TunnelServer.
     * <p>
     * The Boolean value is set to false by default and updated to true if a packet is sent
     * successfully to the remote client.
     */
    @Bean
    public ConcurrentHashMap<SocketChannel, Boolean> remoteClients() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public TunnelServer tunnelServer() throws IOException {
        return new TunnelServer(localServerEndpoint().getPort(),
                messageQueueToRemoteClients(),
                packetsToResendQueue(),
                remoteClients(),
                resend(), getSelectorTunnelServer(), getServerSocketChannel(), getPacketUtils());
    }

    /**
     * Check if we have an interface defined to resend packets that were not successful
     * when first captured due to slow consumer / dead client / bad connection
     */
    @Bean
    public Boolean resend() {
        if (interfaceResend().length() == 0) {
            return Boolean.FALSE;
        } else {
            return Boolean.TRUE;
        }
    }

    /**
     * Check if we have an interface defined to dump packets locally
     * Packets will be taken off messageQueueToLocalDump and dumped
     * on local interface
     */
    @Bean
    public Boolean dumpLocal() {
        if (interfaceLocalDump().length() == 0) {
            return Boolean.FALSE;
        } else {
            return Boolean.TRUE;
        }
    }

    @Bean
    public PacketCapture packetCapture() {
        return new PacketCapture(interfaceLocalCapture(),
                remoteClients(),
                queuePackets(),
                dumpLocal(),
                forwardLocalCapture());
    }

    @Bean
    public PacketDump packetDump() {
        return new PacketDump(messageQueueToLocalDump(),
                interfaceLocalDump());
    }

    @Bean
    public TunnelClient tunnelClient() throws IOException {
        return new TunnelClient(remoteServers(),
                queuePackets(),
                dumpLocal(),
                forwardRemoteCapture());
    }

    @Bean
    public int threadCount() {
        return defaultConfig.getInt("resend.threadCount");
    }

    @Bean
    public int threadTimeOut() {
        return defaultConfig.getInt("resend.threadTimeOut");
    }

    @Bean
    public int getQueueSizeResend() {
        return defaultConfig.getInt("queueSizes.resend");
    }

    @Bean
    public BlockingQueue packetsToResendQueue() {
        int queueSizeResend = getQueueSizeResend();
        BlockingQueue packetsToResendQueue = new ArrayBlockingQueue(queueSizeResend, true);
        return packetsToResendQueue;
    }

    @Bean
    Selector getSelectorTunnelServer() {
        Selector selector = null;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return selector;
    }

    @Bean
    Selector getSelectorResendPacket() {
        Selector selector = null;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return selector;
    }

    @Bean
    ServerSocketChannel getServerSocketChannel() {
        ServerSocketChannel serverSocketChannel = null;
        try {
            serverSocketChannel = ServerSocketChannel.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return serverSocketChannel;
    }

    @Bean
    public PacketUtils getPacketUtils() {
        return new PacketUtils();
    }

    @Bean
    public ResendPacket resendPacket() {
        return new ResendPacket(threadCount()) {
            @Override
            public Callable<String> getResendPacketHandler() {
                return new ResendPacketHandler(packetsToResendQueue(), threadTimeOut(), getSelectorResendPacket(), getPacketUtils());

            }
        };
    }

//    @Bean
//    public ResendPacket resendPacket() {
//        return new ResendPacket(threadCount()) {
//            @Override
//            public Callable<String> getResendPacketHandler() {
//                ResendPacketHandlerStub resendPacketHandlerTest = new ResendPacketHandlerStub(packetsToResendQueue(), threadTimeOut());
//                return resendPacketHandlerTest;
//            }
//        };
//    }
}
