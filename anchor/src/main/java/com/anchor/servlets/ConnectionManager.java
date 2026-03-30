package com.anchor.servlets;

import com.anchor.models.Connection;
import com.anchor.models.SerialConnection;
import com.anchor.models.SshConnection;

import javax.websocket.Session;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/*
 * ConnectionManager (Singleton)
 * ----------------------------
 * Manages all active device connections across all clients.
 *
 * Handles two key problems:
 *
 * 1. MULTI-DEVICE: Different devices on this server, each can be
 *    used by a different client simultaneously.
 *
 * 2. SAME-DEVICE CONFLICT: When two people want the same device:
 *    - First person becomes the OWNER (can read AND write)
 *    - Others become VIEWERS (can only watch the output)
 *    - When owner disconnects, first viewer gets promoted to owner
 *    - This is like screen-sharing: professor can watch student's terminal
 *
 * Why Singleton: Only one manager should exist. If two managers existed,
 * they wouldn't know about each other's connections and could open the
 * same serial port twice (which crashes).
 *
 * Why ConcurrentHashMap: Multiple WebSocket threads access this
 * simultaneously. Regular HashMap is not thread-safe - two threads
 * writing at the same time can corrupt it.
 *
 * Java concepts: Singleton, ConcurrentHashMap, synchronized,
 *                Observer-like pattern (viewers), instanceof
 */
public class ConnectionManager {

    private static ConnectionManager instance;

    // active connections: connectionId → Connection object
    private Map<String, Connection> connections = new ConcurrentHashMap<>();

    // who OWNS each connection (can send commands): connectionId → wsSessionId
    private Map<String, String> connectionOwner = new ConcurrentHashMap<>();

    // who is VIEWING each connection (read-only): connectionId → list of wsSessionIds
    private Map<String, List<Session>> connectionViewers = new ConcurrentHashMap<>();

    // reverse lookup: wsSessionId → connectionId
    private Map<String, String> sessionToConnection = new ConcurrentHashMap<>();

    // which user owns which connection (for display)
    private Map<String, String> connectionUser = new ConcurrentHashMap<>();

    // track which port is in use (prevents double-open)
    private Map<String, String> portToConnection = new ConcurrentHashMap<>();

    private ConnectionManager() {
        System.out.println("[Anchor] ConnectionManager initialized");
    }

    public static synchronized ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    /*
     * Create a serial connection.
     * If port is already in use by someone else, the new user
     * becomes a VIEWER (can watch output but not type).
     */
    public synchronized String connectSerial(String port, int baudRate,
                                              Session wsSession, String username) {
        // check if this port is already open
        String existingConnId = portToConnection.get(port);

        if (existingConnId != null) {
            Connection existing = connections.get(existingConnId);
            if (existing != null && existing.isConnected()) {
                // port is in use - add as viewer
                String owner = connectionUser.get(existingConnId);
                addViewer(existingConnId, wsSession);
                sessionToConnection.put(wsSession.getId(), existingConnId);

                System.out.println("[Anchor] " + username + " joined as viewer on " + port
                                   + " (owned by " + owner + ")");
                return "VIEW:" + existingConnId + ":" + owner;
            }
            // connection exists but is dead, clean it up
            cleanup(existingConnId);
        }

        // port is free, create new connection
        SerialConnection conn = new SerialConnection(port, baudRate);
        if (conn.connect()) {
            String connId = conn.getConnectionId();
            connections.put(connId, conn);
            connectionOwner.put(connId, wsSession.getId());
            connectionViewers.put(connId, new ArrayList<>());
            sessionToConnection.put(wsSession.getId(), connId);
            connectionUser.put(connId, username);
            portToConnection.put(port, connId);

            System.out.println("[Anchor] " + username + " connected to " + port + " as owner");
            return "OWNER:" + connId;
        }

        return null;
    }

    /*
     * Create an SSH connection.
     * SSH connections are always unique (different sessions to same host are fine).
     */
    public synchronized String connectSsh(String host, int port, String sshUser,
                                           String password, Session wsSession, String username) {
        SshConnection conn = new SshConnection(host, port, sshUser, password);
        if (conn.connect()) {
            String connId = conn.getConnectionId();
            connections.put(connId, conn);
            connectionOwner.put(connId, wsSession.getId());
            connectionViewers.put(connId, new ArrayList<>());
            sessionToConnection.put(wsSession.getId(), connId);
            connectionUser.put(connId, username);

            System.out.println("[Anchor] " + username + " SSH connected to " + host);
            return "OWNER:" + connId;
        }
        return null;
    }

