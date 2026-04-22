# Anchor - Prototype v0.1

> **Status: Early Development / Exploration Phase**

Unified Terminal Interface for Embedded and Remote Systems

## Current State

This prototype demonstrates the foundational architecture we are exploring:

| Feature | Status |
|---------|--------|
| Project Structure | Done |
| User Authentication (Basic) | Working |
| Session Management | Working |
| Device Detection (jSerialComm) | Exploring |
| Serial Communication | Exploring |
| SSH Integration (JSch) | Exploring |
| WebSocket Terminal | Planned |
| xterm.js Integration | Planned |
| Database Persistence | Planned |

## Running the Prototype

### Prerequisites
- Java 11+
- Apache Maven
- Apache Tomcat 9+

### Build
```bash
cd anchor
mvn clean package
```

### Deploy
Copy `target/anchor.war` to Tomcat's `webapps/` directory.

### Access
Open `http://localhost:8080/anchor`

**Demo Credentials:**
- Username: `admin`
- Password: `admin123`

## Project Structure

```
anchor/
├── src/main/java/com/anchor/
│   ├── models/
│   │   ├── Connection.java      # Abstract base class
│   │   ├── SerialConnection.java
│   │   ├── SshConnection.java
│   │   └── User.java
│   └── servlets/
│       ├── LoginServlet.java
│       ├── LogoutServlet.java
│       └── DeviceScannerServlet.java
├── src/main/webapp/
│   ├── WEB-INF/web.xml
│   ├── css/style.css
│   ├── index.jsp
│   ├── login.jsp
│   └── dashboard.jsp
└── pom.xml
```

## Technologies Being Explored

- **jSerialComm** - Pure Java serial port library
- **JSch** - Java SSH implementation
- **WebSocket API** - Real-time communication
- **xterm.js** - Terminal emulator for browser

## Team

- Ayush Kadali (1032232229)
- Krishna Khandelwal (1032232078)
- Mohammad Khot (1032232680)
- Yashoda Varma (1032232146)

---
*Mini Project — AID3PR03A*
