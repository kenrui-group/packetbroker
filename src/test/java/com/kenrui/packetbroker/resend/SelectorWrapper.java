package com.kenrui.packetbroker.resend;

import java.io.IOException;
import java.nio.channels.Selector;

public class SelectorWrapper {

    public SelectorWrapper() throws IOException {

    }

    public Selector getSelector() throws IOException {
        return Selector.open();
    }
}
