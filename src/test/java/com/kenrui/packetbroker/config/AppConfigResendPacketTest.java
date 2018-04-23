package com.kenrui.packetbroker.config;

import com.kenrui.packetbroker.resend.ResendPacket;
import com.kenrui.packetbroker.resend.ResendPacketHandler;
import com.kenrui.packetbroker.utilities.PacketUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.channels.Selector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

//@Profile("ResendPacketTest")
@Configuration
//@ComponentScan(basePackageClasses = {
//        ResendPacketTest.class,
//        ResendPacketHandlerTest.class
//})
public class AppConfigResendPacketTest {

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
    public BlockingQueue packetsToResendQueue() {
        int queueSizeResend = getQueueSizeResend();
        BlockingQueue packetsToResendQueue = new ArrayBlockingQueue(queueSizeResend, true);
        return packetsToResendQueue;
    }

    @Bean
    public PacketUtils getPacketUtils() {
        return Mockito.spy(new PacketUtils());
    }

//    public SelectorWrapper selectorWrapper;

    @Bean
    Selector getSelectorResendPacket() {
        // We tried to use a wrapper for Selector because Selector.open() is a static method and Mockito doesn't support static method
        // Turns out this is not required because with a mock we can instruct what it needs to do anyway in the test
        // This is also causing org.mockito.exceptions.misusing.NotAMockException for some reasons
//        selectorWrapper = Mockito.spy(new SelectorWrapper());
//        Selector selectorMock = selectorWrapper.getSelector();
//        return selectorMock;

        Selector selector = Mockito.mock(Selector.class, Mockito.RETURNS_DEEP_STUBS);
        return selector;
    }

    @Bean
    public ResendPacket resendPacket() {
        return Mockito.spy(new ResendPacket(threadCount()) {
            @Override
            public Callable<String> getResendPacketHandler() {
                ResendPacketHandler resendPacketHandler = new ResendPacketHandler(packetsToResendQueue(), threadTimeOut(), getSelectorResendPacket(), getPacketUtils());
                return Mockito.spy(resendPacketHandler);
            }
        });
    }
}
