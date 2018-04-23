package com.kenrui.packetbroker.utilities;

import java.util.concurrent.ThreadFactory;

public class MyThreadFactory implements ThreadFactory {
    private String threadGroupName;
    private ThreadGroup threadGroup;
    private String threadPrefix;
    private int count;

    public MyThreadFactory(String threadGroupName, String threadPrefix) {
        this.threadGroupName = threadGroupName;
        threadGroup = new ThreadGroup(threadGroupName);
        this.threadPrefix = threadPrefix;
        this.count = 0;
    }

    @Override
    public Thread newThread(Runnable r) {
        count++;
        return new Thread(threadGroup, r, threadPrefix + " - " + count);
    }
}