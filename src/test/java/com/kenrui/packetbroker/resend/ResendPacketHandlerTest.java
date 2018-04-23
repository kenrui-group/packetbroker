package com.kenrui.packetbroker.resend;

import com.kenrui.packetbroker.config.AppConfigResendPacketTest;
import com.kenrui.packetbroker.structures.PacketToResend;
import com.kenrui.packetbroker.utilities.PacketUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@ActiveProfiles("ResendPacketTest")
@Component
@ContextConfiguration(classes=AppConfigResendPacketTest.class)
public class ResendPacketHandlerTest extends AbstractTestNGSpringContextTests {
    private static final Logger logger = LogManager.getLogger("ResendPacketHandlerTest");

    // Dependencies
    @Autowired public BlockingQueue packetsToResendQueue;
    @Autowired public int threadTimeOut;
    @Autowired public PacketUtils packetUtils;
    private Set keySet = new HashSet<SelectionKey>(); // Create a HashSet to hold SelectionKeys of interest
    private ConcurrentHashMap<SocketChannel, Boolean> remoteClients;
    private int packetSizeLowerLimit = 1;
    private int packetSizeUpperLimit = 9030;
    private ByteBuffer byteBuffer;
    private int randomNum;
    private int portBase = 1000;
    private int clientsToResend = 0;
    private ArgumentCaptor<ConcurrentHashMap> remoteClientsCaptor;
    private ArgumentCaptor<ByteBuffer> byteBufferCaptor;
    private ArgumentCaptor<SocketChannel> socketChannelCaptor;
    private ArgumentCaptor<Logger> loggerCaptor;

    // Collaborators
    // We have two collaborators for the test, Selector, injected here, and SocketChannel, created in method setUpRemoteClients().
    // By injecting Selector we can control the SelectionKeys to be returned which then allows us to control whether
    // each SocketChannel associated with each SelectionKey is writable or not.
    @Autowired public Selector selector;

    // SUT
    private ResendPacketHandler resendPacketHandlerThread;

    @BeforeMethod
    public void setup() throws IOException {
        remoteClients = new ConcurrentHashMap<>();

        // Create the fake packet that some of the remote clients missed receiving
        randomNum = ThreadLocalRandom.current().nextInt(packetSizeLowerLimit, packetSizeUpperLimit);
        byte[] bytes = new byte[randomNum];
        new Random().nextBytes(bytes);
        byteBuffer = ByteBuffer.wrap(bytes);

        remoteClientsCaptor = ArgumentCaptor.forClass(ConcurrentHashMap.class);
        byteBufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
        socketChannelCaptor = ArgumentCaptor.forClass(SocketChannel.class);
        loggerCaptor = ArgumentCaptor.forClass(Logger.class);
    }

    @AfterMethod
    public void tearDown() {
        // Clear SelectionKeys from previous runs
        keySet.clear();

        // Reset the spy so it won't remember which method we have called on the previous runs
        Mockito.reset(packetUtils);
    }


    public void setUpRemoteClients(Boolean channelWritable) throws IOException {


        // Create a number of remote clients running on the same remote server but connecting from different ports
        // Put each of these remote client into the remoteClients HashMap, marking even ones as having missed a packet and needs to be resent
        for (int port = portBase; port <= portBase + 5; port++) {
            SocketChannel socketChannel = Mockito.mock(SocketChannel.class);
            SocketAddress socketAddress = new InetSocketAddress("mockremoteclient.kenrui.com", port);
            Mockito.when(socketChannel.getRemoteAddress()).thenReturn(socketAddress);
            Mockito.when(socketChannel.write(byteBuffer)).thenReturn(randomNum);
            Mockito.when(socketChannel.setOption(Mockito.any(), Mockito.anyBoolean())).thenReturn(socketChannel);

            // One SelectionKey to be created for each Selector & SocketChannel combination
            SelectionKey selectionKey = Mockito.spy(new SelectionKey() {
                @Override
                public SelectableChannel channel() {
                    return socketChannel;
                }

                @Override
                public Selector selector() {
                    return selector;
                }

                @Override
                public boolean isValid() {
                    return true;
                }

                @Override
                public void cancel() {

                }

                @Override
                public int interestOps() {
                    return 0;
                }

                @Override
                public SelectionKey interestOps(int ops) {
                    return null;
                }

                @Override
                public int readyOps() {
                    return 0;
                }
            });
            Mockito.when(selectionKey.isValid()).thenReturn(Boolean.TRUE);

            // Depending on the test case we may or may not want to simulate remote clients are available or all dead
            if (channelWritable) {
                // This is to test SUT sending packet to remote clients
                Mockito.when(selectionKey.isWritable()).thenReturn(Boolean.TRUE);
            } else {
                // This is to test SUT putting packet on DLQ
                Mockito.when(selectionKey.isWritable()).thenReturn(Boolean.FALSE);
            }

            // SUT only registers those remote clients that need to be resent so we are only keeping the SelectionKeys for these specific clients
            Mockito.doAnswer(new Answer() {
                @Override
                public SelectionKey answer(InvocationOnMock invocationOnMock) throws Throwable {
                    logger.info("Registering Selector for " + ((SocketChannel) selectionKey.channel()).getRemoteAddress());
                    keySet.add(selectionKey);
                    return selectionKey;
                }
            }).when(socketChannel).register(Mockito.any(Selector.class), Mockito.eq(SelectionKey.OP_WRITE));

            Mockito.doAnswer(new Answer() {
                @Override
                public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                    ByteBuffer byteBuffer = invocationOnMock.getArgument(0);
                    byteBuffer.position(byteBuffer.limit());
                    return byteBuffer.limit();
                }
            }).when(socketChannel).write((ByteBuffer) Mockito.any());

            // Mark remote mock clients with even port numbers to have missed packet and needs to be resent
            if (port % 2 == 0) {
                remoteClients.put(socketChannel, Boolean.FALSE);
                clientsToResend++;
            } else {
                remoteClients.put(socketChannel, Boolean.TRUE);
            }
        }

