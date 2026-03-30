# Anchor Prototype - Walkthrough Notes

Read this before the demo. It explains what each part does and what to say.

## How to Start the Demo

```bash
cd anchor
mvn jetty:run
```
Open: http://localhost:8080/anchor

## Demo Script (Follow This Order)

---

### 1. LOGIN PAGE

**What opens:** A simple login form. Nothing fancy.

**What to show:**
- The page is `login.jsp` - a JSP file (HTML with Java inside)
- There's a link to "Create new account" (registration)
- Demo credentials are shown: admin / admin123

**What to explain:**
> "When you open the app, `index.jsp` runs first. It checks if you have a session.
> Since you don't, it redirects to `login.jsp`. This redirect is done by the
> AuthenticationFilter - a Servlet Filter that intercepts ALL requests and checks
> if you're logged in."

**Show the code:** Open `AuthenticationFilter.java`
> "See here - `doFilter()` runs before every request. It checks the URI - if it's
> the login page, CSS, or an API call, it lets it through. For everything else,
> it checks `session.getAttribute("user")`. If null, you get redirected to login."

---

### 2. REGISTRATION (Create New Account)

**Click:** "Create new account" link

**What to show:**
- Simple form: username, password, confirm password
- Register a new user like "testuser" / "test123"

**What to explain:**
> "This form POSTs to `/register` which maps to `RegisterServlet`.
> The servlet validates: are fields empty? Do passwords match? Is username taken?
> Then it creates a `new User()` - and THIS is where the hashing happens."

**Show the code:** Open `User.java` constructor
> "Look at the constructor. When we create `new User("testuser", "test123", "USER")`,
> it calls `generateSalt()` which uses `SecureRandom` to make 16 random bytes,
> then `hashPassword()` which takes the password + salt, runs it through
> `MessageDigest.getInstance("SHA-256")`, and stores only the hash.
> The plain password is NEVER stored anywhere."

**After registering:** You get redirected to login with "Registration successful!"

---

### 3. LOGIN WITH NEW USER

**Login with:** testuser / test123

**What to explain:**
> "The form POSTs to `/login` → `LoginServlet.doPost()`.
> It gets the username from `request.getParameter("username")`.
> Looks up the User object from our HashMap.
> Calls `user.verifyPassword("test123")` which:
> 1. Gets the stored salt
> 2. Hashes the entered password with that salt
> 3. Compares with stored hash
> 4. If they match → create HttpSession, store username and role
> 5. `session.setMaxInactiveInterval(1800)` = 30 minute timeout
> 6. Redirect to dashboard"

---

### 4. DASHBOARD - DEVICE SCANNING

**What shows:** Dashboard with device list on left, terminal on right.

**What to show:**
- Session info in header (username, role, session ID)
- "Scan for Devices" button → click it

**What to explain:**
> "The device list calls `fetch('api/devices')` - that's JavaScript making an AJAX
> request to our `DeviceScannerServlet`. The servlet calls
> `SerialPort.getCommPorts()` from the jSerialComm library. This talks to the
> operating system to find all connected USB serial ports. It builds a JSON
> response and sends it back. The JavaScript parses that JSON and updates the page."

**Show the code:** Open `DeviceScannerServlet.java`
> "See - `response.setContentType("application/json")` tells the browser this is
> JSON, not HTML. We loop through all ports, build the JSON string with
> StringBuilder, and write it with `PrintWriter`. The `escapeJson()` method
> prevents injection attacks."

**If no devices found:**
> "That's normal - there's no USB serial device plugged in right now.
> The servlet still works - it returns `{"devices":[], "count":0}`.
> If we plug in an Arduino and scan again, it would show up."

---

### 5. WEBSOCKET TERMINAL

**Click:** "Connect WebSocket" button

**What shows:** Terminal connects, shows welcome message.

**What to do:** Type something in the input box, press Enter.

**What to explain:**
> "This is a WebSocket connection - different from HTTP. HTTP is request-response:
> browser asks, server answers. WebSocket is bidirectional: both sides can send
> anytime. We need this for a terminal because:
> - When I type a key, it needs to go to the device instantly
> - When the device sends output, it needs to appear in the browser instantly
> - HTTP would be too slow - you'd have to keep polling
>
> Our `TerminalWebSocket.java` uses the `@ServerEndpoint("/terminal")` annotation.
> When the browser connects, `onOpen()` fires. When the browser sends text,
> `onMessage()` fires. Right now it just echoes back - in the full version,
> this is where we'd forward to `SerialConnection.send()` or `SshConnection.send()`."

**Show the code:** Open `TerminalWebSocket.java`
> "See the annotations: `@OnOpen`, `@OnMessage`, `@OnClose`, `@OnError`.
> These are from JSR 356 - Java's WebSocket API. The container (Jetty/Tomcat)
> calls these methods automatically when events happen."

---

### 6. SERIAL / SSH CONNECTION ATTEMPT

**Click:** Serial tab → enter any port name → click "Connect Serial"

**What shows:** Terminal shows what Java code WOULD execute.

**What to explain:**
> "Right now this shows what the connection code does step by step.
> The actual `SerialConnection.java` is written and compiles - it extends our
> abstract `Connection` class. Let me show you the class hierarchy."

