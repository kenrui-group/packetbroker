package com.kenrui.packetbroker.clientserver;

import com.kenrui.packetbroker.config.AppConfigTunnelServerTest;
import com.kenrui.packetbroker.structures.ConnectionInfo;
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
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;


@ActiveProfiles("TunnelServerTest")
@Component
@ContextConfiguration(classes = AppConfigTunnelServerTest.class)
public class TunnelServerTest extends AbstractTestNGSpringContextTests {
    private static final Logger logger = LogManager.getLogger("TunnelServerTest");

    // Dependencies
    @Autowired
    public ConnectionInfo localServerEndpoint;
    @Autowired
    public BlockingQueue messageQueueToRemoteClients;
    @Autowired
    public BlockingQueue packetsToResendQueue;
    @Autowired
    public ConcurrentHashMap<SocketChannel, Boolean> remoteClients;
    @Autowired
    public Boolean resend;
    @Autowired
    public int threadTimeOut;
    @Autowired
    public PacketUtils packetUtils;
    @Autowired
    private ServerSocketChannel serverSocketChannel;

    private enum SelectionKeyStatus {
        INVALID, ACCEPTABLE, WRITABLE, READABLE
    }

    private ServerSocket serverSocket;
    private Set keySet = new HashSet<SelectionKey>(); // Create a HashSet to hold SelectionKeys of interest
    private SocketChannel socketChannel;
    private SocketAddress socketAddress;
    private int packetSizeLowerLimit = 1;
    private int packetSizeUpperLimit = 9030;
    private ByteBuffer byteBuffer;
    private int randomNum;
    private int portBase = 1000;
    private ArgumentCaptor<ConcurrentHashMap> remoteClientsCaptor;
    private ArgumentCaptor<ByteBuffer> byteBufferCaptor;
    private ArgumentCaptor<SocketChannel> socketChannelCaptor;
    private ArgumentCaptor<Logger> loggerCaptor;

    // Collaborators
    // We have two collaborators for the test, Selector, injected here, and SocketChannel, created in method setUpRemoteClients().
    // By injecting Selector we can control the SelectionKeys to be returned which then allows us to control whether
    // each SocketChannel associated with each SelectionKey is writable or not.
    @Autowired
    public Selector selector;

    // SUT
    private TunnelServer tunnelServerThread;

    public TunnelServerTest() {
    }

    @BeforeMethod
    public void setup() throws IOException {
        remoteClients = new ConcurrentHashMap<>();

        // Create the fake packet that some of the remote clients missed receiving
        randomNum = ThreadLocalRandom.current().nextInt(packetSizeLowerLimit, packetSizeUpperLimit);
        byte[] bytes = new byte[randomNum];
        new Random().nextBytes(bytes);
        byteBuffer = ByteBuffer.wrap(bytes);

        messageQueueToRemoteClients.add(byteBuffer);

        remoteClientsCaptor = ArgumentCaptor.forClass(ConcurrentHashMap.class);
        byteBufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
        socketChannelCaptor = ArgumentCaptor.forClass(SocketChannel.class);
        loggerCaptor = ArgumentCaptor.forClass(Logger.class);
    }

    @AfterMethod
    public void tearDown() {
        // Clear SelectionKeys from previous runs
        keySet.clear();

        // testSocketAcceptable() won't consume so need to clear for testSocketWritable()
        messageQueueToRemoteClients.clear();
    }


