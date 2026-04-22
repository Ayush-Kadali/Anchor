# Anchor Prototype - Walkthrough Notes

Read this before the demo. Follow the demo script in order.

## How to Start

```bash
cd anchor
mvn jetty:run
```

Open: http://localhost:8080/anchor

From another device on the same network: http://YOUR_IP:8080/anchor

---

## All Files in the Project

```
anchor/src/main/java/com/anchor/
  models/
    Connection.java          - Abstract base class (OOP)
    SerialConnection.java    - USB serial via jSerialComm
    SshConnection.java       - SSH via JSch
    User.java                - User model with SHA-256 hashing
  servlets/
    AuthenticationFilter.java - Blocks unauthorized requests
    ConnectionManager.java    - Singleton managing all device connections
    DbTestServlet.java        - JDBC connection test
    DeviceScannerServlet.java - REST API listing serial ports
    LoginServlet.java         - Login authentication
    LogoutServlet.java        - Session destruction
    RegisterServlet.java      - User registration
    TerminalWebSocket.java    - Real-time terminal with owner/viewer

anchor/src/main/webapp/
    WEB-INF/web.xml          - URL routing and filter config
    css/style.css            - Styling
    dashboard.jsp            - Main page with terminal
    index.jsp                - Entry point redirect
    login.jsp                - Login form
    register.jsp             - Registration form

pom.xml                      - Maven dependencies and build
```

---

## Demo Script

### 1. LOGIN

Open http://localhost:8080/anchor

