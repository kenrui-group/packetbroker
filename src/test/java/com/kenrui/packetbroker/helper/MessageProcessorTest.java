package com.kenrui.packetbroker.helper;

import baseline.MessageHeaderDecoder;
import baseline.PacketDecoder;
import com.kenrui.packetbroker.structures.ConnectionInfo;
import com.kenrui.packetbroker.structures.DecodedMessages;
import com.kenrui.packetbroker.utilities.PacketUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static com.kenrui.packetbroker.helper.MessageProcessor.decode;
import static com.kenrui.packetbroker.helper.MessageProcessor.encode;

public class MessageProcessorTest {
    private ZonedDateTime timeStamp = null;
    private int packetSizeLowerLimit = 1;
    // todo:  need to match value set for varDataEncoding's length in tunnel-schema.xml
    private int packetSizeUpperLimit = 9098;
    private long seqNum;
    private int testRuns;
    private static final MessageHeaderDecoder MESSAGE_HEADER_DECODER = new MessageHeaderDecoder();
    private static final PacketDecoder PACKET_DECODER = new PacketDecoder();
    private PacketUtils packetUtils = new PacketUtils();
    private Config defaultConfig = ConfigFactory.parseResources("defaults.conf");
    private ConnectionInfo localServerEndpoint = new ConnectionInfo(defaultConfig.getString("localServer.ip"),
            defaultConfig.getInt("localServer.port"),
            defaultConfig.getString("localServer.hostname"),
            defaultConfig.getString("localServer.description"));

    @BeforeMethod
    public void setUp() throws Exception {
    }

    @AfterMethod
    public void tearDown() throws Exception {
    }

    @DataProvider(name = "MultipleMessages")
    public Object[][] createMultipleMessages() {
        testRuns = 5;
        Object[][] objectToReturn = new Object[testRuns][];

        for (int run = 1; run <= testRuns; run++) {
            timeStamp = ZonedDateTime.now(ZoneOffset.UTC);
            byte[] randomPacket = packetUtils.getRandomPacket(packetSizeLowerLimit, packetSizeUpperLimit);
            seqNum = run;

            objectToReturn[run - 1] = new Object[]{
                    localServerEndpoint, timeStamp, seqNum, randomPacket
            };
        }

        return objectToReturn;
    }

    @Test(dataProvider = "MultipleMessages")
    public void testEncodeDecodeSingleMessage(final ConnectionInfo localServerEndPoint,
                                              final ZonedDateTime timeStamp,
                                              final long seqNum, final byte[] packet) throws Exception {
        DecodedMessages decodedMessages = decode(encode(localServerEndPoint, timeStamp, seqNum, packet));
        List<byte[]> decodedMessagesList = decodedMessages.getDecodedMsgList();

        // validate only one message is present
        Assert.assertEquals((long) 1, (long) decodedMessagesList.size());

        // validate no partial message is left over
        Assert.assertNull(decodedMessages.getLeftOverPartialMsg());

        // Validate content of message decoded is identical to message encoded
        // This allows us to test both encode and decode are working correctly
        // If we check the encoded output we need to provide a byte stream that is specific to a SBE schema
        // This also allows us to handle the case of the SBE schema changing
        Assert.assertArrayEquals(packet, decodedMessagesList.get(0));
    }

    // This test decoding messages with incorrect SBE template version
    @Test(dataProvider = "MultipleMessages", expectedExceptions = IllegalStateException.class)
    public void testEncodeDecodeSingleMessageWithIncorrectTemplateVersion(final ConnectionInfo localServerEndPoint,
                                                                          final ZonedDateTime timeStamp,
                                                                          final long seqNum, final byte[] packet) throws Exception {
        ByteBuffer byteBuffer = encode(localServerEndPoint, timeStamp, seqNum, packet);
        byteBuffer.put(2, (byte) (byteBuffer.get(2) + 1));

        DecodedMessages decodedMessages = decode(byteBuffer);
    }

