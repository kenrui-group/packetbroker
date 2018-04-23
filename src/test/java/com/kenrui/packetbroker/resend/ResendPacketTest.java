package com.kenrui.packetbroker.resend;

import com.kenrui.packetbroker.config.AppConfigResendPacketTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

import static java.lang.Thread.sleep;

//@ActiveProfiles("ResendPacketTest")
@Component
@ContextConfiguration(classes=AppConfigResendPacketTest.class)
public class ResendPacketTest extends AbstractTestNGSpringContextTests {
    private static final Logger logger = LogManager.getLogger("ResendPacketTest");

    // Dependency
    @Autowired public int threadCount;

    // SUT
    @Autowired public ResendPacket resendPacket;

    @Test
    public void testCreateHandler() {
        Thread resendPacketThread = new Thread(resendPacket);
        resendPacketThread.setName("resendPacket");
        resendPacketThread.start();

        // We need to wait a bit to allow the parent ResendPacket thread to start the ResendPacketHandler child threads
        // Using Mockito to spy the classes being tested require significantly longer time
        // We are creating multiple handler threads, each are being spied so the time needed to wait are accumulative
        try  {
            sleep(1000);
        } catch (Exception e) {
            logger.error(e);
        }

        ThreadGroup resendPacketThreadGroup = resendPacketThread.getThreadGroup();
        ThreadGroup[] childThreadGroup = new ThreadGroup[resendPacketThreadGroup.activeGroupCount()];
        resendPacketThreadGroup.enumerate(childThreadGroup);

        // We are expecting only one child group exists, namely the ResendPacketHandler child thread group
        Assert.assertEquals(childThreadGroup.length, 1);

        // Check the number of ResendPacketHandler threads started are per configured
        int resendPacketHandlerThreadCount = childThreadGroup[0].activeCount();
        Assert.assertEquals(resendPacketHandlerThreadCount, threadCount);

        // Get list of ResendPacketHandler threads
        Thread[] threadList = new Thread[resendPacketHandlerThreadCount];
        childThreadGroup[0].enumerate(threadList);

        threadList[0].interrupt();

        // By default Executors.newCachedThreadPool kills threads after 60s of inactivity (after being interrupted) so we need to wait this long
        try  {
            sleep(61000);
        } catch (Exception e) {
            logger.error(e);
        }

        // Check threadDoneRoutine is called and not threadCancelledRoutine, which only happens if triggered by ResendPacket's executorService
        Mockito.verify(resendPacket).threadDoneRoutine();

        // Check the number of ResendPacketHandler threads now have been reduced
        resendPacketHandlerThreadCount = childThreadGroup[0].activeCount();
        Assert.assertEquals(resendPacketHandlerThreadCount, threadCount - 1);
    }
}
