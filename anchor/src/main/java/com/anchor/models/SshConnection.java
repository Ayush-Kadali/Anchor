package com.anchor.models;

import com.jcraft.jsch.*;
import java.io.*;
import java.util.UUID;

/**
 * SSH connection implementation.
 *
 * Demonstrates:
 * - Inheritance (extends Connection)
 * - JSch library usage for SSH
 * - Stream handling for I/O
 *
 * NOTE: Prototype implementation - exploring JSch integration
 * TODO: Add key-based authentication support
 * TODO: Add proper session management
 */
public class SshConnection extends Connection {

    private String host;
    private int port;
    private String username;
    private String password;

    private JSch jsch;
    private Session session;
    private Channel channel;
    private InputStream inputStream;
    private OutputStream outputStream;

    public SshConnection(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.connectionId = UUID.randomUUID().toString();
        this.jsch = new JSch();
    }

    @Override
    public boolean connect() {
        try {
            // Create session
            session = jsch.getSession(username, host, port);
            session.setPassword(password);

            // Skip host key checking (prototype only!)
            // TODO: Implement proper host key verification
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            // Connect with timeout
            session.connect(10000);

            // Open shell channel with pseudo-terminal
            // PTY is needed for interactive shell (colors, line editing, etc.)
            channel = session.openChannel("shell");
            ((com.jcraft.jsch.ChannelShell) channel).setPty(true);
            ((com.jcraft.jsch.ChannelShell) channel).setPtyType("vt100");
            inputStream = channel.getInputStream();
            outputStream = channel.getOutputStream();
            channel.connect(5000);

            connected = true;
            connectedAt = System.currentTimeMillis();
            System.out.println("[Anchor] SSH connected: " + username + "@" + host);
            return true;

        } catch (Exception e) {
            System.err.println("[Anchor] SSH connect error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void disconnect() {
        try {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
            System.out.println("[Anchor] SSH disconnected: " + host);
        } catch (Exception e) {
            System.err.println("[Anchor] SSH disconnect error: " + e.getMessage());
        }
        connected = false;
    }

    @Override
    public boolean send(byte[] data) {
        if (!connected || outputStream == null) return false;

        try {
            outputStream.write(data);
            outputStream.flush();
            return true;
        } catch (IOException e) {
            System.err.println("[Anchor] SSH send error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public byte[] receive() {
        if (!connected || inputStream == null) return new byte[0];

        try {
            int available = inputStream.available();
            if (available <= 0) return new byte[0];

            byte[] buffer = new byte[available];
            inputStream.read(buffer);
            return buffer;
        } catch (IOException e) {
            System.err.println("[Anchor] SSH receive error: " + e.getMessage());
            return new byte[0];
        }
    }

    @Override
    public String getType() {
        return "SSH";
    }

    // Getters for SSH-specific properties
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
}