    /*
     * Send data to device.
     * Only the OWNER can send. Viewers are read-only.
     */
    public boolean send(String wsSessionId, byte[] data) {
        String connId = sessionToConnection.get(wsSessionId);
        if (connId == null) return false;

        // check if this session is the owner
        String owner = connectionOwner.get(connId);
        if (!wsSessionId.equals(owner)) {
            // this is a viewer, not allowed to send
            return false;
        }

        Connection conn = connections.get(connId);
        if (conn != null && conn.isConnected()) {
            return conn.send(data);
        }
        return false;
    }

    /*
     * Receive data from device.
     * Data goes to owner AND all viewers.
     */
    public byte[] receive(String connectionId) {
        Connection conn = connections.get(connectionId);
        if (conn != null && conn.isConnected()) {
            return conn.receive();
        }
        return new byte[0];
    }

    /*
     * Broadcast received data to all viewers of a connection.
     * Called by the reader thread.
     */
    public void broadcastToViewers(String connectionId, String data) {
        List<Session> viewers = connectionViewers.get(connectionId);
        if (viewers == null) return;

        // iterate and send to each viewer
        Iterator<Session> it = viewers.iterator();
        while (it.hasNext()) {
            Session viewer = it.next();
            if (viewer.isOpen()) {
                try {
                    viewer.getBasicRemote().sendText(data);
                } catch (IOException e) {
                    it.remove(); // remove dead sessions
                }
            } else {
                it.remove();
            }
        }
    }

    // Add a viewer to a connection
    private void addViewer(String connectionId, Session wsSession) {
        List<Session> viewers = connectionViewers.get(connectionId);
        if (viewers != null) {
            viewers.add(wsSession);
        }
    }

    // Check if session is owner (can type) or viewer (read-only)
    public boolean isOwner(String wsSessionId) {
        String connId = sessionToConnection.get(wsSessionId);
        if (connId == null) return false;
        return wsSessionId.equals(connectionOwner.get(connId));
    }

    /*
     * Disconnect a session.
     * If owner disconnects, promote first viewer to owner.
     * If viewer disconnects, just remove from list.
     */
    public synchronized void disconnect(String wsSessionId) {
        String connId = sessionToConnection.remove(wsSessionId);
        if (connId == null) return;

        String owner = connectionOwner.get(connId);

        if (wsSessionId.equals(owner)) {
            // owner is leaving
            List<Session> viewers = connectionViewers.get(connId);

            if (viewers != null && !viewers.isEmpty()) {
                // promote first viewer to owner
                Session newOwner = viewers.remove(0);
                connectionOwner.put(connId, newOwner.getId());
                sessionToConnection.put(newOwner.getId(), connId);

                try {
                    newOwner.getBasicRemote().sendText(
                        "\r\n[You are now the OWNER. You can type commands.]\r\n");
                } catch (IOException e) {
                    // new owner died too, clean up
                    cleanup(connId);
                }
                System.out.println("[Anchor] Viewer promoted to owner on " + connId);
            } else {
                // no viewers, close the actual connection
                cleanup(connId);
            }
        } else {
            // viewer is leaving, just remove
            List<Session> viewers = connectionViewers.get(connId);
            if (viewers != null) {
                viewers.removeIf(s -> s.getId().equals(wsSessionId));
            }
        }
    }

    // Clean up a connection completely
    private void cleanup(String connId) {
        Connection conn = connections.remove(connId);
        if (conn != null) {
            conn.disconnect();

            // remove port mapping for serial
            if (conn instanceof SerialConnection) {
                String port = ((SerialConnection) conn).getPortName();
                portToConnection.remove(port);
            }
        }
        connectionOwner.remove(connId);
        connectionViewers.remove(connId);
        connectionUser.remove(connId);
        System.out.println("[Anchor] Connection cleaned up: " + connId);
    }

    public String getConnectionForSession(String wsSessionId) {
        return sessionToConnection.get(wsSessionId);
    }

    public Connection getConnection(String connectionId) {
        return connections.get(connectionId);
    }

    public int getActiveCount() {
        return connections.size();
    }

    public String getOwnerName(String connectionId) {
        return connectionUser.get(connectionId);
    }
}
