# Anchor

**Unified Terminal Interface for Embedded and Remote Systems**

Anchor is a Java web application that unifies serial and SSH terminal communication into a single browser-based interface. It lets engineers connect to hardware devices (Arduino, routers, embedded boards) via USB serial and remote servers via SSH, all from one browser tab, from any device on the network.

## Features

- **Unified terminal**: Serial (USB) and SSH in one interface
- **Web-based**: Access from any browser on the network - no desktop app install
- **Multi-user**: First user is owner (read-write), others are viewers (read-only)
- **Ownership transfer**: Give control to any connected user with one command
- **Auto-detection**: Lists available USB serial ports automatically
- **Session persistence**: Device connection survives page refresh
- **Authentication**: User registration, login, session management
- **Network accessible**: Server binds to all interfaces for multi-laptop access

## Tech Stack

- Java 11+
- Apache Maven (build)
- Jetty / Apache Tomcat (servlet container)
- Servlets, JSP, JSTL (web layer)
- WebSocket API / JSR 356 (real-time terminal)
- jSerialComm (serial port communication)
- JSch (SSH client)
- MySQL Connector/J (planned for persistence)

## Architecture

Three-layer architecture:

- **Client Layer**: Browser with JSP pages and JavaScript. Communicates via HTTP and WebSocket.
- **Application Layer**: Servlets, WebSocket endpoint, ConnectionManager (Singleton). Runs on Jetty or Tomcat.
- **External Layer**: USB serial devices (via jSerialComm), remote SSH servers (via JSch), MySQL database (planned).

```
Browser (any laptop)
    |
    |--- HTTP --->  Servlets (Login, Register, DeviceScanner, etc.)
    |--- WebSocket ---> TerminalWebSocket
                              |
                        ConnectionManager (Singleton)
                              |
                      +-------+--------+
                      |                |
              SerialConnection   SshConnection
               (jSerialComm)       (JSch)
                      |                |
                 USB Device       SSH Server
```

## Security

- SHA-256 password hashing with per-user salt (SecureRandom)
- HttpSession with 30-minute timeout
- AuthenticationFilter blocks unauthorized access to all pages
- Viewers cannot send commands to devices - read-only enforcement

## Multi-User Flow

1. User A connects to a device - becomes **OWNER** (read-write)
2. User B joins the same device - becomes **VIEWER** (read-only, sees all output)
3. User A types `give userB` - ownership transfers
4. User A becomes viewer, User B can now type commands
5. Both still see all output

## Team

| Name | Contribution |
|------|-------------|
| Ayush Kadali | ConnectionManager, TerminalWebSocket, REST APIs, Maven config, integration |
| Krishna Khandelwal | Abstract Connection, SerialConnection, SshConnection |
| Mohammad Khot | User model, authentication servlets, filter, web.xml |
| Yashoda Varma | JSP pages, dashboard, JavaScript (AJAX, WebSocket client), styling |

## Getting Started

See [INSTALL.md](INSTALL.md) for installation and setup instructions.

## License

Academic project - Mini Project AID3PR03A
