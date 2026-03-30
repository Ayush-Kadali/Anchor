package com.anchor.models;

/**
 * Abstract base class for all connection types.
 *
 * Demonstrates:
 * - Abstraction
 * - Inheritance (SerialConnection, SshConnection extend this)
 * - Polymorphism (common interface for different connection types)
 *
 * NOTE: Prototype class - methods contain placeholder implementations
 */
public abstract class Connection {

    protected String connectionId;
    protected boolean connected;
    protected long connectedAt;

    /**
     * Establish connection to the device/server
     * @return true if connection successful
     */
    public abstract boolean connect();

    /**
     * Close the connection and release resources
     */
    public abstract void disconnect();

    /**
     * Send data through the connection
     * @param data bytes to send
     * @return true if send successful
     */
    public abstract boolean send(byte[] data);

    /**
     * Receive data from the connection
     * @return received bytes, or empty array if none available
     */
    public abstract byte[] receive();

    /**
     * Get connection type identifier
     * @return "SERIAL" or "SSH"
     */
    public abstract String getType();

    // Common methods
    public boolean isConnected() {
        return connected;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public long getConnectedAt() {
        return connectedAt;
    }
}
