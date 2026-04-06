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
 * Manages all active device connections.
 *
 * Key behavior:
 * - Device connections PERSIST even if all WebSocket sessions disconnect
 * - When a new user connects, they see active connections and can join
 * - First person to join a connection is OWNER (read-write)
 * - Others are VIEWERS (read-only, see all output)
 * - Owner leaving promotes next viewer, but connection stays alive
 * - Connection only dies when explicitly closed or device disconnects
 *
 * This means:
 * - Page refresh doesn't kill the device connection
 * - Navigating to status page doesn't kill it
 * - New users automatically see what's connected
 */
public class ConnectionManager {

    private static ConnectionManager instance;

    // connectionId -> Connection object (the actual serial/ssh connection)
    private Map<String, Connection> connections = new ConcurrentHashMap<>();

    // connectionId -> wsSessionId of current owner
    private Map<String, String> connectionOwner = new ConcurrentHashMap<>();

    // connectionId -> list of viewer WebSocket sessions
    private Map<String, List<Session>> connectionViewers = new ConcurrentHashMap<>();

    // wsSessionId -> connectionId (reverse lookup)
    private Map<String, String> sessionToConnection = new ConcurrentHashMap<>();

    // connectionId -> username who created it
    private Map<String, String> connectionUser = new ConcurrentHashMap<>();

    // port name -> connectionId (prevents opening same port twice)
    private Map<String, String> portToConnection = new ConcurrentHashMap<>();

    // connectionId -> description for display (e.g. "COM3 @ 9600" or "root@192.168.1.1")
    private Map<String, String> connectionDescription = new ConcurrentHashMap<>();

    // track viewer usernames: wsSessionId -> username
    private Map<String, String> sessionUsername = new ConcurrentHashMap<>();

    // reader threads: connectionId -> Thread
    // keep reader alive even if owner disconnects
    private Map<String, Thread> readerThreads = new ConcurrentHashMap<>();
    private Map<String, Boolean> readerRunning = new ConcurrentHashMap<>();

    // owner WebSocket session object (needed to send data)
    private Map<String, Session> ownerSessions = new ConcurrentHashMap<>();

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
     * Create a serial connection or join existing one as viewer.
     */
    public synchronized String connectSerial(String port, int baudRate,
                                              Session wsSession, String username) {
        String existingConnId = portToConnection.get(port);

        if (existingConnId != null) {
            Connection existing = connections.get(existingConnId);
            if (existing != null && existing.isConnected()) {
                // port in use - join as viewer (or take ownership if no owner)
                return joinConnection(existingConnId, wsSession, username);
            }
            cleanup(existingConnId);
        }

        // port is free, create new connection
        SerialConnection conn = new SerialConnection(port, baudRate);
        if (conn.connect()) {
            String connId = conn.getConnectionId();
            connections.put(connId, conn);
            connectionOwner.put(connId, wsSession.getId());
            ownerSessions.put(connId, wsSession);
            connectionViewers.put(connId, Collections.synchronizedList(new ArrayList<>()));
            sessionToConnection.put(wsSession.getId(), connId);
            connectionUser.put(connId, username);
            sessionUsername.put(wsSession.getId(), username);
            portToConnection.put(port, connId);
            connectionDescription.put(connId, port + " @ " + baudRate + " baud");

            // start reader thread (managed by ConnectionManager, not WebSocket)
            startReaderThread(connId);

            System.out.println("[Anchor] " + username + " connected to " + port + " as OWNER");
            return "OWNER:" + connId;
        }
        return null;
    }