    // This simulates the scenario during a read multiple whole encoded messages are read off from the socket
    // We could have used a DataProvider but because it's going to be calling the method multiple times,
    // once per each message, we'd need to store the message on a global variable to build up the message.
    // It'll not be as self-contained as the way we are doing it here.
    @Test
    public void testDecodeMultipleMessagesPerRead() throws Exception {
        int messagesToCreate = 5;
        int totalEncodedSize = 0;
        int bytesCopiedTotal = 0;
        ZonedDateTime timeStamp;
        long seqNum;
        List<byte[]> bytesPreEncodedArrayList = new ArrayList<>();
        List<ByteBuffer> byteBufferEncodedArrayList = new ArrayList<>();

        for (int messageNumber = 1; messageNumber <= messagesToCreate; messageNumber++) {
            timeStamp = ZonedDateTime.now(ZoneOffset.UTC);
            int randomNum = ThreadLocalRandom.current().nextInt(packetSizeLowerLimit, packetSizeUpperLimit);
            byte[] bytes = new byte[randomNum];
            new Random().nextBytes(bytes);
            seqNum = messageNumber;

            ByteBuffer byteBuffer = encode(localServerEndpoint, timeStamp, seqNum, bytes);
            totalEncodedSize = totalEncodedSize + byteBuffer.limit();
            bytesPreEncodedArrayList.add(bytes);

            byteBufferEncodedArrayList.add(byteBuffer);
        }

        int sizeToAllocate = totalEncodedSize;
        ByteBuffer byteBufferMultipleMessages = ByteBuffer.allocate(sizeToAllocate);
        for (ByteBuffer byteBuffer : byteBufferEncodedArrayList) {
            for (int bytesCopiedCurrentMessage = 0; bytesCopiedCurrentMessage < byteBuffer.limit(); bytesCopiedCurrentMessage++) {
                byteBufferMultipleMessages.put(bytesCopiedTotal, byteBuffer.get(bytesCopiedCurrentMessage));
                bytesCopiedTotal++;
            }
        }

        DecodedMessages decodedMessages = decode(byteBufferMultipleMessages);
        List<byte[]> decodedMessagesList = decodedMessages.getDecodedMsgList();

        // validate number of messages decoded is same as number of messages encoded
        Assert.assertEquals((long) bytesPreEncodedArrayList.size(), (long) decodedMessagesList.size());

        // validate no partial message is left over
        Assert.assertNull(decodedMessages.getLeftOverPartialMsg());

        // Validate content of messages decoded is identical to messages encoded
        int decodedMessagesIndex = 0;
        for (byte[] byteArray : bytesPreEncodedArrayList) {
            Assert.assertArrayEquals(byteArray, decodedMessagesList.get(decodedMessagesIndex));
            decodedMessagesIndex++;
        }
    }

    @Test
    public void testEncodeDecodeSingleMessagePlusLessThanHeader() throws Exception {
        int messagesToCreate = 2;
        int totalEncodedSize = 0;
        int bytesCopiedTotal = 0;
        ZonedDateTime timeStamp;
        long seqNum;
        List<byte[]> bytesPreEncodedArrayList = new ArrayList<>();
        List<ByteBuffer> byteBufferEncodedArrayList = new ArrayList<>();
        int msgHeaderBytes = MESSAGE_HEADER_DECODER.encodedLength();
        int msgLengthEncodingBytes = PACKET_DECODER.msgLengthEncodingLength();
        int msgMinBytesRequired = msgHeaderBytes + msgLengthEncodingBytes;

        for (int messageNumber = 1; messageNumber <= messagesToCreate; messageNumber++) {
            timeStamp = ZonedDateTime.now(ZoneOffset.UTC);
            int randomNum = ThreadLocalRandom.current().nextInt(packetSizeLowerLimit, packetSizeUpperLimit);
            byte[] bytes = new byte[randomNum];
            new Random().nextBytes(bytes);
            seqNum = messageNumber;

            ByteBuffer byteBuffer = encode(localServerEndpoint, timeStamp, seqNum, bytes);
            totalEncodedSize = totalEncodedSize + byteBuffer.limit();
            bytesPreEncodedArrayList.add(bytes);

            byteBufferEncodedArrayList.add(byteBuffer);
        }

        int randomPartialHeaderSize = ThreadLocalRandom.current().nextInt(1, msgMinBytesRequired);
        int sizeToAllocate = byteBufferEncodedArrayList.get(0).limit() + randomPartialHeaderSize;
        ByteBuffer byteBufferMultipleMessages = ByteBuffer.allocate(sizeToAllocate);

        for (int bytesCopiedCurrentMessage = 0; bytesCopiedCurrentMessage < byteBufferEncodedArrayList.get(0).limit(); bytesCopiedCurrentMessage++) {
            byteBufferMultipleMessages.put(bytesCopiedTotal, byteBufferEncodedArrayList.get(0).get(bytesCopiedCurrentMessage));
            bytesCopiedTotal++;
        }

        for (int bytesCopiedCurrentMessage = 0; bytesCopiedCurrentMessage < randomPartialHeaderSize; bytesCopiedCurrentMessage++) {
            byteBufferMultipleMessages.put(bytesCopiedTotal, byteBufferEncodedArrayList.get(1).get(bytesCopiedCurrentMessage));
            bytesCopiedTotal++;
        }


        DecodedMessages decodedMessages = decode(byteBufferMultipleMessages);
        List<byte[]> decodedMessagesList = decodedMessages.getDecodedMsgList();

        // validate only one message was decoded
        Assert.assertEquals((long) 1, (long) decodedMessagesList.size());

        // validate there is partial message left over
        Assert.assertNotNull(decodedMessages.getLeftOverPartialMsg());

        // Validate content of message decoded is identical to messages encoded
        Assert.assertArrayEquals(bytesPreEncodedArrayList.get(0), decodedMessagesList.get(0));

        // Validate partial header read is stored to be joined up with packets from next socket read
        byte[] partialHeader = new byte[randomPartialHeaderSize];
        byteBufferEncodedArrayList.get(1).get(partialHeader, 0, randomPartialHeaderSize);
        Assert.assertArrayEquals(partialHeader, decodedMessages.getLeftOverPartialMsg());
    }

