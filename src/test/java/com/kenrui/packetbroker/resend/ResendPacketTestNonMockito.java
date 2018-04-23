package com.kenrui.packetbroker.resend;

import com.kenrui.packetbroker.config.AppConfigResendPacketTestNonMockito;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

import static java.lang.Thread.sleep;

//@ActiveProfiles("ResendPacketTestNonMockito")
@Component
@ContextConfiguration(classes=AppConfigResendPacketTestNonMockito.class)
public class ResendPacketTestNonMockito extends AbstractTestNGSpringContextTests {
    private static final Logger logger = LogManager.getLogger("ResendPacketTestNonMockito");

    @Autowired
    public ResendPacketProxy resendPacketProxy;

    @Autowired
    public int threadCount;

    @Test
    public void testCreateHandler() {
        Thread resendPacketThread = new Thread(resendPacketProxy);
        resendPacketThread.setName("resendPacket");
        resendPacketThread.start();

        // We need to wait a bit to allow the parent ResendPacket thread to start the ResendPacketHandler child threads
        try  {
            sleep(2000);
        } catch (Exception e) {
            logger.error(e);
        }

        ThreadGroup resendPacketThreadGroup = resendPacketThread.getThreadGroup();
        ThreadGroup[] childThreadGroup = new ThreadGroup[resendPacketThreadGroup.activeGroupCount()];
        resendPacketThreadGroup.enumerate(childThreadGroup);

        // We are expecting only two child groups exist, namely the ResendPacketHandler & Log4j child thread groups
        // todo: find out why threads actual changes from run to run
//        Assert.assertEquals(childThreadGroup.length, 2);

        // Check the number of ResendPacketHandler threads started are per configured
        // todo: find out why threads actual changes from run to run
        int resendPacketHandlerThreadCount = childThreadGroup[0].activeCount();
//        Assert.assertEquals(resendPacketHandlerThreadCount, threadCount);

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
        Assert.assertTrue(resendPacketProxy.threadDoneRoutineCalled);
        Assert.assertFalse(resendPacketProxy.threadCancelledRoutineCalled);

        // Check the number of ResendPacketHandler threads now have been reduced
        resendPacketHandlerThreadCount = childThreadGroup[0].activeCount();
        Assert.assertEquals(resendPacketHandlerThreadCount, threadCount - 1);
    }
}
