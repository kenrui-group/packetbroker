package com.kenrui.packetbroker.resend;

import com.kenrui.packetbroker.structures.PacketToResend;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

/**
 * Handler for processing each packet that needs to be resent to remote clients.
 */
@Component
@Scope("prototype")
public class ResendPacketHandlerStub implements Callable<String> {
    private BlockingQueue packetsToResendQueue;
    private PacketToResend packetToResend = null;
    private int threadTimeOut;
    private static int threadCreated = 0;
    private int threadNum;
    private static final Logger logger = LogManager.getLogger("ResendPacketHandlerStub");

    /**
     * Creates handler for PacketToResend object.
     *
     * @param packetsToResendQueue Queue with list of PacketToResend objects, which contain packets to resend, and remote clients for each packet.
     */
    @Autowired
    public ResendPacketHandlerStub(BlockingQueue packetsToResendQueue, int threadTimeOut) {
        this.packetsToResendQueue = packetsToResendQueue;
        this.threadTimeOut = threadTimeOut;
        threadCreated++;
        this.threadNum = threadCreated;
    }

    @Override
    public String call() {
        while (!Thread.currentThread().isInterrupted()) {

        }

        return null;
    }
}
