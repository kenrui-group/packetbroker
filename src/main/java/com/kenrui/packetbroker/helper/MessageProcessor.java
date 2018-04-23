package com.kenrui.packetbroker.helper;

import baseline.MessageHeaderDecoder;
import baseline.MessageHeaderEncoder;
import baseline.PacketDecoder;
import baseline.PacketEncoder;
import com.kenrui.packetbroker.structures.ConnectionInfo;
import com.kenrui.packetbroker.structures.DecodedMessages;
import com.kenrui.packetbroker.utilities.PacketUtils;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pcap4j.util.ByteArrays;

import java.nio.ByteBuffer;
import java.time.ZonedDateTime;

/**
 * Helper class to encode or decode messages.
 */
public class MessageProcessor {
    private static final MessageHeaderDecoder MESSAGE_HEADER_DECODER = new MessageHeaderDecoder();
    private static final MessageHeaderEncoder MESSAGE_HEADER_ENCODER = new MessageHeaderEncoder();
    private static final PacketEncoder PACKET_ENCODER = new PacketEncoder();
    private static final PacketDecoder PACKET_DECODER = new PacketDecoder();
    private static final Logger loggerTunnelClient = LogManager.getLogger("TunnelClient");
    private static final Logger loggerTunnelServer = LogManager.getLogger("TunnelServer");
    private static final Logger consoleLogger = LogManager.getLogger("console");


    /**
     * Encodes a message before sending over a tunnel.
     * @param localServerEndPoint Info of tunnel hop.
     * @param timeStamp Current time in UTC.
     * @param seqNum Sequence number of message being transmitted.  Starts at 1 and resets daily.
     * @param packet Payload to be encoded.
     * @return Encoded payload.
     */
    public static ByteBuffer encode(final ConnectionInfo localServerEndPoint,
                                    final ZonedDateTime timeStamp,
                                    final long seqNum, final byte[] packet) {

        int packetSize = packet.length;
        // todo: 140,222 bytes is based on a specific SBE encoding scheme's max size & 4 trailer based timestamps
        // todo: it'd be good to inject configurable parameters so we can do int packetSizeMax = [bytes of trailer based timestamp] * [max number of trailer based timestamps];
        // this is set to match value set for varDataEncoding's length + overhead on other info in tunnel-schema.xml
        // Max 140,222 bytes consisting of following:
        // header (8 bytes for overall header)
        // msglength (2 bytes)
        // header (2 bytes for packetBrokerHops group)
        // port (2 bytes)
        // seqnum (8 bytes)
        // timestamp (24 bytes for eg like '2018-01-13T09:57:06.707Z')
        // ip (10 bytes for eg '/127.0.0.1')
        // hostname (max at 65534 bytes)
        // description (max at 65534 bytes)
        // data (max at 9098 bytes)
        int packetSizeMax = 140222;
        loggerTunnelServer.debug("Encoding " + packetSize + " bytes: " + ByteArrays.toHexString(packet, " "));

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(packetSizeMax);
        UnsafeBuffer directBuffer = new UnsafeBuffer(byteBuffer);


        PACKET_ENCODER.wrapAndApplyHeader(directBuffer, 0, MESSAGE_HEADER_ENCODER)
                .packetBrokerHopsCount(1).next()
                .seqNum(seqNum)
                .timestamp(timeStamp.toString())
                .ip(localServerEndPoint.getIp().toString())
                .hostname(localServerEndPoint.getHostname())
                .description(localServerEndPoint.getDescription())
                .port(localServerEndPoint.getPort());

        PACKET_ENCODER.putPacket(packet, 0, packetSize);

        int msgLength = PACKET_ENCODER.encodedLength();
        PACKET_ENCODER.msgLength(msgLength);

        // Because directBuffer has been globally allocated to a set size (4096 bytes) and somehow the
        // packetEncoder isn't updating the limit marker to the actual amount encoded, we need to set
        // limit of directBuffer to the size of the actual data stored before returning.
        int totalEncodedSize = MessageHeaderEncoder.ENCODED_LENGTH + msgLength;
        directBuffer.byteBuffer().limit(totalEncodedSize);

        byte[] encodedByteArray = PacketUtils.byteBufferToArray(directBuffer.byteBuffer());
        loggerTunnelServer.debug("Encoded " + directBuffer.byteBuffer().limit() + " bytes " + ByteArrays.toHexString(encodedByteArray, " "));

        return directBuffer.byteBuffer();
    }

