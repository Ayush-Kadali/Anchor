package com.anchor.servlets;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;

/*
 * TerminalWebSocket
 * -----------------
 * This is a WebSocket endpoint. Unlike HTTP (request → response),
 * WebSocket keeps a persistent connection open.
 *
 * Why we need this:
 * - HTTP: browser asks, server responds. one direction at a time.
 * - WebSocket: both sides can send messages anytime. bidirectional.
 * - Terminal needs this because:
 *   - user types a key → send to device instantly
 *   - device sends output → push to browser instantly
 *   - can't wait for browser to "ask" for new data
 *
 * Right now this is just an echo server (sends back what you type).
 * Next step: connect this to SerialConnection or SshConnection
 * so keystrokes go to the actual device.
 *
 * How to test: dashboard.jsp has JavaScript that connects to this.
 *
 * Java concepts: @ServerEndpoint annotation, OnOpen, OnMessage, OnClose
 * This uses JSR 356 (Java WebSocket API)
 */
@ServerEndpoint("/terminal")
public class TerminalWebSocket {

    // called when browser connects
    @OnOpen
    public void onOpen(Session session) {
        System.out.println("[Anchor] WebSocket opened: " + session.getId());
        try {
            // send a welcome message to show it's working
            session.getBasicRemote().sendText(
                "Connected to Anchor Terminal (prototype)\r\n" +
                "This is a WebSocket echo server for now.\r\n" +
                "Type something and it will echo back.\r\n" +
                "Next step: connect this to serial/SSH device.\r\n\r\n$ "
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // called when browser sends a message
    @OnMessage
    public void onMessage(String message, Session session) {
        System.out.println("[Anchor] WebSocket received: " + message);
        try {
            // for now, just echo it back
            // later this will forward to SerialConnection.send() or SshConnection.send()
            session.getBasicRemote().sendText("echo: " + message + "\r\n$ ");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // called when browser disconnects
    @OnClose
    public void onClose(Session session) {
        System.out.println("[Anchor] WebSocket closed: " + session.getId());
    }

    // called on error
    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("[Anchor] WebSocket error: " + throwable.getMessage());
    }
}
