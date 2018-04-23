package com.kenrui.packetbroker.structures;

public class RandomPacket {
    private int packetSize;
    private byte[] packet;

    public int getPacketSize() {
        return packetSize;
    }

    public void setPacketSize(int packetSize) {
        this.packetSize = packetSize;
    }

    public byte[] getPacket() {
        return packet;
    }

    public void setPacket(byte[] packet) {
        this.packet = packet;
    }
}
