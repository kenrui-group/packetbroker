package com.kenrui.packetbroker.config;

import com.kenrui.packetbroker.helper.QueueSizeChecker;
import com.kenrui.packetbroker.structures.ConnectionInfo;
import com.kenrui.packetbroker.structures.EthernetPausePacket;
import com.kenrui.packetbroker.utilities.PacketUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Configuration
public class AppConfigQueuePacketsTest {

    Config defaultConfig = ConfigFactory.parseFile(new File("configs/defaults.conf"));

    @Bean
    public int packetSizeLowerLimit() {
        return 1;
    }

    // this is set to match value set for varDataEncoding's length in tunnel-schema.xml
    @Bean
    public int packetSizeUpperLimit() {
        return 9030;
    }

    @Bean
    public int queueSizeRemoteClients() {
        return defaultConfig.getInt("queueSizes.remoteClients");
    }

    @Bean
    public int queueSizeLocalDump() {
        return defaultConfig.getInt("queueSizes.localDump");
    }

    @Bean
    public int highWaterMark() {return defaultConfig.getInt("queueSizes.highWaterMark"); }

    @Bean
    public QueueSizeChecker queueSizeChecker() {
        return new QueueSizeChecker(highWaterMark(), new EthernetPausePacket(interfaceLocalCapture()));
    }

    @Bean
    public String interfaceLocalCapture() {
        return defaultConfig.getString("interfaces.localCapture");
    }

    @Bean
    public BlockingQueue messageQueueToRemoteClients () {
        return new ArrayBlockingQueue(queueSizeRemoteClients(), true);
    }

    @Bean
    public BlockingQueue messageQueueToLocalDump() {
        return new ArrayBlockingQueue(queueSizeLocalDump(), true);
    }


    @Bean
    public PacketUtils getPacketUtils() { return new PacketUtils();}

    @Bean
    public ConnectionInfo localServerEndpoint() {
        return new ConnectionInfo(defaultConfig.getString("localServer.ip"),
                defaultConfig.getInt("localServer.port"),
                defaultConfig.getString("localServer.hostname"),
                defaultConfig.getString("localServer.description"));
    }

}
