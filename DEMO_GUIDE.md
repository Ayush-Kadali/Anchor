# Anchor - Demo Guide & Project Status

## How to Start

```bash
cd /Users/ayushkadali/Documents/university/SEM_6/MPJ/mini-project/anchor
mvn jetty:run
```

Open: http://localhost:8080/anchor
From other devices: http://YOUR_IP:8080/anchor (find IP with `ifconfig | grep inet`)

---

## PROJECT STATUS

### Working

| # | Feature | How to Demo |
|---|---------|-------------|
| 1 | Login with password hashing | Login admin/admin123 |
| 2 | User registration | Click "Create new account" |
| 3 | Auth filter blocks unauthorized | Logout, try /dashboard.jsp directly |
| 4 | Device scanning (jSerialComm) | Click "Scan for Devices" |
| 5 | WebSocket terminal | Click "Connect WebSocket", type commands |
| 6 | SSH connection (live) | `connect ssh 127.0.0.1 22 user pass` |
| 7 | Serial connection (live) | `connect serial COM3 9600` (needs USB device) |
| 8 | Multi-user owner/viewer | Two tabs, both join same connection |
| 9 | Transfer ownership | Owner types `give` or `give <username>` |
| 10 | Active connections list | Sidebar shows connections + Join button |
| 11 | Network access | Other laptops access via IP:8080 |
| 12 | Connection persists on refresh | Refresh page, reconnect WS, type `list`, rejoin |
| 13 | JDBC test | Click "Test DB" link |
| 14 | Server status page | Click "Server Status" link |
| 15 | Command echo toggle | "Show my commands" checkbox |
| 16 | Viewer sees owner's commands | Broadcast to all viewers |

### Not Yet Implemented

| Feature | What's Needed | How We'd Implement |
|---------|-------------|-------------------|
| MySQL database | Install MySQL, create tables | UserDAO class replaces HashMap. PreparedStatement for queries. Tables: users, device_permissions, session_logs. |
| xterm.js terminal | Add JS library | Replace plain div with xterm.js Terminal(). Attach WebSocket. Gives colors, cursor, arrow keys, vim support. |
| HTTPS | SSL certificate | Add HTTPS Filter that redirects HTTP to HTTPS. Configure Jetty with keystore. |
| Persistent permissions | MySQL | device_permissions table: (device_port, username, role). Check on join. Roles: AUTO_OWNER, ALLOWED, BLOCKED. |
| Auto-owner flag | MySQL | When user joins, query device_permissions. If AUTO_OWNER, promote immediately. |
| Graphical permission panel | Frontend + API | New JSP page. Shows all users per device. Buttons: Make Owner, Block, Remove. Calls REST API. |
| Session logging/audit | MySQL | session_logs table: (connection_id, username, action, timestamp). Insert on connect/disconnect/give/kick. |
| Auto-reconnect on device reset | ConnectionManager | Detect disconnect in reader thread. Retry with exponential backoff (1s, 2s, 4s, 8s). |
| Profile saving | MySQL | device_profiles table: (name, port, baud, type, auto_connect). Save/load from dashboard. |

---

## DEMO CHECKLIST

Do each step in order. For each step there's what to show, what to say, and which file+line to open if professor asks.

---

### DEMO 1: Login Flow

**Show:** Open http://localhost:8080/anchor. Login with admin / admin123.

**Say:**
> "The form POSTs to /login. web.xml maps that to LoginServlet.
> The servlet gets the password, calls user.verifyPassword() which
> hashes the input with SHA-256 and the stored salt, then compares
> with the stored hash. If it matches, it creates an HttpSession."

**If prof asks to see code:**

| What | File | Line |
|------|------|------|
| Password hashing | User.java | :73 `hashPassword()` - MessageDigest SHA-256 |
| Salt generation | User.java | :62 `generateSalt()` - SecureRandom 16 bytes |
| Password verification | User.java | :54 `verifyPassword()` - hash and compare |
| No setPasswordHash | User.java | :40 - getter exists, no setter (encapsulation) |
| Servlet doPost | LoginServlet.java | :61 `doPost()` - getParameter, verify, create session |
| Session creation | LoginServlet.java | :78-81 - getSession(true), setAttribute |
| URL mapping | web.xml | :30-35 - /login maps to LoginServlet |

---

### DEMO 2: Registration

**Show:** Click "Create new account". Register testuser / test123 / test123.

**Say:**
> "RegisterServlet validates input, checks if username exists, then
> creates new User(). The constructor generates a random salt and
> hashes the password. Plain password is never stored."

**If prof asks:**

| What | File | Line |
|------|------|------|
| Validation | RegisterServlet.java | :37-56 - empty check, password match, username taken |
| User creation | RegisterServlet.java | :61 - new User() triggers hashing |
| User constructor | User.java | :26-31 - generateSalt() then hashPassword() |