    /*
     * Create an SSH connection or join existing one.
     */
    public synchronized String connectSsh(String host, int port, String sshUser,
                                           String password, Session wsSession, String username) {
        // for SSH, check if same host:port:user is already connected
        String sshKey = sshUser + "@" + host + ":" + port;
        for (Map.Entry<String, String> entry : connectionDescription.entrySet()) {
            if (entry.getValue().equals(sshKey)) {
                String existingId = entry.getKey();
                Connection existing = connections.get(existingId);
                if (existing != null && existing.isConnected()) {
                    return joinConnection(existingId, wsSession, username);
                }
            }
        }

        SshConnection conn = new SshConnection(host, port, sshUser, password);
        if (conn.connect()) {
            String connId = conn.getConnectionId();
            connections.put(connId, conn);
            connectionOwner.put(connId, wsSession.getId());
            ownerSessions.put(connId, wsSession);
            connectionViewers.put(connId, Collections.synchronizedList(new ArrayList<>()));
            sessionToConnection.put(wsSession.getId(), connId);
            connectionUser.put(connId, username);
            sessionUsername.put(wsSession.getId(), username);
            connectionDescription.put(connId, sshKey);

            startReaderThread(connId);

            System.out.println("[Anchor] " + username + " SSH connected to " + host + " as OWNER");
            return "OWNER:" + connId;
        }
        return null;
    }

    /*
     * Join an existing connection.
     * If there's no current owner, become owner.
     * If there's an owner, become viewer.
     */
    private String joinConnection(String connId, Session wsSession, String username) {
        sessionToConnection.put(wsSession.getId(), connId);
        sessionUsername.put(wsSession.getId(), username);

        String currentOwnerId = connectionOwner.get(connId);
        Session currentOwnerSession = ownerSessions.get(connId);

        // if no owner or owner's session is dead, take ownership
        if (currentOwnerId == null || currentOwnerSession == null || !currentOwnerSession.isOpen()) {
            connectionOwner.put(connId, wsSession.getId());
            ownerSessions.put(connId, wsSession);
            connectionUser.put(connId, username);
            System.out.println("[Anchor] " + username + " took ownership of " + connId);
            return "OWNER:" + connId;
        }

        // owner exists and is alive, join as viewer
        List<Session> viewers = connectionViewers.get(connId);
        if (viewers != null) {
            viewers.add(wsSession);
        }
        String ownerName = connectionUser.get(connId);
        System.out.println("[Anchor] " + username + " joined as VIEWER on " + connId);
        return "VIEW:" + connId + ":" + ownerName;
    }

    /*
     * Reader thread - runs independently of any WebSocket session.
     * Polls device for data and pushes to owner + all viewers.
     * Stays alive as long as the device connection is alive.
     */
    private void startReaderThread(String connId) {
        readerRunning.put(connId, true);

        Thread reader = new Thread(() -> {
            while (readerRunning.getOrDefault(connId, false)) {
                try {
                    Connection conn = connections.get(connId);
                    if (conn == null || !conn.isConnected()) break;

                    byte[] data = conn.receive();
                    if (data.length > 0) {
                        String text = new String(data);

                        // send to owner (if connected)
                        Session owner = ownerSessions.get(connId);
                        if (owner != null && owner.isOpen()) {
                            try {
                                owner.getBasicRemote().sendText(text);
                            } catch (IOException e) {
                                // owner session died, will be cleaned up
                            }
                        }

                        // send to all viewers
                        broadcastToViewers(connId, text);
                    }
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("[Anchor] Reader error on " + connId + ": " + e.getMessage());
                    break;
                }
            }
            System.out.println("[Anchor] Reader thread stopped for " + connId);
        });
        reader.setDaemon(true);
        reader.setName("reader-" + connId.substring(0, 8));
        reader.start();
        readerThreads.put(connId, reader);
    }

    /*
     * Send data to device. Only OWNER can send.
     */
    public boolean send(String wsSessionId, byte[] data) {
        String connId = sessionToConnection.get(wsSessionId);
        if (connId == null) return false;

        if (!wsSessionId.equals(connectionOwner.get(connId))) {
            return false; // viewer cannot send
        }

        Connection conn = connections.get(connId);
        if (conn != null && conn.isConnected()) {
            return conn.send(data);
        }
        return false;
    }