    public void setUpRemoteClients(SelectionKeyStatus selectionKeyStatus) throws IOException {


        // Create a number of remote clients running on the same remote server but connecting from different ports
        // Put each of these remote client into the remoteClients HashMap, marking even ones as having missed a packet and needs to be resent

        socketChannel = Mockito.mock(SocketChannel.class);
        serverSocketChannel = Mockito.mock(ServerSocketChannel.class);
        socketAddress = new InetSocketAddress("mockremoteclient.kenrui.com", portBase);
        Mockito.when(serverSocketChannel.accept()).thenReturn(socketChannel);
        Mockito.when(socketChannel.getRemoteAddress()).thenReturn(socketAddress);
        Mockito.when(socketChannel.write(byteBuffer)).thenReturn(randomNum);
        Mockito.when(socketChannel.setOption(Mockito.any(), Mockito.anyBoolean())).thenReturn(socketChannel);


        // Create SelectionKey
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

        // Depending on the test case we want to test what the server will do
        if (selectionKeyStatus == SelectionKeyStatus.INVALID) {
            // Override default SelectionKey return value
            Mockito.when(selectionKey.isValid()).thenReturn(Boolean.FALSE);
        } else if (selectionKeyStatus == SelectionKeyStatus.ACCEPTABLE) {
            // Override default SelectionKey return value
            Mockito.when(selectionKey.channel()).thenReturn(serverSocketChannel);
            Mockito.when(selectionKey.isAcceptable()).thenReturn(Boolean.TRUE);
            Mockito.when(selectionKey.isWritable()).thenReturn(Boolean.FALSE);
            Mockito.when(selectionKey.isReadable()).thenReturn(Boolean.FALSE);
        } else if (selectionKeyStatus == SelectionKeyStatus.WRITABLE) {
            Mockito.when(selectionKey.isAcceptable()).thenReturn(Boolean.FALSE);
            Mockito.when(selectionKey.isWritable()).thenReturn(Boolean.TRUE);
            Mockito.when(selectionKey.isReadable()).thenReturn(Boolean.FALSE);
        } else if (selectionKeyStatus == SelectionKeyStatus.READABLE) {
            Mockito.when(selectionKey.isAcceptable()).thenReturn(Boolean.FALSE);
            Mockito.when(selectionKey.isWritable()).thenReturn(Boolean.FALSE);
            Mockito.when(selectionKey.isReadable()).thenReturn(Boolean.TRUE);
        }


        serverSocket = Mockito.mock(ServerSocket.class);
        Mockito.when(serverSocketChannel.socket()).thenReturn(serverSocket);
        Mockito.doNothing().when(serverSocket).bind(Mockito.any(InetSocketAddress.class));

//        Mockito.when(serverSocketChannel.register(Mockito.any(Selector.class), Mockito.eq(SelectionKey.OP_ACCEPT))).thenReturn(selectionKey);

        // First register by ServerSocketChannel adds SelectionKey mock into keySet
        // This allows us to get a non zero size keySet such that keys.hasNext() is true
        Mockito.doAnswer(new Answer() {
            @Override
            public SelectionKey answer(InvocationOnMock invocationOnMock) throws Throwable {
                keySet.add(selectionKey);
                return selectionKey;
            }
        }).when(serverSocketChannel).register(Mockito.any(Selector.class), Mockito.eq(SelectionKey.OP_ACCEPT));

        // SUT only registers those remote clients that need to be resent so we are only keeping the SelectionKeys for these specific clients
        Mockito.doAnswer(new Answer() {
            @Override
            public SelectionKey answer(InvocationOnMock invocationOnMock) throws Throwable {
//                keySet.add(selectionKey);
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

        Mockito.when(selector.selectNow()).thenReturn(1);
        Mockito.when(selector.selectedKeys()).thenReturn(keySet);

//        Mockito.doNothing().when(packetUtils).sendPacketAndUpdateStatus(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());



        // Following is assuming we are using a spy on SelectorWrapper
//        Mockito.doReturn(clientsToResend).when(selector).selectNow();
//        Mockito.doReturn(keySet).when(selector).selectedKeys();

        // Create a handler thread for testing and spy it
        tunnelServerThread = Mockito.spy(new TunnelServer(localServerEndpoint, messageQueueToRemoteClients,
                packetsToResendQueue, remoteClients, resend, selector, serverSocketChannel, packetUtils));
    }

    @Test
    public void testSocketAcceptable() throws IOException, InterruptedException {
        setUpRemoteClients(SelectionKeyStatus.ACCEPTABLE);

        // Run the thread
        Assert.assertEquals(messageQueueToRemoteClients.size(), 1);
        tunnelServerThread.runnableTask();
        Assert.assertEquals(messageQueueToRemoteClients.size(), 1);

        // Assert 1 client has been added and check card is set to false by default
        Assert.assertEquals(remoteClients.size(), 1);
        Assert.assertFalse(remoteClients.containsValue(Boolean.TRUE));
    }

    @Test
    public void testSocketWritable() throws IOException, InterruptedException {
        setUpRemoteClients(SelectionKeyStatus.WRITABLE);

        // Run the thread
        Assert.assertEquals(messageQueueToRemoteClients.size(), 1);
        tunnelServerThread.runnableTask();
        Assert.assertEquals(messageQueueToRemoteClients.size(), 0);

        Mockito.verify(packetUtils, Mockito.times(1)).sendPacketAndUpdateStatus(remoteClientsCaptor.capture(),
                byteBufferCaptor.capture(), socketChannelCaptor.capture(), loggerCaptor.capture());

        // Check each time we attempted to send the same packet that needed to be sent
        List<ByteBuffer> byteBufferList = byteBufferCaptor.getAllValues();
        for (ByteBuffer byteBuffer : byteBufferList) {
            Assert.assertEquals(byteBuffer, this.byteBuffer);
        }
    }

}