    @Test
    public void testEncodeDecodeSingleMessagePlusEqualToHeader() throws Exception {
        int messagesToCreate = 2;
        int totalEncodedSize = 0;
        int bytesCopiedTotal = 0;
        ZonedDateTime timeStamp;
        long seqNum;
        List<byte[]> bytesPreEncodedArrayList = new ArrayList<>();
        List<ByteBuffer> byteBufferEncodedArrayList = new ArrayList<>();
        int msgHeaderBytes = MESSAGE_HEADER_DECODER.encodedLength();
        int msgLengthEncodingBytes = PACKET_DECODER.msgLengthEncodingLength();
        int msgMinBytesRequired = msgHeaderBytes + msgLengthEncodingBytes;

        for (int messageNumber = 1; messageNumber <= messagesToCreate; messageNumber++) {
            timeStamp = ZonedDateTime.now(ZoneOffset.UTC);
            int randomNum = ThreadLocalRandom.current().nextInt(packetSizeLowerLimit, packetSizeUpperLimit);
            byte[] bytes = new byte[randomNum];
            new Random().nextBytes(bytes);
            seqNum = messageNumber;

            ByteBuffer byteBuffer = encode(localServerEndpoint, timeStamp, seqNum, bytes);
            totalEncodedSize = totalEncodedSize + byteBuffer.limit();
            bytesPreEncodedArrayList.add(bytes);

            byteBufferEncodedArrayList.add(byteBuffer);
        }

        int sizeToAllocate = byteBufferEncodedArrayList.get(0).limit() + msgMinBytesRequired;
        ByteBuffer byteBufferMultipleMessages = ByteBuffer.allocate(sizeToAllocate);

        for (int bytesCopiedCurrentMessage = 0; bytesCopiedCurrentMessage < byteBufferEncodedArrayList.get(0).limit(); bytesCopiedCurrentMessage++) {
            byteBufferMultipleMessages.put(bytesCopiedTotal, byteBufferEncodedArrayList.get(0).get(bytesCopiedCurrentMessage));
            bytesCopiedTotal++;
        }

        for (int bytesCopiedCurrentMessage = 0; bytesCopiedCurrentMessage < msgMinBytesRequired; bytesCopiedCurrentMessage++) {
            byteBufferMultipleMessages.put(bytesCopiedTotal, byteBufferEncodedArrayList.get(1).get(bytesCopiedCurrentMessage));
            bytesCopiedTotal++;
        }


        DecodedMessages decodedMessages = decode(byteBufferMultipleMessages);
        List<byte[]> decodedMessagesList = decodedMessages.getDecodedMsgList();

        // validate only one message was decoded
        Assert.assertEquals((long) 1, (long) decodedMessagesList.size());

        // validate there is partial message left over
        Assert.assertNotNull(decodedMessages.getLeftOverPartialMsg());

        // Validate content of message decoded is identical to messages encoded
        Assert.assertArrayEquals(bytesPreEncodedArrayList.get(0), decodedMessagesList.get(0));

        // Validate partial header read is stored to be joined up with packets from next socket read
        byte[] fullHeader = new byte[msgMinBytesRequired];
        byteBufferEncodedArrayList.get(1).get(fullHeader, 0, msgMinBytesRequired);
        Assert.assertArrayEquals(fullHeader, decodedMessages.getLeftOverPartialMsg());
    }