    /*
     * Send to all viewers of a connection.
     */
    public void broadcastToViewers(String connectionId, String data) {
        List<Session> viewers = connectionViewers.get(connectionId);
        if (viewers == null) return;

        synchronized (viewers) {
            Iterator<Session> it = viewers.iterator();
            while (it.hasNext()) {
                Session viewer = it.next();
                if (viewer.isOpen()) {
                    try {
                        viewer.getBasicRemote().sendText(data);
                    } catch (IOException e) {
                        it.remove();
                    }
                } else {
                    it.remove();
                }
            }
        }
    }

    public boolean isOwner(String wsSessionId) {
        String connId = sessionToConnection.get(wsSessionId);
        if (connId == null) return false;
        return wsSessionId.equals(connectionOwner.get(connId));
    }

    /*
     * WebSocket session disconnected (page refresh, navigate away, close tab).
     * DOES NOT kill the device connection.
     * Just removes this session from owner/viewer tracking.
     */
    public synchronized void sessionDisconnected(String wsSessionId) {
        String connId = sessionToConnection.remove(wsSessionId);
        if (connId == null) return;

        String ownerId = connectionOwner.get(connId);

        if (wsSessionId.equals(ownerId)) {
            // owner left - try to promote a viewer
            List<Session> viewers = connectionViewers.get(connId);

            if (viewers != null) {
                synchronized (viewers) {
                    // find first alive viewer
                    while (!viewers.isEmpty()) {
                        Session next = viewers.remove(0);
                        if (next.isOpen()) {
                            connectionOwner.put(connId, next.getId());
                            ownerSessions.put(connId, next);
                            sessionToConnection.put(next.getId(), connId);
                            try {
                                next.getBasicRemote().sendText(
                                    "\r\n[You are now the OWNER. You can type commands.]\r\n");
                            } catch (IOException e) {
                                continue; // this one is dead too, try next
                            }
                            System.out.println("[Anchor] Viewer promoted to owner on " + connId);
                            return;
                        }
                    }
                }
            }

            // no viewers alive - connection stays open but ownerless
            // next person to join will become owner
            connectionOwner.remove(connId);
            ownerSessions.remove(connId);
            System.out.println("[Anchor] Connection " + connId + " has no owner (still alive)");
        } else {
            // viewer left
            List<Session> viewers = connectionViewers.get(connId);
            if (viewers != null) {
                synchronized (viewers) {
                    viewers.removeIf(s -> s.getId().equals(wsSessionId));
                }
            }
        }
    }

