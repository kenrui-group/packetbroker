package com.kenrui.packetbroker.clientserver;

import com.kenrui.packetbroker.helper.MessageProcessor;
import com.kenrui.packetbroker.helper.QueuePackets;
import com.kenrui.packetbroker.structures.ConnectionInfo;
import com.kenrui.packetbroker.structures.DecodedMessages;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pcap4j.util.ByteArrays;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;

/**
 * Tunnel client thread for receiving packets captured remotely.
 * Received packets may be dumped locally for packet capture / decoded appliance or forwarded on to further clients.
 */
public class TunnelClient implements Runnable {
    private List<ConnectionInfo> remoteServers;
    private SocketChannel socketChannel = null;
    private Selector selector;
    private ByteBuffer byteBuffer;
    private byte[] leftOverBytes;
    private QueuePackets queuePackets;
    private Boolean dumpLocal, forwardRemoteCapture;
    private static final Logger logger = LogManager.getLogger("TunnelClient");

    /**
     * Creates tunnel client thread
     * @param remoteServers List of remote servers to connect to.
     * @param queuePackets Helper class to put packets on queues for either local dump or sending to remote clients.
     * @param dumpLocal Determine if packets captured remotely will be dumped to local interface.
     * @param forwardRemoteCapture Determine if we need to forward packets received from remote servers to remote clients.
     * @throws IOException
     */
    public TunnelClient(List<ConnectionInfo> remoteServers,
                        QueuePackets queuePackets, Boolean dumpLocal,
                        Boolean forwardRemoteCapture) throws IOException {
        selector = Selector.open();
        byteBuffer = ByteBuffer.allocate(9022);
        this.remoteServers = remoteServers;
        this.queuePackets = queuePackets;
        this.dumpLocal = dumpLocal;
        this.forwardRemoteCapture = forwardRemoteCapture;

        synchronized (this.remoteServers) {
            try {
                int i = 1;
                for (ConnectionInfo connection : this.remoteServers) {
                    socketChannel = SocketChannel.open();
                    // todo:  This require enhancement to handle remote servers not ready and have to try again.
                    logger.info("Connecting to " + connection.getIp() + ":" + connection.getPort());
                    socketChannel.connect(new InetSocketAddress(connection.getIp(), connection.getPort()));
                    socketChannel.configureBlocking(false);
                    socketChannel.register(selector, SelectionKey.OP_READ);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                selector.selectNow();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isWritable()) {
                        logger.trace("Client is writable");
                    }

                    if (key.isConnectable()) {
                        logger.trace("Client is connectable");
                    }

                    if (key.isAcceptable()) {
                        logger.trace("Client is acceptable");
                    }

                    if (key.isReadable()) {
                        logger.trace("Client is readable");
                        SocketChannel channel = (SocketChannel) key.channel();
                        byteBuffer.clear();
                        int bytesRead = channel.read(byteBuffer);
                        byteBuffer.flip();
                        logger.trace("Received " + bytesRead + " bytes from " + ((SocketChannel) key.channel()).getRemoteAddress());
                        byte[] bytes = new byte[byteBuffer.limit()];
                        int pos = byteBuffer.position();
                        try {
                            byteBuffer.get(bytes);

                            byteBuffer.position(pos);
                            logger.trace("Received " + ByteArrays.toHexString(bytes, " "));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        // Decode message
                        try {
                            int leftOverBytesSize = 0;
                            DecodedMessages decodedMessages;
                            if (leftOverBytes != null) {
                                leftOverBytesSize = leftOverBytes.length;

                                // Add previously left over bytes to latest read
                                int latestByteBufferSize = byteBuffer.limit();
                                int totalByteBufferSize = leftOverBytesSize + latestByteBufferSize;
                                logger.trace("leftOverBytesSize: " + leftOverBytesSize + " latestByteBufferSize: " + latestByteBufferSize);
                                logger.trace("Creating new ByteBuffer with size " + totalByteBufferSize);
                                ByteBuffer newByteBuffer = ByteBuffer.allocate(totalByteBufferSize)
                                        .put(leftOverBytes)
                                        .put(byteBuffer);
                                newByteBuffer.flip();
                                logger.trace("newByteBuffer position: " + newByteBuffer.position() + " limit: " + newByteBuffer.limit() + " capacity: " + newByteBuffer.capacity());
                                decodedMessages = MessageProcessor.decode(newByteBuffer);
                            } else {
                                // No previous left over bytes
                                decodedMessages = MessageProcessor.decode(byteBuffer);
                            }

                            leftOverBytes = decodedMessages.getLeftOverPartialMsg();

                            for (byte[] packet : decodedMessages.getDecodedMsgList()) {
                                if (dumpLocal == Boolean.TRUE) {
                                    queuePackets.PutOnLocalQueue(packet);
                                }

                                // packet forwarding mode enabled
                                if (forwardRemoteCapture == Boolean.TRUE) {
                                    queuePackets.PutOnRemoteQueue(packet);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        logger.debug("============================================================================");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}