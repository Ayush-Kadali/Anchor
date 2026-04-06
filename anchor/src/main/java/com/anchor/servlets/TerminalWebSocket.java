package com.anchor.servlets;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/*
 * TerminalWebSocket
 * -----------------
 * Handles WebSocket messages from browser.
 * Delegates all connection management to ConnectionManager.
 *
 * Key behavior:
 * - WebSocket disconnect (refresh, navigate) does NOT kill device connection
 * - ConnectionManager keeps connections alive independently
 * - On reconnect, user can rejoin existing connections
 * - "list" command shows active connections you can join
 */
@ServerEndpoint("/terminal")
public class TerminalWebSocket {

    private String username = "unknown";

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("[Anchor] WebSocket opened: " + session.getId());
        try {
            session.getBasicRemote().sendText(
                "Anchor Terminal\r\n" +
                "Commands:\r\n" +
                "  connect serial <port> <baudrate>\r\n" +
                "  connect ssh <host> <port> <user> <password>\r\n" +
                "  join <connectionId>        - join an active connection\r\n" +
                "  list                       - show active connections\r\n" +
                "  give                       - transfer ownership to next viewer\r\n" +
                "  give <username>            - transfer ownership to specific user\r\n" +
                "  disconnect                 - close device connection\r\n" +
                "  status\r\n\r\n"
            );

            // show active connections if any exist
            showActiveConnections(session);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        String msg = message.trim();
        if (msg.isEmpty()) return;

        try {
            if (msg.startsWith("connect serial ")) {
                handleSerialConnect(msg, session);
            } else if (msg.startsWith("connect ssh ")) {
                handleSshConnect(msg, session);
            } else if (msg.startsWith("join ")) {
                handleJoin(msg, session);
            } else if (msg.equals("list")) {
                showActiveConnections(session);
            } else if (msg.startsWith("give")) {
                handleGiveOwnership(msg, session);
            } else if (msg.equals("disconnect")) {
                handleDisconnect(session);
            } else if (msg.equals("status")) {
                handleStatus(session);
            } else if (msg.startsWith("user ")) {
                username = msg.substring(5).trim();
                sendText(session, "Username set to: " + username + "\r\n");
            } else {
                handleData(msg, session);
            }
        } catch (Exception e) {
            sendText(session, "Error: " + e.getMessage() + "\r\n");
        }
    }

    private void showActiveConnections(Session session) {
        ConnectionManager mgr = ConnectionManager.getInstance();
        List<Map<String, String>> active = mgr.getActiveConnectionsList();

        if (active.isEmpty()) {
            sendText(session, "No active device connections.\r\n");
        } else {
            sendText(session, "Active connections:\r\n");
            for (Map<String, String> conn : active) {
                sendText(session, "  [" + conn.get("type") + "] " +
                         conn.get("description") + " (owner: " + conn.get("owner") +
                         ") - join " + conn.get("id").substring(0, 8) + "\r\n");
            }
            sendText(session, "Type 'join <id>' to join a connection.\r\n\r\n");
        }
    }

    private void handleJoin(String msg, Session session) {
        String partialId = msg.substring(5).trim();
        ConnectionManager mgr = ConnectionManager.getInstance();

        // find connection matching partial ID
        for (Map.Entry<String, com.anchor.models.Connection> entry : mgr.getAllConnections().entrySet()) {
            if (entry.getKey().startsWith(partialId)) {
                String connId = entry.getKey();
                // use joinConnection via connectSerial/connectSsh path
                // simpler: just register this session as viewer/owner
                sessionToConnectionDirect(mgr, connId, session);
                return;
            }
        }
        sendText(session, "Connection not found: " + partialId + "\r\n");
    }

    private void sessionToConnectionDirect(ConnectionManager mgr, String connId, Session session) {
        // let ConnectionManager handle the join logic
        String desc = mgr.getDescription(connId);
        com.anchor.models.Connection conn = mgr.getConnection(connId);
        if (conn == null || !conn.isConnected()) {
            sendText(session, "Connection is no longer active.\r\n");
            return;
        }

        // try to join via the serial path (it handles owner/viewer logic)
        if (conn.getType().equals("SERIAL")) {
            com.anchor.models.SerialConnection sc = (com.anchor.models.SerialConnection) conn;
            String result = mgr.connectSerial(sc.getPortName(), sc.getBaudRate(), session, username);
            handleConnectResult(result, session);
        } else {
            // for SSH, just register as viewer
            sendText(session, "Joined connection as VIEWER.\r\n");
        }
    }

    private void handleSerialConnect(String msg, Session session) {
        String[] parts = msg.split(" ");
        if (parts.length < 4) {
            sendText(session, "Usage: connect serial <port> <baudrate>\r\n");
            return;
        }

        String port = parts[2];
        int baud;
        try {
            baud = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            sendText(session, "Invalid baud rate: " + parts[3] + "\r\n");
            return;
        }

        sendText(session, "Connecting to " + port + " at " + baud + " baud...\r\n");

        String result = ConnectionManager.getInstance().connectSerial(port, baud, session, username);
        handleConnectResult(result, session);
    }

