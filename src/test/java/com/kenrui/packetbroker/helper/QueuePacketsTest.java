package com.kenrui.packetbroker.helper;

import com.kenrui.packetbroker.config.AppConfigQueuePacketsTest;
import com.kenrui.packetbroker.structures.ConnectionInfo;
import com.kenrui.packetbroker.structures.DecodedMessages;
import com.kenrui.packetbroker.utilities.PacketUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;

@Component
@ContextConfiguration(classes=AppConfigQueuePacketsTest.class)
public class QueuePacketsTest extends AbstractTestNGSpringContextTests {
    private static final Logger logger = LogManager.getLogger("QueuePacketsTest");

    // Dependencies
    @Autowired public int packetSizeLowerLimit;
    @Autowired public int packetSizeUpperLimit; // this is set to match value set for varDataEncoding's length in tunnel-schema.xml
    @Autowired public BlockingQueue messageQueueToRemoteClients;
    @Autowired public BlockingQueue messageQueueToLocalDump;
    @Autowired public ConnectionInfo localServerEndpoint;
    @Autowired public PacketUtils packetUtils;

    // SUT
    public QueuePackets queuePackets;

    @BeforeMethod
    public void queuePackets() {
        queuePackets = new QueuePackets(messageQueueToLocalDump, messageQueueToRemoteClients, localServerEndpoint);
    }

    @Test
    public void testPutOnLocalQueue() throws InterruptedException {
        byte[] randomPacket = packetUtils.getRandomPacket(packetSizeLowerLimit, packetSizeUpperLimit);

        queuePackets.PutOnLocalQueue(randomPacket);

        Assert.assertArrayEquals(randomPacket, (byte[]) messageQueueToLocalDump.take());
    }

    @Test
    public void testPutOnRemoteQueue() throws InterruptedException {
        byte[] randomPacket = packetUtils.getRandomPacket(packetSizeLowerLimit, packetSizeUpperLimit);
        queuePackets.PutOnRemoteQueue(randomPacket);

        DecodedMessages decodedMessages = MessageProcessor.decode((ByteBuffer) messageQueueToRemoteClients.take());
        List<byte[]> decodedMessagesList = decodedMessages.getDecodedMsgList();

        // validate only one message is present
        Assert.assertEquals((long) 1, (long) decodedMessagesList.size());

        // validate no partial message is left over
        Assert.assertNull(decodedMessages.getLeftOverPartialMsg());

        // Validate content of message decoded is identical to message encoded
        // This allows us to test both encode and decode are working correctly
        // If we check the encoded output we need to provide a byte stream that is specific to a SBE schema
        // This also allows us to handle the case of the SBE schema changing
        Assert.assertArrayEquals(randomPacket, decodedMessagesList.get(0));
    }

}
