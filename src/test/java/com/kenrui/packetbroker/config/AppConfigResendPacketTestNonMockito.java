package com.kenrui.packetbroker.config;

import com.kenrui.packetbroker.resend.ResendPacketHandlerStub;
import com.kenrui.packetbroker.resend.ResendPacketProxy;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

//@Profile("ResendPacketTestNonMockito")
@Configuration
//@ComponentScan(basePackageClasses = {
//        ResendPacketTestNonMockito.class
//})
public class AppConfigResendPacketTestNonMockito {

    Config defaultConfig = ConfigFactory.parseFile(new File("configs/defaults.conf"));

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
    public ResendPacketProxy resendPacketProxy() {
        return new ResendPacketProxy(threadCount()) {
            @Override
            public Callable<String> getResendPacketHandler() {
                ResendPacketHandlerStub resendPacketHandlerStub = new ResendPacketHandlerStub(packetsToResendQueue(), threadTimeOut());
                return resendPacketHandlerStub;
            }
        };
    }
}
