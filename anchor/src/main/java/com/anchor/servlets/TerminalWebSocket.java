package com.anchor.servlets;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;

/*
 * TerminalWebSocket
 * -----------------
 * Real-time terminal over WebSocket with multi-user support.
 *
 * Handles two scenarios the professor asked about:
 *
 * 1. MULTIPLE LAPTOPS AS SERVERS:
 *    - Each laptop runs Anchor with its own hardware
 *    - Server binds to 0.0.0.0 so any device on network can access
 *    - Client just opens http://SERVER_IP:8080/anchor
 *    - Each server is independent, manages its own devices
 *
 * 2. MULTIPLE PEOPLE, SAME DEVICE:
 *    - First person to connect becomes OWNER (can type commands)
 *    - Others become VIEWERS (see output but cannot type)
 *    - When owner leaves, first viewer gets promoted
 *    - Like screen sharing: professor watches student work
 *
 * Protocol:
 *   "connect serial <port> <baud>"       → open serial / join as viewer
 *   "connect ssh <host> <port> <u> <p>"  → open SSH
 *   "disconnect"                          → leave connection
 *   anything else                         → send to device (owner only)
 */
@ServerEndpoint("/terminal")
public class TerminalWebSocket {

    private Thread readerThread;
    private volatile boolean reading = false;

    // we store the username by extracting from the HTTP session
    // but WebSocket doesn't have direct access to HttpSession
    // so for prototype we track it per connection
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
                "  disconnect\r\n" +
                "  status\r\n\r\n"
            );
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
            } else if (msg.equals("disconnect")) {
                handleDisconnect(session);
            } else if (msg.equals("status")) {
                handleStatus(session);
            } else if (msg.startsWith("user ")) {
                // set username: "user ayush"
                username = msg.substring(5).trim();
                sendText(session, "Username set to: " + username + "\r\n");
            } else {
                handleData(msg, session);
            }
        } catch (Exception e) {
            sendText(session, "Error: " + e.getMessage() + "\r\n");
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

        ConnectionManager mgr = ConnectionManager.getInstance();
        String result = mgr.connectSerial(port, baud, session, username);

        if (result == null) {
            sendText(session, "Failed to connect. Check port name and device.\r\n");
            return;
        }

        if (result.startsWith("OWNER:")) {
            String connId = result.split(":")[1];
            sendText(session, "Connected as OWNER. You can type commands.\r\n\r\n");
            startReaderThread(session, connId);

        } else if (result.startsWith("VIEW:")) {
            // VIEW:connId:ownerName
            String[] r = result.split(":");
            String ownerName = r[2];
            sendText(session, "Device in use by " + ownerName + ".\r\n");
            sendText(session, "Joined as VIEWER (read-only). You can see the output.\r\n");
            sendText(session, "You will become owner when " + ownerName + " disconnects.\r\n\r\n");
            // viewers don't need their own reader thread
            // they receive data via broadcastToViewers
        }
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

        ConnectionManager mgr = ConnectionManager.getInstance();
        String result = mgr.connectSsh(host, port, user, pass, session, username);

        if (result != null && result.startsWith("OWNER:")) {
            String connId = result.split(":")[1];
            sendText(session, "SSH connected as OWNER.\r\n\r\n");
            startReaderThread(session, connId);
        } else {
            sendText(session, "SSH connection failed.\r\n");
        }
    }

    private void handleDisconnect(Session session) {
        stopReaderThread();
        ConnectionManager.getInstance().disconnect(session.getId());
        sendText(session, "Disconnected.\r\n");
    }

    private void handleStatus(Session session) {
        ConnectionManager mgr = ConnectionManager.getInstance();
        String connId = mgr.getConnectionForSession(session.getId());
        sendText(session, "Active connections on this server: " + mgr.getActiveCount() + "\r\n");
        if (connId != null) {
            boolean owner = mgr.isOwner(session.getId());
            sendText(session, "Your role: " + (owner ? "OWNER (can type)" : "VIEWER (read-only)") + "\r\n");
            sendText(session, "Connection: " + connId + "\r\n");
        } else {
            sendText(session, "Not connected to any device.\r\n");
        }
    }

    private void handleData(String msg, Session session) {
        ConnectionManager mgr = ConnectionManager.getInstance();
        String connId = mgr.getConnectionForSession(session.getId());

        if (connId == null) {
            sendText(session, "echo: " + msg + "\r\n");
            sendText(session, "(Not connected. Use 'connect serial' or 'connect ssh')\r\n");
            return;
        }

        if (!mgr.isOwner(session.getId())) {
            sendText(session, "[You are a VIEWER. Only the owner can send commands.]\r\n");
            return;
        }

        // send to device
        mgr.send(session.getId(), (msg + "\r\n").getBytes());
    }

    /*
     * Reader thread polls device for data and sends to:
     * 1. The owner (via their WebSocket session)
     * 2. All viewers (via ConnectionManager.broadcastToViewers)
     *
     * This is the key part that makes real-time terminal work.
     * Device can send data at any time - we push it instantly.
     */
    private void startReaderThread(Session session, String connId) {
        reading = true;
        ConnectionManager mgr = ConnectionManager.getInstance();

        readerThread = new Thread(() -> {
            while (reading && session.isOpen()) {
                try {
                    byte[] data = mgr.receive(connId);
                    if (data.length > 0) {
                        String text = new String(data);
                        // send to owner
                        session.getBasicRemote().sendText(text);
                        // send to all viewers too
                        mgr.broadcastToViewers(connId, text);
                    }
                    Thread.sleep(50);
                } catch (Exception e) {
                    if (reading) {
                        System.err.println("[Anchor] Reader error: " + e.getMessage());
                    }
                    break;
                }
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void stopReaderThread() {
        reading = false;
        if (readerThread != null) {
            readerThread.interrupt();
        }
    }

    @OnClose
    public void onClose(Session session) {
        System.out.println("[Anchor] WebSocket closed: " + session.getId());
        stopReaderThread();
        ConnectionManager.getInstance().disconnect(session.getId());
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