    @Test
    public void testEncodeDecodeSingleMessagePlusGreaterThanHeaderButLessThanWholeMessage() throws Exception {
        int messagesToCreate = 2;
        int totalEncodedSize = 0;
        int bytesCopiedTotal = 0;
        ZonedDateTime timeStamp;
        long seqNum;
        List<byte[]> bytesPreEncodedArrayList = new ArrayList<>();
        List<ByteBuffer> byteBufferEncodedArrayList = new ArrayList<>();
        int msgHeaderBytes = MESSAGE_HEADER_DECODER.encodedLength();
        int msgLengthEncodingBytes = PACKET_DECODER.msgLengthEncodingLength();
        int msgMinBytesRequired = msgHeaderBytes + msgLengthEncodingBytes;

        for (int messageNumber = 1; messageNumber <= messagesToCreate; messageNumber++) {
            timeStamp = ZonedDateTime.now(ZoneOffset.UTC);
            int randomNum = ThreadLocalRandom.current().nextInt(packetSizeLowerLimit, packetSizeUpperLimit);
            byte[] bytes = new byte[randomNum];
            new Random().nextBytes(bytes);
            seqNum = messageNumber;

            ByteBuffer byteBuffer = encode(localServerEndpoint, timeStamp, seqNum, bytes);
            totalEncodedSize = totalEncodedSize + byteBuffer.limit();
            bytesPreEncodedArrayList.add(bytes);

            byteBufferEncodedArrayList.add(byteBuffer);
        }

        int randomPartialMessageSize = ThreadLocalRandom.current().nextInt(msgMinBytesRequired, byteBufferEncodedArrayList.get(1).limit());
        int sizeToAllocate = byteBufferEncodedArrayList.get(0).limit() + randomPartialMessageSize;
        ByteBuffer byteBufferMultipleMessages = ByteBuffer.allocate(sizeToAllocate);

        for (int bytesCopiedCurrentMessage = 0; bytesCopiedCurrentMessage < byteBufferEncodedArrayList.get(0).limit(); bytesCopiedCurrentMessage++) {
            byteBufferMultipleMessages.put(bytesCopiedTotal, byteBufferEncodedArrayList.get(0).get(bytesCopiedCurrentMessage));
            bytesCopiedTotal++;
        }

        for (int bytesCopiedCurrentMessage = 0; bytesCopiedCurrentMessage < randomPartialMessageSize; bytesCopiedCurrentMessage++) {
            byteBufferMultipleMessages.put(bytesCopiedTotal, byteBufferEncodedArrayList.get(1).get(bytesCopiedCurrentMessage));
            bytesCopiedTotal++;
        }


        DecodedMessages decodedMessages = decode(byteBufferMultipleMessages);
        List<byte[]> decodedMessagesList = decodedMessages.getDecodedMsgList();

        // validate only one message was decoded
        Assert.assertEquals((long) 1, (long) decodedMessagesList.size());

        // validate there is partial message left over
        Assert.assertNotNull(decodedMessages.getLeftOverPartialMsg());

        // Validate content of message decoded is identical to messages encoded
        Assert.assertArrayEquals(bytesPreEncodedArrayList.get(0), decodedMessagesList.get(0));

        // Validate partial message read is stored to be joined up with packets from next socket read
        byte[] partialMessage = new byte[randomPartialMessageSize];
        byteBufferEncodedArrayList.get(1).get(partialMessage, 0, randomPartialMessageSize);
        Assert.assertArrayEquals(partialMessage, decodedMessages.getLeftOverPartialMsg());
    }


    @DataProvider(name = "DataToBeEncodedPacketSizeTooLarge")
    public Object[][] createDataToBeEncodedPacketSizeTooLarge() {
        Object[][] objectToReturn = new Object[1][];

        timeStamp = ZonedDateTime.now(ZoneOffset.UTC);
        byte[] bytes = new byte[packetSizeUpperLimit + 1];
        new Random().nextBytes(bytes);

        objectToReturn[0] = new Object[]{
                localServerEndpoint, timeStamp, (long) 1, bytes
        };

        return objectToReturn;
    }

    // SBE will issue IllegalStateException rather than IndexOutOfBoundsException
    @Test(dataProvider = "DataToBeEncodedPacketSizeTooLarge", expectedExceptions = IllegalStateException.class)
    public void testEncodePacketSizeTooLarge(final ConnectionInfo localServerEndPoint,
                                             final ZonedDateTime timeStamp,
                                             final long seqNum, final byte[] packet) throws Exception {
        encode(localServerEndPoint, timeStamp, seqNum, packet);
    }

    @DataProvider(name = "DataToBeEncodedMaxPacketSize")
    public Object[][] createDataToBeEncodedMaxPacketSize() {
        Object[][] objectToReturn = new Object[1][];

        timeStamp = ZonedDateTime.now(ZoneOffset.UTC);
        byte[] bytes = new byte[packetSizeUpperLimit];
        new Random().nextBytes(bytes);

        objectToReturn[0] = new Object[]{
                localServerEndpoint, timeStamp, (long) 1, bytes
        };

        return objectToReturn;
    }

    @Test(dataProvider = "DataToBeEncodedMaxPacketSize")
    public void testEncodeMaxPacketSize(final ConnectionInfo localServerEndPoint,
                                             final ZonedDateTime timeStamp,
                                             final long seqNum, final byte[] packet) throws Exception {
        encode(localServerEndPoint, timeStamp, seqNum, packet);
    }
}