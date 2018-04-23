package com.kenrui.packetbroker.resend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public abstract class ResendPacketProxy extends ResendPacket {
    protected boolean threadDoneRoutineCalled = false;
    protected boolean threadCancelledRoutineCalled = false;

    /**
     * Creates resend packet thread.  Uses ResendPacketHandler to process each PacketToResend object.
     *
     * @param threadCount Threads used for handling resend to remote clients.
     */
    @Autowired
    public ResendPacketProxy(int threadCount) {
        super(threadCount);
    }

    @Override
    protected void threadDoneRoutine() {
        this.threadDoneRoutineCalled = true;
        super.threadDoneRoutine();
    }

    @Override
    protected void threadCancelledRoutine() {
        this.threadCancelledRoutineCalled = true;
        super.threadCancelledRoutine();
    }

}