---

### DEMO 3: Auth Filter

**Show:** Logout. Type /anchor/dashboard.jsp in URL bar. Gets redirected to login.

**Say:**
> "AuthenticationFilter implements javax.servlet.Filter. It runs before
> every request. Checks session - if no user attribute, redirects to login.
> Login and register pages are whitelisted so they don't get blocked."

**If prof asks:**

| What | File | Line |
|------|------|------|
| Filter class | AuthenticationFilter.java | :25 - implements Filter |
| URL whitelist | AuthenticationFilter.java | :42-45 - skip login, register, css, api |
| Session check | AuthenticationFilter.java | :57 - getSession(false), check user attribute |
| chain.doFilter | AuthenticationFilter.java | :53, :62 - let request through |
| Filter registration | web.xml | :18-24 - url-pattern /* |

---

### DEMO 4: Device Scanning

**Show:** Click "Scan for Devices" on dashboard.

**Say:**
> "JavaScript calls fetch('api/devices') which hits DeviceScannerServlet.
> The servlet calls SerialPort.getCommPorts() from jSerialComm - this asks
> the OS for all serial ports. Returns JSON. JavaScript updates the sidebar."

**If prof asks:**

| What | File | Line |
|------|------|------|
| Servlet | DeviceScannerServlet.java | :34 - doGet, setContentType("application/json") |
| Port enumeration | DeviceScannerServlet.java | :41 - SerialPort.getCommPorts() |
| JSON building | DeviceScannerServlet.java | :43-60 - StringBuilder loop |
| JSON escaping | DeviceScannerServlet.java | :73-80 - escapeJson() prevents injection |
| Frontend AJAX | dashboard.jsp | :305-330 - fetch(), response.json(), update DOM |

---

### DEMO 5: WebSocket Terminal

**Show:** Click "Connect WebSocket". Type `status`. Type `list`.

**Say:**
> "This opens a persistent WebSocket connection to TerminalWebSocket.java.
> Unlike HTTP which is request-response, WebSocket stays open - both sides
> can send anytime. We need this because terminal data is real-time."

**If prof asks:**

| What | File | Line |
|------|------|------|
| @ServerEndpoint | TerminalWebSocket.java | :21 - annotation, auto-detected by container |
| @OnOpen | TerminalWebSocket.java | :26-27 - browser connects |
| @OnMessage | TerminalWebSocket.java | :51-52 - browser sends data |
| Command parsing | TerminalWebSocket.java | :57-72 - if/else for connect, join, list, etc. |
| @OnClose | TerminalWebSocket.java | :301 - sessionDisconnected, keeps device alive |
| Frontend WebSocket | dashboard.jsp | :252-280 - new WebSocket(), onopen, onmessage |

---

### DEMO 6: SSH Connection (HARDWARE DEMO)

**Prerequisite:** Enable Remote Login on Mac (System Settings > Sharing > Remote Login).

**Show:** In terminal type: `connect ssh 127.0.0.1 22 ayushkadali YOUR_PASSWORD`
Then type: `whoami`, `ls`, `pwd`, `date`

**Say:**
> "The command goes through WebSocket to TerminalWebSocket, which calls
> ConnectionManager.connectSsh(). That creates an SshConnection using JSch.
> JSch opens a TCP connection, authenticates, opens a shell channel with PTY.
> A reader thread polls every 50ms for output and pushes it back through
> WebSocket to the browser. When I type 'ls', it goes: browser → WebSocket
> → ConnectionManager.send() → SshConnection.send() → outputStream.write()
> → SSH server executes → response comes back → reader thread → WebSocket
> → browser displays."

**If prof asks:**

| What | File | Line |
|------|------|------|
| SSH connect handler | TerminalWebSocket.java | :158 - handleSshConnect() |
| ConnectionManager SSH | ConnectionManager.java | :119 - connectSsh(), synchronized |
| SshConnection.connect() | SshConnection.java | :42 - jsch.getSession, openChannel("shell") |
| PTY setup | SshConnection.java | :58-59 - setPty(true), setPtyType("vt100") |
| Send to device | SshConnection.java | :87 - outputStream.write(data) |
| Receive from device | SshConnection.java | :97 - inputStream.available(), read() |
| Reader thread | ConnectionManager.java | :177-206 - startReaderThread(), polls every 50ms |
| Data forwarding | TerminalWebSocket.java | :280-298 - handleData(), mgr.send() |

---

### DEMO 7: Multi-User Owner/Viewer

**Show:** Open two browser tabs. Tab 1: login as admin, connect SSH. Tab 2: login as demo, click Join on active connection. Show Tab 2 sees output but can't type.

**Say:**
> "ConnectionManager tracks owner and viewers for each connection.
> When a second user connects to the same device, they join as viewer.
> Viewers see all output via broadcastToViewers() but cannot send commands.
> The send() method checks isOwner() before forwarding to device."

**If prof asks:**

| What | File | Line |
|------|------|------|
| Join logic | ConnectionManager.java | :158 - joinConnection(), checks if owner alive |
| Owner check on send | ConnectionManager.java | :215-220 - send(), isOwner check |
| Broadcast to viewers | ConnectionManager.java | :228-245 - broadcastToViewers(), iterate sessions |
| Viewer blocked message | TerminalWebSocket.java | :292 - "VIEWER: read-only" |
| Owner commands broadcast | TerminalWebSocket.java | :297 - broadcastToViewers("> " + msg) |
| Active connections API | ActiveConnectionsServlet.java | :25-45 - returns JSON for sidebar |
| Join button frontend | dashboard.jsp | :365-380 - refreshConnections(), joinConnection() |

---

### DEMO 8: Ownership Transfer

**Show:** In Tab 1 (owner), type `give demo`. Tab 2 becomes owner. Tab 1 becomes viewer.

**Say:**
> "The give command transfers ownership to a specific user. ConnectionManager
> finds the viewer with that username, promotes them, and demotes the current
> owner. This lets the host give control to a remote student."

**If prof asks:**

| What | File | Line |
|------|------|------|
| Give handler | TerminalWebSocket.java | :203-245 - handleGiveOwnership() |
| Transfer to user | ConnectionManager.java | :307-345 - transferToUser(), find by username |
| Transfer to next | ConnectionManager.java | :287-305 - transferOwnership(), first alive viewer |
| Status shows viewers | TerminalWebSocket.java | :253-278 - handleStatus(), viewer names |

---

### DEMO 9: Connection Persists on Refresh

**Show:** With SSH connected, refresh the page. Connect WebSocket again. Type `list`. See the connection. Type `join <id>`. You're back as owner.

**Say:**
> "When WebSocket closes, @OnClose calls sessionDisconnected() - NOT
> closeConnection(). The device connection stays alive in ConnectionManager.
> Reader thread keeps running. When you reconnect and join, you get back in."

**If prof asks:**

| What | File | Line |
|------|------|------|
| @OnClose | TerminalWebSocket.java | :301-306 - sessionDisconnected, NOT cleanup |
| Session disconnect | ConnectionManager.java | :360-400 - removes session, keeps connection |
| Ownerless connection | ConnectionManager.java | :395 - "no owner, still alive" |
| Rejoin as owner | ConnectionManager.java | :164-167 - no owner? take ownership |

---

### DEMO 10: Network Access

**Show:** Show server IP in dashboard header. Open same URL from phone or teammate's laptop.

**Say:**
> "Jetty binds to 0.0.0.0 which means all network interfaces.
> Any device on the same network can access the server by IP.
> The dashboard shows the server address. Multiple clients can
> connect simultaneously - each gets their own WebSocket session."

**If prof asks:**

| What | File | Line |
|------|------|------|
| 0.0.0.0 binding | pom.xml | :90 - `<host>0.0.0.0</host>` |
| Server IP display | dashboard.jsp | :55 - request.getLocalAddr() |
| Network info section | dashboard.jsp | :100-108 - sidebar shows URL |

---

### DEMO 11: JDBC Test

**Show:** Click "Test DB" (opens new tab). Shows driver loaded.

**Say:**
> "DbTestServlet loads the MySQL driver with Class.forName(), tries
> DriverManager.getConnection(). Driver loads because mysql-connector
> is in pom.xml. Connection fails because MySQL isn't running - that's
> expected. The try-catch-finally handles errors and closes the connection."

**If prof asks:**

| What | File | Line |
|------|------|------|
| Class.forName | DbTestServlet.java | :57 - loads JDBC driver |
| DriverManager | DbTestServlet.java | :62 - getConnection() |
| try-catch-finally | DbTestServlet.java | :53-83 - exception handling |
| finally close | DbTestServlet.java | :77-83 - conn.close() always runs |
| MySQL dependency | pom.xml | :42-46 - mysql-connector-java |

---

### DEMO 12: Server Status

**Show:** Click "Server Status" (opens new tab). Shows connections table with owner and viewers.

**Say:**
> "StatusServlet reads from ConnectionManager. Shows every active connection,
> who owns it, who's viewing. Auto-refreshes every 5 seconds."

**If prof asks:**

| What | File | Line |
|------|------|------|
| Status servlet | StatusServlet.java | :27 - doGet() |
| Viewer names | StatusServlet.java | :57 - mgr.getViewerNames() |
| Auto-refresh | StatusServlet.java | :36 - meta http-equiv refresh |

---

### DEMO 13: OOP Architecture (Show Code)

**Show:** Open Connection.java, SerialConnection.java, SshConnection.java side by side.

**Say:**
> "Connection is abstract - defines the contract. Cannot instantiate it directly.
> SerialConnection and SshConnection extend it with different implementations.
> Both override connect(), send(), receive(). The rest of the system uses
> Connection type - doesn't care if it's serial or SSH. That's polymorphism."

**If prof asks:**

| What | File | Line |
|------|------|------|
| Abstract class | Connection.java | :13 - `public abstract class` |
| Protected fields | Connection.java | :15-17 - accessible by children |
| Abstract methods | Connection.java | :23-47 - no body, must override |
| Concrete method | Connection.java | :50 - isConnected() has body |
| Serial extends | SerialConnection.java | :17 - `extends Connection` |
| Serial connect | SerialConnection.java | :37 - @Override, jSerialComm calls |
| SSH extends | SshConnection.java | :19 - `extends Connection` |
| SSH connect | SshConnection.java | :42 - @Override, JSch calls |

---

### DEMO 14: ConnectionManager Singleton

**Show:** Open ConnectionManager.java.

**Say:**
> "Singleton pattern - private constructor, static getInstance(). Only one
> manager exists. Uses ConcurrentHashMap for thread safety. synchronized
> methods prevent race conditions when two people connect at the same time."

**If prof asks:**

| What | File | Line |
|------|------|------|
| Private constructor | ConnectionManager.java | :66 - `private ConnectionManager()` |
| getInstance | ConnectionManager.java | :70 - `synchronized`, creates once |
| ConcurrentHashMap | ConnectionManager.java | :33-56 - thread-safe maps |
| synchronized method | ConnectionManager.java | :80 - connectSerial() |
| Reader thread | ConnectionManager.java | :177 - daemon thread, polls 50ms |

---

## ARCHITECTURE (Quick Diagram to Draw on Board)

```
Browser (Any Laptop)
  |
  |-- HTTP POST /login ---------> AuthFilter --> LoginServlet --> User.java (SHA-256)
  |-- HTTP GET /api/devices ----> AuthFilter --> DeviceScannerServlet --> jSerialComm
  |-- HTTP GET /api/connections -> AuthFilter --> ActiveConnectionsServlet
  |-- WebSocket /terminal ------> TerminalWebSocket
                                      |
                                 ConnectionManager (Singleton)
                                      |
                                 +---------+---------+
                                 |                   |
                           SerialConnection    SshConnection
                            (jSerialComm)        (JSch)
                                 |                   |
                            USB Device          SSH Server
```

---

## JAVA CONCEPTS CHECKLIST

| Concept | File:Line | Quick Explanation |
|---------|-----------|-------------------|
| Abstract class | Connection.java:13 | Cannot instantiate, defines contract |
| Inheritance | SerialConnection.java:17 | `extends Connection` |
| Polymorphism | ConnectionManager.java:215 | `conn.send()` works for both types |
| Encapsulation | User.java:18-22 | Private fields, no setPasswordHash |
| Method overriding | SerialConnection.java:37 | `@Override connect()` |
| Singleton | ConnectionManager.java:66-73 | Private constructor, getInstance() |
| Servlet lifecycle | LoginServlet.java:32,54,61 | init(), doGet(), doPost() |
| Filter | AuthenticationFilter.java:25 | `implements Filter`, doFilter() |
| HttpSession | LoginServlet.java:78 | getSession(true), setAttribute() |
| WebSocket | TerminalWebSocket.java:21 | @ServerEndpoint, @OnOpen, @OnMessage |
| JDBC | DbTestServlet.java:57-62 | Class.forName, DriverManager |
| try-catch-finally | DbTestServlet.java:53-83 | Exception handling, resource cleanup |
| ConcurrentHashMap | ConnectionManager.java:33 | Thread-safe collection |
| synchronized | ConnectionManager.java:70,80 | Prevents race conditions |
| Daemon thread | ConnectionManager.java:196 | Dies when server stops |
| volatile | ConnectionManager.java:57 | Reader flag visible across threads |
| instanceof | ConnectionManager.java:420 | Check if SerialConnection |
| SHA-256 | User.java:73 | MessageDigest.getInstance("SHA-256") |
| SecureRandom | User.java:62 | Cryptographic salt generation |
| Base64 | User.java:65,79 | Encode bytes to string |
| StringBuilder | DeviceScannerServlet.java:43 | Efficient string building |
| UUID | SerialConnection.java:34 | Unique connection ID |
| Collections.synchronizedList | ConnectionManager.java:100 | Thread-safe list |
| Iterator.remove | ConnectionManager.java:238 | Safe removal during iteration |