    /*
     * Transfer ownership from current owner to next viewer.
     * Current owner becomes a viewer.
     * Returns true if transfer happened, false if no viewers to transfer to.
     */
    public synchronized boolean transferOwnership(String connId, Session currentOwnerSession) {
        List<Session> viewers = connectionViewers.get(connId);
        if (viewers == null || viewers.isEmpty()) {
            return false;
        }

        synchronized (viewers) {
            // find first alive viewer
            while (!viewers.isEmpty()) {
                Session newOwner = viewers.remove(0);
                if (newOwner.isOpen()) {
                    // new owner setup
                    connectionOwner.put(connId, newOwner.getId());
                    ownerSessions.put(connId, newOwner);
                    // update reverse lookup
                    sessionToConnection.put(newOwner.getId(), connId);

                    try {
                        newOwner.getBasicRemote().sendText(
                            "\r\n[You are now the OWNER. You can type commands.]\r\n");
                    } catch (IOException e) {
                        continue; // dead, try next
                    }

                    // old owner becomes viewer
                    viewers.add(currentOwnerSession);

                    System.out.println("[Anchor] Ownership transferred on " + connId);
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * Transfer ownership to a specific user by username.
     * If username is null or empty, transfers to next viewer in line.
     */
    public synchronized boolean transferToUser(String connId, Session currentOwner, String targetUsername) {
        List<Session> viewers = connectionViewers.get(connId);
        if (viewers == null || viewers.isEmpty()) return false;

        synchronized (viewers) {
            // if no target specified, give to first viewer
            if (targetUsername == null || targetUsername.isEmpty()) {
                return transferOwnership(connId, currentOwner);
            }

            // find viewer with matching username
            Session target = null;
            for (Session v : viewers) {
                String vName = sessionUsername.get(v.getId());
                if (targetUsername.equals(vName) && v.isOpen()) {
                    target = v;
                    break;
                }
            }

            if (target == null) return false;

            // remove target from viewers
            viewers.remove(target);

            // set new owner
            connectionOwner.put(connId, target.getId());
            ownerSessions.put(connId, target);
            connectionUser.put(connId, targetUsername);

            try {
                target.getBasicRemote().sendText(
                    "\r\n[You are now the OWNER. You can type commands.]\r\n");
            } catch (IOException e) {
                return false;
            }

            // old owner becomes viewer
            viewers.add(currentOwner);

            System.out.println("[Anchor] Ownership given to " + targetUsername + " on " + connId);
            return true;
        }
    }

    /*
     * Get list of viewers for a connection (usernames).
     */
    public List<String> getViewerNames(String connId) {
        List<String> names = new ArrayList<>();
        List<Session> viewers = connectionViewers.get(connId);
        if (viewers == null) return names;

        synchronized (viewers) {
            for (Session v : viewers) {
                String name = sessionUsername.getOrDefault(v.getId(), "unknown");
                if (v.isOpen()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    public String getSessionUsername(String wsSessionId) {
        return sessionUsername.getOrDefault(wsSessionId, "unknown");
    }

    /*
     * Explicitly close a device connection (user typed "disconnect").
     */
    public synchronized void closeConnection(String wsSessionId) {
        String connId = sessionToConnection.get(wsSessionId);
        if (connId == null) return;

        // only owner can close
        if (!wsSessionId.equals(connectionOwner.get(connId))) return;

        // notify all viewers
        List<Session> viewers = connectionViewers.get(connId);
        if (viewers != null) {
            synchronized (viewers) {
                for (Session v : viewers) {
                    if (v.isOpen()) {
                        try {
                            v.getBasicRemote().sendText("\r\n[Connection closed by owner.]\r\n");
                        } catch (IOException e) { /* ignore */ }
                    }
                    sessionToConnection.remove(v.getId());
                }
            }
        }

        sessionToConnection.remove(wsSessionId);
        cleanup(connId);
    }

    private void cleanup(String connId) {
        // stop reader thread
        readerRunning.put(connId, false);
        Thread reader = readerThreads.remove(connId);
        if (reader != null) reader.interrupt();

        // close actual device connection
        Connection conn = connections.remove(connId);
        if (conn != null) {
            conn.disconnect();
            if (conn instanceof SerialConnection) {
                portToConnection.remove(((SerialConnection) conn).getPortName());
            }
        }

        connectionOwner.remove(connId);
        ownerSessions.remove(connId);
        connectionViewers.remove(connId);
        connectionUser.remove(connId);
        connectionDescription.remove(connId);
        System.out.println("[Anchor] Connection closed: " + connId);
    }

    /*
     * Get list of active connections for display.
     * Used by dashboard to show what's available to join.
     */
    public List<Map<String, String>> getActiveConnectionsList() {
        List<Map<String, String>> list = new ArrayList<>();
        for (Map.Entry<String, Connection> entry : connections.entrySet()) {
            String connId = entry.getKey();
            Connection conn = entry.getValue();
            if (conn.isConnected()) {
                Map<String, String> info = new HashMap<>();
                info.put("id", connId);
                info.put("type", conn.getType());
                info.put("description", connectionDescription.getOrDefault(connId, "unknown"));
                info.put("owner", connectionUser.getOrDefault(connId, "none"));
                list.add(info);
            }
        }
        return list;
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

    public Map<String, Connection> getAllConnections() {
        return connections;
    }

    public String getDescription(String connectionId) {
        return connectionDescription.get(connectionId);
    }
}