    /**
     * Decodes a or a number of messages after receiving from tunnel.
     * @param byteBufferReceive ByteBuffer read from SocketChannel.  May contain more than one message sent by server.
     * @return A DecodedMessages object storing all messages decoded, and any partial messages that needs to be appended with additional bytes from the next read.
     */
    public static DecodedMessages decode(ByteBuffer byteBufferReceive) {
        int bufferOffset = 0;
        UnsafeBuffer directBufferReceive = new UnsafeBuffer(byteBufferReceive);
        int totalBufferSize = byteBufferReceive.limit();
        int remainingBytesInBuffer = totalBufferSize;
        int msgHeaderBytes = MESSAGE_HEADER_DECODER.encodedLength();
        int msgLengthEncodingBytes = PACKET_DECODER.msgLengthEncodingLength();
        int msgMinBytesRequired = msgHeaderBytes + msgLengthEncodingBytes;
        byte[] packet = new byte[0];
        int offset = 0;
        Boolean storeFlag = Boolean.FALSE;
        DecodedMessages decodedMessages = new DecodedMessages();
        byte[] encodedByteArray = PacketUtils.byteBufferToArray(byteBufferReceive);
        int loopCount = 1;
        loggerTunnelClient.debug("Decoding " + totalBufferSize + " bytes " + ByteArrays.toHexString(encodedByteArray, " "));
        loggerTunnelClient.trace("remainingBytesInBuffer: " + remainingBytesInBuffer + " msgMinBytesRequired: " + msgMinBytesRequired);
        while (remainingBytesInBuffer >= msgMinBytesRequired) {
            loggerTunnelClient.debug("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
            loggerTunnelClient.debug("Loopcount: " + loopCount);
            loggerTunnelClient.trace("totalBufferSize: " + totalBufferSize + " bufferOffset: " + bufferOffset + " remainingBytesInBuffer: " + remainingBytesInBuffer);
            MESSAGE_HEADER_DECODER.wrap(directBufferReceive, bufferOffset);

            final int templateId = MESSAGE_HEADER_DECODER.templateId();
            if (templateId != PACKET_DECODER.TEMPLATE_ID) {
                String debugContent = "ERROR: offset: " + bufferOffset + "\n";
                byte[] inputByteBufferOffset = new byte[remainingBytesInBuffer];
                byte[] inputByteBuffer = new byte[totalBufferSize];
                int byteBufferReceivePos = byteBufferReceive.position();
                byteBufferReceive.get(inputByteBufferOffset, bufferOffset, remainingBytesInBuffer);
                byteBufferReceive.position(byteBufferReceivePos);
                byteBufferReceive.get(inputByteBuffer);
                debugContent = debugContent + "ERROR: Current Byte Buffer Begins: " + ByteArrays.toHexString(inputByteBufferOffset, " ") + "\n";
                debugContent = debugContent + "ERROR: Whole Byte Buffer: " + ByteArrays.toHexString(inputByteBuffer, " ") + "\n";
                consoleLogger.fatal(debugContent);
                loggerTunnelClient.fatal(debugContent);
                throw new IllegalStateException("Template ids do not match");
            }

            final int actingBlockLength = MESSAGE_HEADER_DECODER.blockLength();
            final int actingVersion = MESSAGE_HEADER_DECODER.version();

            bufferOffset += MESSAGE_HEADER_DECODER.encodedLength();


            PACKET_DECODER.wrap(directBufferReceive, bufferOffset, actingBlockLength, actingVersion);
            int currentMsgLengthBytes = PACKET_DECODER.msgLength();
            int currentMsgTotalBytes = msgHeaderBytes + currentMsgLengthBytes;

            loggerTunnelClient.trace("remainingBytesInBuffer: " + remainingBytesInBuffer + " currentMsgTotalBytes: " + currentMsgTotalBytes + " currentMsgLengthBytes: " + currentMsgLengthBytes);
            if (remainingBytesInBuffer >= currentMsgTotalBytes) {
                remainingBytesInBuffer = remainingBytesInBuffer - currentMsgTotalBytes;
                loggerTunnelClient.debug("Message Length " + currentMsgLengthBytes + " bytes after header");

                int hopCount = 1;
                for (final PacketDecoder.PacketBrokerHopsDecoder packetBrokerHops : PACKET_DECODER.packetBrokerHops()) {
                    loggerTunnelClient.debug("Hop " + hopCount);
                    loggerTunnelClient.debug("\tPort: " + packetBrokerHops.port());
                    loggerTunnelClient.debug("\tSeqNum: " + packetBrokerHops.seqNum());
                    loggerTunnelClient.debug("\tTimestamp: " + packetBrokerHops.timestamp());
                    loggerTunnelClient.debug("\tIp: " + packetBrokerHops.ip());
                    loggerTunnelClient.debug("\tHostname: " + packetBrokerHops.hostname());
                    loggerTunnelClient.debug("\tDescription: " + packetBrokerHops.description());
                    hopCount++;
                }

                int packetLength = PACKET_DECODER.packetLength();
                packet = new byte[packetLength];
                try {
                    PACKET_DECODER.getPacket(packet, 0, packetLength);
                    loggerTunnelClient.debug("Decoded " + packetLength + " bytes: " + ByteArrays.toHexString(packet, " "));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // push packet into packetArray
                loggerTunnelClient.trace("Adding packet to decodedMessages object.");
                decodedMessages.addDecodedMsg(packet);

                bufferOffset = totalBufferSize - remainingBytesInBuffer;
                loggerTunnelClient.trace("Updated bufferOffset to " + bufferOffset);
            } else {
                // store msg if we can only read header but not whole msg
                try {
                    // Need to adjust offset because we updated it to read the message length past the header earlier
                    int adjustedBufferOffset = bufferOffset - MESSAGE_HEADER_DECODER.encodedLength();
                    loggerTunnelClient.trace("remainingBytesInBuffer >= currentMsgTotalBytes returns false.  Extracting " + remainingBytesInBuffer + " starting from offset " + adjustedBufferOffset + " in byteBufferReceive to leftOverPartialMsg.");
                    byte[] leftOverPartialMsg = new byte[remainingBytesInBuffer];
                    loggerTunnelClient.trace("Created leftOverPartialMsg of length " + leftOverPartialMsg.length);
                    loggerTunnelClient.trace("Copying byteBufferReceive from offset " + adjustedBufferOffset + " of length " + remainingBytesInBuffer + " into leftOverPartialMsg");
                    loggerTunnelClient.trace("byteBufferReceive pos: " + byteBufferReceive.position() + " limit: " + byteBufferReceive.limit() + " capacity: " + byteBufferReceive.capacity());


//                    Following get always get indexoutofboundexception for no good reason so is replaced with a for loop
//                    byteBufferReceive.get(leftOverPartialMsg, adjustedBufferOffset, remainingBytesInBuffer);

                    for (int i=adjustedBufferOffset, j=0; i<(adjustedBufferOffset+remainingBytesInBuffer); i++, j++) {
                        leftOverPartialMsg[j] = byteBufferReceive.get(i);
                    }

                    loggerTunnelClient.trace("Copied byteBufferReceive from offset " + adjustedBufferOffset + " of length " + remainingBytesInBuffer + " into leftOverPartialMsg");
                    decodedMessages.setLeftOverPartialMsg(leftOverPartialMsg);
                    loggerTunnelClient.trace("Added leftOverPartialMsg into decodedMessages");
                    loggerTunnelClient.trace("returning decodedMessages");
                    return decodedMessages;
                } catch (Exception e) {
                    e.printStackTrace();
                    loggerTunnelClient.fatal(e.getMessage().toString());
                    loggerTunnelClient.fatal(e.getLocalizedMessage().toString());
                    loggerTunnelClient.fatal(e.getCause().toString());
                }
            }

            loopCount++;
            loggerTunnelClient.trace("Updated loopCount to " + loopCount);
        }

        // store msg if we can't even read the header
        if (remainingBytesInBuffer > 0) {
            try {
                bufferOffset = totalBufferSize - remainingBytesInBuffer;
                byte[] leftOverPartialMsg = new byte[remainingBytesInBuffer];

                for (int i = bufferOffset, j = 0; i < totalBufferSize && j < remainingBytesInBuffer; i++, j++) {
                    leftOverPartialMsg[j] = byteBufferReceive.get(i);
                }
                decodedMessages.setLeftOverPartialMsg(leftOverPartialMsg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        // change to returning packetArray
        return decodedMessages;
    }
}
