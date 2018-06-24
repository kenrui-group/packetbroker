package com.kenrui.packetbroker.clientserver;

import com.kenrui.packetbroker.structures.ConnectionInfo;
import com.kenrui.packetbroker.structures.PacketToResend;
import com.kenrui.packetbroker.utilities.PacketUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tunnel server thread for sending packets to remote clients.
 * Packets may have been captured locally or received from remote servers.
 */
@Component
public class TunnelServer implements Runnable {
    //    @Autowired
//    public PacketUtils packetUtils;
    private PacketUtils packetUtils;
    private BlockingQueue messageQueueToRemoteClients;
    private BlockingQueue packetsToResendQueue;
    private int port;
    private InetAddress listeningInterfaceIp;
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;
    private ConcurrentHashMap<SocketChannel, Boolean> remoteClientsSentCheck, remoteClients;
    private Boolean resend;
    private Boolean firstWritableChannel = Boolean.TRUE;
    private ByteBuffer byteBuffer = null;
    private static final Logger logger = LogManager.getLogger("TunnelServer");

    /**
     * Creates tunnel server thread.
     *
     * @param localServerEndpoint         Object configured with IP and port info for TunnelServer to bind to listening for remote clients connection.
     * @param messageQueueToRemoteClients Queue with packets destined to be sent to remote clients.
     * @param packetsToResendQueue        Queue for packets that need to be retried again due to slower consumers / dead clients / poor network.
     * @param remoteClients               Map of remote clients.  This object is passed in with no content for this server to populate for each remote client connecting in.
     * @param resend                      Determine if we have configured an interface to resend packets that were not successful when first captured due to slow consumer / dead client / bad connection.
     * @param selector                    NIO Selector object.  This is being passed in so we can control its behavior when doing Unit Test.
     * @param serverSocketChannel         ServerSocketChannel object.  This is being passed in so we can control its behavior when doing Unit Test.
     * @param packetUtils                 PacketUtils is a utility class used for doing a number of things and we use it here for sending messages.
     * @throws IOException
     */
    public TunnelServer(ConnectionInfo localServerEndpoint, BlockingQueue messageQueueToRemoteClients,
                        BlockingQueue packetsToResendQueue,
                        ConcurrentHashMap<SocketChannel, Boolean> remoteClients,
                        Boolean resend, Selector selector, ServerSocketChannel serverSocketChannel, PacketUtils packetUtils) throws IOException {
        this.port = localServerEndpoint.getPort();
        this.listeningInterfaceIp = localServerEndpoint.getIp();
        this.messageQueueToRemoteClients = messageQueueToRemoteClients;
        this.packetsToResendQueue = packetsToResendQueue;
        this.remoteClients = remoteClients;
        this.resend = resend;
        this.selector = selector;
        this.serverSocketChannel = serverSocketChannel;
        this.packetUtils = packetUtils;

//        selector = Selector.open();
//        serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.configureBlocking(false);
        this.serverSocketChannel.socket().bind(new InetSocketAddress(listeningInterfaceIp, port));
        this.serverSocketChannel.register(this.selector, SelectionKey.OP_ACCEPT);
    }


    @Override
    public void run() {
        try {
            logger.info(Thread.currentThread().getName() + " listening on " + serverSocketChannel.getLocalAddress());

            while (!Thread.currentThread().isInterrupted()) {
                runnableTask();
            }
        } catch (IOException e) {
            logger.error(e);
        }
    }

    protected void runnableTask() {
        try {
            selector.selectNow();
        } catch (IOException e) {
            logger.error(e);
        }
        Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

        while (keys.hasNext()) {
            SelectionKey key = keys.next();
            if (!key.isValid()) {
                continue;
            }

            if (key.isAcceptable()) {
                ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                try {
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    if (socketChannel != null) {
                        // Do following only if a remote client has connected in
                        socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                        socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                        socketChannel.configureBlocking(false);
                        socketChannel.register(selector, SelectionKey.OP_WRITE);
                        logger.info("Accepted connection from " + socketChannel.getRemoteAddress().toString() + " on port " + this.port);
                        remoteClients.put(socketChannel, Boolean.FALSE);
                    }
                } catch (IOException e) {
                    logger.error(e);
                }

            }

            if (key.isWritable()) {
                SocketChannel channel = (SocketChannel) key.channel();

                if (firstWritableChannel == Boolean.TRUE) {
                    /**
                     * We will do two things only once upon the first writable remote client
                     * 1) Get a packet to be sent.  The same packet will be sent to the rest of the
                     * writable remote clients.
                     * 2) Get a new check card so we can keep check of which remote clients we have
                     * sent the packet to.
                     */
                    try {
                        // Get ByteBuffer representation of packet to be sent
                        byteBuffer = (ByteBuffer) messageQueueToRemoteClients.take();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    /**
                     * Create a new check card for this packet to be sent to remote clients
                     * New check card contains all current remote client connections default to not
                     * having been sent
                     */
                    remoteClientsSentCheck = new ConcurrentHashMap<>();
                    remoteClientsSentCheck.putAll(remoteClients);

                    /**
                     * Set flag so we will not get another packet and check card again on this
                     * round of SelectionKeys
                     */
                    firstWritableChannel = Boolean.FALSE;
                }

                // Try to send to remote client
                packetUtils.sendPacketAndUpdateStatus(remoteClientsSentCheck, byteBuffer, channel, logger);
            }


            if (key.isReadable()) {
                SocketChannel channel = (SocketChannel) key.channel();
                ByteBuffer[] readBuffer = new ByteBuffer[1000];
                try {
                    // todo: Nothing is done on packets received.  Need to do see if any requirements to process messages received here.
                    channel.read(readBuffer);
                } catch (AsynchronousCloseException e) {
                    remoteClients.remove(channel);
                    logger.error(e);
                } catch (ClosedChannelException e) {
                    remoteClients.remove(channel);
                    logger.error(e);
                } catch (IOException e) {
                    logger.error(e);
                }

            }
        }


        // Reset flag so we will be able to get a new packet and a new check card again
        firstWritableChannel = Boolean.TRUE;

        /**
         * If a check card is created and populated that means we have attempted to send a packet to remote clients.
         * If any remote clients have not been sent the packet we need to create an object storing the
         * packet and the check card containing the list of clients that the packet has been sent to or not.
         * This object is then added to a queue to be resent by another thread at a later time.
         * Using a separate thread helps to deal with slow consumers / bad connections / dead clients.
         */
        if (remoteClientsSentCheck != null && remoteClientsSentCheck.containsValue(Boolean.FALSE)
                && resend == Boolean.TRUE) {
            PacketToResend packetToResend = new PacketToResend(byteBuffer, remoteClientsSentCheck);
            packetsToResendQueue.add(packetToResend);
        }
    }
}
