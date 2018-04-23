package com.kenrui.packetbroker.config;

import com.kenrui.packetbroker.structures.ConnectionInfo;
import com.kenrui.packetbroker.utilities.PacketUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;


@Configuration
public class AppConfigTunnelServerTest {

    Config defaultConfig = ConfigFactory.parseResources("defaults.conf");

    @Bean
    public int threadCount() {
        return 4;
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
    public String interfaceResend() {
        return defaultConfig.getString("interfaces.resend");
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

    @Bean
    public ConnectionInfo localServerEndpoint() {
        return new ConnectionInfo(defaultConfig.getString("localServer.ip"),
                defaultConfig.getInt("localServer.port"),
                defaultConfig.getString("localServer.hostname"),
                defaultConfig.getString("localServer.description"));
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
    public BlockingQueue packetsToResendQueue() {
        int queueSizeResend = getQueueSizeResend();
        BlockingQueue packetsToResendQueue = new ArrayBlockingQueue(queueSizeResend, true);
        return packetsToResendQueue;
    }

    @Bean
    ServerSocketChannel getServerSocketChannel() {
        ServerSocketChannel serverSocketChannel = Mockito.mock(ServerSocketChannel.class, Mockito.RETURNS_DEEP_STUBS);
        return serverSocketChannel;
    }

    @Bean
    public PacketUtils getPacketUtils() {
        return Mockito.spy(new PacketUtils());
    }

//    public SelectorWrapper selectorWrapper;

    @Bean
    Selector getSelector() throws IOException {
        // We tried to use a wrapper for Selector because Selector.open() is a static method and Mockito doesn't support static method
        // Turns out this is not required because with a mock we can instruct what it needs to do anyway in the test
        // This is also causing org.mockito.exceptions.misusing.NotAMockException for some reasons
//        selectorWrapper = Mockito.spy(new SelectorWrapper());
//        Selector selectorMock = selectorWrapper.getSelector();
//        return selectorMock;

        Selector selector = Mockito.mock(Selector.class, Mockito.RETURNS_DEEP_STUBS);
        return selector;
    }
}