        // Create the PacketToResend object for the fake packet and the remote clients
        PacketToResend packetToResend = new PacketToResend(byteBuffer, remoteClients);
        packetsToResendQueue.add(packetToResend);

        Mockito.when(selector.selectNow()).thenReturn(clientsToResend);
        Mockito.when(selector.selectedKeys()).thenReturn(keySet);

        // Following is assuming we are using a spy on SelectorWrapper
//        Mockito.doReturn(clientsToResend).when(selector).selectNow();
//        Mockito.doReturn(keySet).when(selector).selectedKeys();

        // Create a handler thread for testing and spy it
        resendPacketHandlerThread = Mockito.spy(new ResendPacketHandler(packetsToResendQueue, threadTimeOut, selector, packetUtils));
    }

    @Test
    public void testResendPacketHandler() throws IOException, InterruptedException {
        setUpRemoteClients(Boolean.TRUE);

        // Run the thread
        Assert.assertEquals(packetsToResendQueue.size(), 1);
        resendPacketHandlerThread.callableTask();
        Assert.assertEquals(packetsToResendQueue.size(),0);

        // Check we have actually sent to 'clientsToResend' clients that were previously marked to have not received the packet
//        Mockito.verify(resendPacketHandlerThread, Mockito.times(clientsToResend)).sendPacket(remoteClientsCaptor.capture(),
//                byteBufferCaptor.capture(),
//                selectionKeyCaptor.capture());
        Mockito.verify(packetUtils, Mockito.times(clientsToResend)).sendPacketAndUpdateStatus(remoteClientsCaptor.capture(),
                byteBufferCaptor.capture(), socketChannelCaptor.capture(), loggerCaptor.capture());

        // Check each time we attempted to send the same packet that needed to be sent
        List<ByteBuffer> byteBufferList = byteBufferCaptor.getAllValues();
        for (ByteBuffer byteBuffer : byteBufferList) {
            Assert.assertEquals(byteBuffer, this.byteBuffer);
        }

        // Assert all clients who missed receiving packet now have all been sent the packet
        Assert.assertFalse(remoteClients.containsValue(Boolean.FALSE));

        // Assert no packets were put on DLQ
        Mockito.verify(packetUtils, Mockito.never()).putOnDLQ(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void testResendPacketHandlerTimeOut() throws IOException, InterruptedException {
        setUpRemoteClients(Boolean.FALSE);

        // For this specific test case we want to explicitly control the resend time out value
        // This ensures we don't wait too long for the test if the value set in config file is too large, or
        // we don't wait sufficiently long enough if the value set in config file is too low.
        resendPacketHandlerThread.setThreadTimeOut(5000);

        // Run the thread
        Assert.assertEquals(packetsToResendQueue.size(), 1);
        resendPacketHandlerThread.callableTask();
        Assert.assertEquals(packetsToResendQueue.size(),0);

        // Check we have actually sent to 'clientsToResend' clients that were previously marked to have not received the packet
//        Mockito.verify(resendPacketHandlerThread, Mockito.times(0)).sendPacket(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(packetUtils, Mockito.times(0)).sendPacketAndUpdateStatus(Mockito.any(),
                Mockito.any(), Mockito.any(), Mockito.any());

        // Assert we still have clients not received the missed packet
        Assert.assertTrue(remoteClients.containsValue(Boolean.FALSE));

        // Assert packet was put on DLQ once
        Mockito.verify(packetUtils, Mockito.times(1)).putOnDLQ(Mockito.any(), byteBufferCaptor.capture(), Mockito.any());

        // Check each time we attempted to send the same packet that needed to be sent
        List<ByteBuffer> byteBufferList = byteBufferCaptor.getAllValues();
        for (ByteBuffer byteBuffer : byteBufferList) {
            Assert.assertEquals(byteBuffer, this.byteBuffer);
        }
    }

}