    private void handleSshConnect(String msg, Session session) {
        String[] parts = msg.split(" ");
        if (parts.length < 6) {
            sendText(session, "Usage: connect ssh <host> <port> <user> <password>\r\n");
            return;
        }

        String host = parts[2];
        int port;
        try {
            port = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            sendText(session, "Invalid port: " + parts[3] + "\r\n");
            return;
        }
        String user = parts[4];
        String pass = parts[5];

        sendText(session, "Connecting to " + user + "@" + host + ":" + port + "...\r\n");

        String result = ConnectionManager.getInstance().connectSsh(host, port, user, pass, session, username);
        handleConnectResult(result, session);
    }

    private void handleConnectResult(String result, Session session) {
        if (result == null) {
            sendText(session, "Connection failed. Check device/host.\r\n");
            return;
        }

        if (result.startsWith("OWNER:")) {
            sendText(session, "Connected as OWNER. You can type commands.\r\n\r\n");
        } else if (result.startsWith("VIEW:")) {
            String[] r = result.split(":");
            String ownerName = r.length > 2 ? r[2] : "unknown";
            sendText(session, "Device in use by " + ownerName + ".\r\n");
            sendText(session, "Joined as VIEWER (read-only). You can see all output.\r\n\r\n");
        }
    }

    /*
     * Transfer ownership.
     * "give"          → transfer to next viewer in line
     * "give username" → transfer to specific user
     */
    private void handleGiveOwnership(String msg, Session session) {
        ConnectionManager mgr = ConnectionManager.getInstance();
        String connId = mgr.getConnectionForSession(session.getId());

        if (connId == null) {
            sendText(session, "Not connected to any device.\r\n");
            return;
        }

        if (!mgr.isOwner(session.getId())) {
            sendText(session, "You are not the owner.\r\n");
            return;
        }

        // check if specific username given: "give demo" or just "give"
        String targetUser = null;
        if (msg.length() > 4) {
            targetUser = msg.substring(5).trim();
        }

        boolean transferred;
        if (targetUser != null && !targetUser.isEmpty()) {
            transferred = mgr.transferToUser(connId, session, targetUser);
            if (!transferred) {
                // show who's available
                List<String> viewers = mgr.getViewerNames(connId);
                sendText(session, "User '" + targetUser + "' not found in viewers.\r\n");
                if (viewers.isEmpty()) {
                    sendText(session, "No viewers connected.\r\n");
                } else {
                    sendText(session, "Available viewers: " + String.join(", ", viewers) + "\r\n");
                }
                return;
            }
        } else {
            transferred = mgr.transferOwnership(connId, session);
        }

        if (transferred) {
            sendText(session, "[You are now a VIEWER.]\r\n");
        } else {
            sendText(session, "No viewers to transfer to.\r\n");
        }
    }

    private void handleDisconnect(Session session) {
        ConnectionManager.getInstance().closeConnection(session.getId());
        sendText(session, "Connection closed.\r\n");
    }

    private void handleStatus(Session session) {
        ConnectionManager mgr = ConnectionManager.getInstance();
        String connId = mgr.getConnectionForSession(session.getId());
        sendText(session, "Active connections on server: " + mgr.getActiveCount() + "\r\n");
        if (connId != null) {
            boolean owner = mgr.isOwner(session.getId());
            String desc = mgr.getDescription(connId);
            String ownerName = mgr.getOwnerName(connId);
            java.util.List<String> viewers = mgr.getViewerNames(connId);

            sendText(session, "Connection: " + desc + "\r\n");
            sendText(session, "Your role: " + (owner ? "OWNER (can type)" : "VIEWER (read-only)") + "\r\n");
            sendText(session, "Owner: " + ownerName + "\r\n");
            if (!viewers.isEmpty()) {
                sendText(session, "Viewers: " + String.join(", ", viewers) + "\r\n");
            } else {
                sendText(session, "Viewers: none\r\n");
            }
            if (owner) {
                sendText(session, "Use 'give <username>' to transfer ownership.\r\n");
            }
        } else {
            sendText(session, "You are not connected to any device.\r\n");
            sendText(session, "Type 'list' to see available connections.\r\n");
        }
    }

    private void handleData(String msg, Session session) {
        ConnectionManager mgr = ConnectionManager.getInstance();
        String connId = mgr.getConnectionForSession(session.getId());

        if (connId == null) {
            sendText(session, "Not connected to a device. Type 'list' or 'connect serial/ssh'.\r\n");
            return;
        }

        if (!mgr.isOwner(session.getId())) {
            sendText(session, "[VIEWER: read-only. Only the owner can type commands.]\r\n");
            return;
        }

        // send command to device
        mgr.send(session.getId(), (msg + "\r\n").getBytes());

        // broadcast what was typed to all viewers so they can see commands too
        mgr.broadcastToViewers(connId, "> " + msg + "\r\n");
    }

    @OnClose
    public void onClose(Session session) {
        System.out.println("[Anchor] WebSocket closed: " + session.getId());
        // session disconnected but device connection stays alive
        ConnectionManager.getInstance().sessionDisconnected(session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("[Anchor] WebSocket error: " + throwable.getMessage());
    }

    private void sendText(Session session, String text) {
        try {
            session.getBasicRemote().sendText(text);
        } catch (IOException e) {
            System.err.println("[Anchor] Send error: " + e.getMessage());
        }
    }
}
