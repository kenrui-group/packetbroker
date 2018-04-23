package com.kenrui.packetbroker.structures;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Describe intermediate tunneling hops or remote servers to connect to.
 * This is useful to know which hops a packet has traversed through in a chain of tunnels, or describing
 * to which remote server to connect to for receiving packets captured remotely.
 */
public class ConnectionInfo {
    private InetAddress ip;
    private int port;
    private String hostname;
    private String description;

    public InetAddress getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public String getHostname() {
        return hostname;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Creates a ConnectionInfo with the given parameters.
     * @param ip
     * @param port
     * @param hostname
     * @param description
     */
    public ConnectionInfo(String ip, int port, String hostname, String description)  {
        try {
            this.ip = InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        this.port = port;
        this.hostname = hostname;
        this.description = description;
    }
}