**Show the code:** Open `Connection.java`, `SerialConnection.java`, `SshConnection.java`
> "Connection is abstract - it defines connect(), disconnect(), send(), receive().
> You can't do `new Connection()` - you MUST use a subclass.
> SerialConnection uses jSerialComm to talk to USB ports.
> SshConnection uses JSch to talk to SSH servers.
> Both override the same methods with different implementations.
> This is polymorphism - same method name, different behavior."

---

### 7. DATABASE TEST

**Click:** "Test DB Connection" link in header

**What shows:** Page showing JDBC driver loaded, connection attempt result.

**What to explain:**
> "This calls `DbTestServlet`. It does three things:
> 1. `Class.forName("com.mysql.cj.jdbc.Driver")` - loads the MySQL JDBC driver
> 2. `DriverManager.getConnection(url, user, pass)` - tries to connect
> 3. Handles the result in try-catch-finally
>
> The driver loads successfully because we have mysql-connector in pom.xml.
> The connection fails because MySQL isn't running here - that's expected.
> The point is: the JDBC code works, the driver loads, and we handle errors
> properly with try-catch-finally. The finally block always closes the connection."

---

### 8. LOGOUT

**Click:** Logout link

**What to explain:**
> "`LogoutServlet` calls `session.invalidate()` - this destroys the session
> on the server. The session cookie in the browser becomes invalid.
> Then it redirects to login page."

**Try accessing dashboard directly:** Type /anchor/dashboard.jsp in URL bar
> "See - it redirects to login. That's the AuthenticationFilter working.
> It checked the session, found no user, and redirected."

---

### 9. WEB.XML WALKTHROUGH

**Open:** `web.xml`

> "This is the deployment descriptor. It tells the server:
> - Which URLs go to which servlets (servlet-mapping)
> - Which filters run on which URLs (filter-mapping)
> - Session timeout (30 minutes)
> - Welcome file (index.jsp)
>
> For example: `<url-pattern>/login</url-pattern>` → `LoginServlet`
> means when someone visits /login, Java runs LoginServlet.
>
> The filter has `<url-pattern>/*</url-pattern>` which means
> it runs on EVERY request."

---

### 10. POM.XML WALKTHROUGH

**Open:** `pom.xml`

> "This is Maven's config. It lists every library we need:
> - `javax.servlet-api` (scope: provided) - Tomcat has this, don't include in WAR
> - `jSerialComm` - talks to USB serial ports
> - `jsch` - SSH connections
> - `mysql-connector-java` - JDBC driver
> - `javax.websocket-api` (scope: provided) - WebSocket support
>
> When we run `mvn package`, Maven downloads all these from Maven Central,
> compiles our Java code, and creates anchor.war - a deployable archive."

---

## What Each File Does (Quick Reference)

| File | Purpose | Java Concepts |
|------|---------|---------------|
| `Connection.java` | Abstract base for connections | abstract class, abstract methods, protected |
| `SerialConnection.java` | USB serial via jSerialComm | extends, @Override, library integration |
| `SshConnection.java` | SSH via JSch | extends, InputStream/OutputStream |
| `User.java` | User model + password hashing | encapsulation, MessageDigest, SecureRandom |
| `LoginServlet.java` | Login handling | HttpServlet, doGet/doPost, HttpSession |
| `LogoutServlet.java` | Session destruction | session.invalidate() |
| `RegisterServlet.java` | User registration | Input validation, redirect |
| `AuthenticationFilter.java` | Security guard on all requests | javax.servlet.Filter, FilterChain |
| `DeviceScannerServlet.java` | REST API for device listing | JSON response, jSerialComm |
| `TerminalWebSocket.java` | Real-time terminal | @ServerEndpoint, WebSocket API |
| `DbTestServlet.java` | JDBC connection test | DriverManager, try-catch-finally |
| `web.xml` | URL → Servlet mapping | Deployment descriptor |
| `pom.xml` | Dependencies + build | Maven |
| `index.jsp` | Entry redirect | Session check |
| `login.jsp` | Login form | JSP scriptlets, expressions |
| `register.jsp` | Registration form | Form handling |
| `dashboard.jsp` | Main dashboard | Session data, JavaScript, AJAX |

## If Professor Asks...

**"Why in-memory storage, not database?"**
> "This is the prototype. We have the JDBC code ready (DbTestServlet proves it works).
> The User class is designed so we just swap HashMap for a UserDAO class that uses
> PreparedStatement. The User object stays the same."

**"Why is the terminal just echoing?"**
> "The WebSocket endpoint works. The Connection classes work. The next step is
> bridging them - when WebSocket receives text, forward to Connection.send(),
> and when Connection.receive() gets data, push through WebSocket to browser."

**"Why no HTTPS?"**
> "Development environment. In production we'd add an SSL certificate and use
> a Servlet Filter to redirect HTTP to HTTPS. The Filter pattern is the same
> as our AuthenticationFilter."

**"What's the point if minicom already exists?"**
> "minicom is one device, one terminal, one machine. Anchor is multi-device,
> web-accessible, authenticated, with session history. The real value is in
> lab environments where a professor controls access to shared hardware."
