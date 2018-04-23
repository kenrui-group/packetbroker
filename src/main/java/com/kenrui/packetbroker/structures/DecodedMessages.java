package com.kenrui.packetbroker.structures;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a list of messages that has been decoded on the client end of a tunnel.
 * Each read from SocketChannel may contain a number of encoded messages sent by a server.
 */
public class DecodedMessages {
    private List<byte[]> decodedMsgList = new ArrayList<>();
    private byte[] leftOverPartialMsg;

    public DecodedMessages() {
    }

    public List<byte[]> getDecodedMsgList() {
        return decodedMsgList;
    }

    public void addDecodedMsg(byte[] decodedMsg) {
        decodedMsgList.add(decodedMsg);
    }

    public byte[] getLeftOverPartialMsg() {
        return leftOverPartialMsg;
    }

    /**
     * At the end of the ByteBuffer returned from a SocketChannel read may contain a partial message.
     * This message needs to be stored somewhere with the remaining bytes appended to on a subsequent
     * read from SocketChannel.
     * @param leftOverPartialMsg A array of bytes of the first part of a partial message.
     */
    public void setLeftOverPartialMsg(byte[] leftOverPartialMsg) {
        int leftOverPartialMsgSize = leftOverPartialMsg.length;
        this.leftOverPartialMsg = new byte[leftOverPartialMsgSize];
        this.leftOverPartialMsg = leftOverPartialMsg;
    }
}