What happens behind the scenes:
1. Browser requests /anchor/
2. Tomcat serves index.jsp (welcome-file in web.xml)
3. AuthenticationFilter runs first (url-pattern /*)
4. Filter sees login.jsp in URI, lets it through
5. index.jsp checks session - no user, redirects to login.jsp

Login with: admin / admin123

What happens:
1. Form POSTs to /login
2. AuthenticationFilter lets /login through
3. LoginServlet.doPost() runs
4. request.getParameter("username") gets "admin"
5. HashMap lookup: users.get("admin")
6. user.verifyPassword("admin123"):
   - Gets stored salt
   - SHA-256("admin123" + salt) via MessageDigest
   - Compares with stored hash
   - Match!
7. Creates HttpSession, stores user and role
8. sendRedirect to dashboard.jsp

---

### 2. REGISTRATION

Click "Create new account" on login page.

Enter: testuser / test123 / test123

What happens:
1. Form POSTs to /register
2. RegisterServlet.doPost() validates:
   - Fields empty? No
   - Passwords match? Yes
   - Username taken? No (LoginServlet.userExists() checks HashMap)
3. new User("testuser", "test123", "USER"):
   - generateSalt() creates 16 random bytes via SecureRandom
   - hashPassword() runs SHA-256 on "test123" + salt
   - Stores ONLY hash and salt, never plain password
4. LoginServlet.addUser(user) adds to HashMap
5. Redirects to login.jsp?registered=true
6. login.jsp sees ?registered=true parameter, shows success message

---

### 3. AUTH FILTER

After logging out, try accessing dashboard.jsp directly in the URL bar.

What happens:
1. Request for /dashboard.jsp
2. AuthenticationFilter.doFilter() runs
3. URI is not login/register/css/api
4. session.getAttribute("user") is null (logged out)
5. Redirects to login.jsp
6. User is blocked from accessing protected pages

---

### 4. DEVICE SCANNING

On the dashboard, click "Scan for Devices"

What happens:
1. JavaScript: fetch('api/devices') - AJAX call, no page reload
2. DeviceScannerServlet.doGet() runs:
   - response.setContentType("application/json")
   - SerialPort.getCommPorts() - jSerialComm asks OS for serial ports
   - Loops through ports, builds JSON with StringBuilder
   - Returns: {"devices":[...], "count": N, "status": "success"}
3. JavaScript parses JSON, builds HTML, updates device list div
4. If device found, clicking it fills the port field

---

### 5. WEBSOCKET TERMINAL

Click "Connect WebSocket"

What happens:
1. JavaScript: new WebSocket('ws://host/anchor/terminal')
2. TerminalWebSocket.java @OnOpen fires
3. Sends welcome message
4. JavaScript ws.onmessage displays it
5. Input box becomes enabled
6. JavaScript sends "user <username>" to identify this session

Now type "status" and press Enter:
1. JavaScript: ws.send("status")
2. TerminalWebSocket @OnMessage fires
3. Calls handleStatus() which queries ConnectionManager
4. Sends back: active connections count, your role

---

### 6. SERIAL CONNECTION

Enter a port name (e.g., COM3 or /dev/ttyUSB0), select baud rate, click "Connect Serial"

What happens:
1. JavaScript sends: "connect serial COM3 9600"
2. TerminalWebSocket.handleSerialConnect() parses the command
3. Calls ConnectionManager.connectSerial("COM3", 9600, session, username):
   - Checks portToConnection map - is this port already in use?
   - If YES: adds this user as VIEWER (read-only)
   - If NO: creates new SerialConnection("COM3", 9600)
     - SerialConnection.connect():
       - SerialPort.getCommPort("COM3") via jSerialComm
       - serialPort.setBaudRate(9600)
       - serialPort.openPort()
       - connected = true
     - Stores in connections map
     - This user becomes OWNER
4. Starts reader thread:
   - Runs in background
   - Every 50ms: calls connection.receive()
   - If data: pushes to owner via WebSocket
   - Also broadcasts to all viewers via broadcastToViewers()
5. User types commands → ws.send() → ConnectionManager.send() → serialPort.writeBytes()
6. Device responds → reader thread gets data → ws.sendText() → browser displays

---

### 7. MULTI-USER (OWNER/VIEWER)

When two people connect to the same serial port:

Person 1: "connect serial COM3 9600"
  - Port is free → ConnectionManager creates SerialConnection
  - Person 1 becomes OWNER (can type commands)
  - Reader thread starts, pushes device output to Person 1

Person 2: "connect serial COM3 9600"
  - Port already in use → ConnectionManager sees existing connection
  - Person 2 becomes VIEWER (read-only)
  - Receives same device output via broadcastToViewers()
  - Cannot send commands (send() checks isOwner())
  - Gets message: "Device in use by Person1. Joined as viewer."

Person 1 disconnects:
  - ConnectionManager.disconnect() runs
  - Checks if viewers exist
  - Promotes first viewer (Person 2) to OWNER
  - Person 2 can now type commands
  - Gets message: "You are now the OWNER."

---

### 8. NETWORK ACCESS

The server shows its IP address in the dashboard header and sidebar.

From another laptop on the same WiFi:
  Open: http://SERVER_IP:8080/anchor
  Login with same credentials
  Connect to devices on the server's machine

Jetty binds to 0.0.0.0 (all network interfaces) so any device
on the network can access it.

---

### 9. SSH CONNECTION

Switch to SSH tab, enter host/port/user, click "Connect SSH"

What happens:
1. JavaScript sends: "connect ssh 192.168.1.1 22 root password"
2. TerminalWebSocket.handleSshConnect() parses it
3. ConnectionManager.connectSsh() creates SshConnection:
   - jsch.getSession(user, host, port)
   - session.setPassword(password)
   - session.connect(10000) - 10 second timeout
   - session.openChannel("shell")
   - Gets InputStream and OutputStream
4. Reader thread reads from SSH inputStream
5. User types → outputStream.write() → remote server
6. Polymorphism: same ConnectionManager.send()/receive() works
   because both SerialConnection and SshConnection extend Connection

---

### 10. DATABASE TEST

Click "Test DB" link in header

What happens:
1. GET /dbtest → DbTestServlet.doGet()
2. Class.forName("com.mysql.cj.jdbc.Driver") loads JDBC driver
3. DriverManager.getConnection() tries to connect to MySQL
4. If MySQL running: shows "Connected!"
5. If not running: shows "Connection failed" but proves driver loads
6. finally block always runs: conn.close() to prevent resource leak

---

## Architecture Summary

```
Browser (Laptop B)
  │
  ├── HTTP GET/POST ──→ AuthenticationFilter ──→ Servlets
  │                                                │
  │                                          LoginServlet
  │                                          RegisterServlet
  │                                          DeviceScannerServlet
  │                                          DbTestServlet
  │
  └── WebSocket ──→ TerminalWebSocket
                          │
                    ConnectionManager (Singleton)
                          │
                    ┌─────┴──────┐
                    │            │
              SerialConnection  SshConnection
              (jSerialComm)     (JSch)
                    │            │
              USB Device      Remote Server
              (on this laptop)
```

---

## Java Concepts Demonstrated

| Concept | File | How |
|---------|------|-----|
| Abstract class | Connection.java | Cannot instantiate, defines contract |
| Inheritance | SerialConnection, SshConnection | extends Connection |
| Polymorphism | ConnectionManager | Same send()/receive() for both types |
| Encapsulation | User.java | Private fields, no setPasswordHash() |
| Singleton | ConnectionManager | One instance, private constructor |
| Factory-like | ConnectionManager | Creates right Connection type |
| Observer-like | broadcastToViewers() | Push to all viewers |
| Filter pattern | AuthenticationFilter | Intercept before servlet |
| Thread safety | ConcurrentHashMap | Multiple WebSocket threads |
| Daemon threads | Reader thread | Auto-stops when server stops |
| SHA-256 | User.java | MessageDigest for password hashing |
| SecureRandom | User.java | Cryptographic salt generation |
| JDBC | DbTestServlet | Database connectivity |
| WebSocket | TerminalWebSocket | Real-time bidirectional |
| Servlet lifecycle | LoginServlet | init(), doGet(), doPost() |
| Session management | LoginServlet, Filter | HttpSession |
| REST API | DeviceScannerServlet | JSON response |
| Maven | pom.xml | Dependency management |
