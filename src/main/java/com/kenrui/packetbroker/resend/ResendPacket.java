package com.kenrui.packetbroker.resend;

import com.kenrui.packetbroker.utilities.MyThreadFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Resend packet thread for resending packets due to slower consumers / dead clients / bad network connection.
 */
@Component
public abstract class ResendPacket implements Runnable {
    private int threadCount;
    private ExecutorService executorService;
    private static final Logger logger = LogManager.getLogger("ResendPacket");
    protected ListIterator lit; // protected so Mockito's Spy can access it

    /**
     * Creates resend packet thread.  Uses ResendPacketHandler to process each PacketToResend object.
     *
     * @param threadCount Threads used for handling resend to remote clients.
     */
    @Autowired
    public ResendPacket(int threadCount) {
        this.threadCount = threadCount;
    }

    @Lookup
    public abstract Callable<String> getResendPacketHandler();

    @Override
    public void run() {
        List<Future<String>> futureList = new ArrayList<>();
        executorService = Executors.newCachedThreadPool(new MyThreadFactory("ResendPacketHandler", "ResendPacketHandler"));

        for (int i = 1; i <= threadCount; i++) {
            Callable<String> resendPacketHandlerThread = getResendPacketHandler();
            futureList.add(executorService.submit(resendPacketHandlerThread));
        }

        while (!Thread.currentThread().isInterrupted()) {
            // Check if list of handler threads are still running
            if (futureList.size() > 0) {
                lit = futureList.listIterator();
                while (lit.hasNext()) {
                    Future futureTask = (Future) lit.next();
                    if (futureTask.isCancelled()) {
                        // Cancel is true if handler thread is cancelled by executorService
                        threadCancelledRoutine();
                    } else if (futureTask.isDone()) {
                        // Done is true if handler thread has been terminated or interrupted externally
                        threadDoneRoutine();
                    }
                }

                // Sleep for 5 seconds and then check if handler threads are still running
                try {
                    Thread.sleep(5000);
                } catch (Exception e) {
                    logger.error(e);
                }
            }
        }

        executorService.shutdown();
    }

    protected void threadDoneRoutine() {
        // todo: decide if we want to implement logic to respawn handler threads
        logger.info("Handler thread has been terminated or interrupted.");
        lit.remove();
    }

    protected void threadCancelledRoutine() {
        logger.info("Task cancelled");
    }
}
