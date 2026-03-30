**Anchor**

Sessions that don't drop

*Unified Terminal Interface for Embedded and Remote Systems*

Mini Project — AID3PR03A

Prepared by: 

| Ayush Kadali | 1032232229 |
| :---- | :---- |
| Krishna Khandelwal | 1032232078 |
| Mohammad Khot | 1032232680 |
| Yashoda Varma | 1032232146 |

# **Abstract**

Anchor is a web-based terminal application built with Java that solves a common frustration in embedded systems engineering: the need to juggle multiple separate tools just to communicate with devices. Today's engineers routinely switch between PuTTY for serial connections and separate SSH clients, losing time and debug context every time a device resets or a cable is unplugged.

Anchor replaces this fragmented workflow with a single, intelligent, browser-based interface. It automatically detects when a USB device is connected, maintains session history across browser refreshes, and handles both Serial and SSH communication through a unified control panel. Device configurations are stored in a database so engineers never have to re-enter baud rates or connection settings.

The system is built on Apache Tomcat using Java Servlets, JSP, and WebSockets, with MySQL for persistence and xterm.js for the browser terminal. This document provides a complete overview of the problem, proposed solution, system architecture, module design, and development plan.

# **1\. Executive Summary**

Anchor is a Java-based web application that unifies serial and SSH terminal communication into a single browser interface. It is designed for engineers working with embedded hardware and remote servers who are slowed down by the limitations of traditional terminal tools.

The core value proposition of Anchor is three-fold: automatic device detection (no manual port selection), persistent session management (history and settings survive refreshes), and a unified interface for both Serial and SSH connections.

**Technology Stack:**  Java Servlets  |  JSP  |  WebSockets  |  MySQL  |  Apache Tomcat

# **2\. Problem Statement**

## **2.1 What Engineers Deal With Today**

Engineers who work with embedded systems — microcontrollers, development boards, sensors, and remote servers — depend on terminal programs to communicate with their devices. The tools available today (PuTTY, TeraTerm, screen) were not designed for the fast-paced, multi-device workflows of modern engineering teams. This creates four recurring pain points:

| Problem | Impact | Frequency |
| ----- | ----- | ----- |
| Manual reconnection after USB disconnect or device reset | 30–60 seconds lost per occurrence | 10–20 times/day |
| Separate tools for Serial (PuTTY) and SSH (Terminal) | Context switching and inconsistent workflows | Continuous |
| No device configuration persistence | Repeated baud rate and port selection every session | Every session |
| Loss of terminal history on refresh or crash | Debug context permanently lost | Multiple times/day |

## **2.2 The Real Cost**

For a team of just five engineers, these inefficiencies add up to an estimated 2–3 hours of lost productivity per engineer per week. Beyond raw time, interruptions to a debugging session break concentration and often mean starting the investigation over from scratch.

## **2.3 How Existing Tools Fall Short**

No existing tool checks all the boxes that a modern engineering workflow requires:

| Tool | Auto-Reconnect | Web-Based | Unified Serial+SSH | Session Persistence | User Authentication |
| ----- | ----- | ----- | ----- | ----- | ----- |
| PuTTY | No | No | Partial | No | No |
| TeraTerm | No | No | No | No | No |
| Screen / Minicom | No | No | No | No | No |
| **Anchor (proposed)** | **Yes** | **Yes** | **Yes** | **Yes** | **Yes** |

Anchor is designed specifically to close this gap.

# **3\. Objectives**

## **3.1 Primary Objectives**

The following four objectives define the core functionality that Anchor must deliver:

1. Auto-discovery of USB serial devices — The system must detect a newly connected USB device within 2 seconds and offer to initiate communication automatically, without requiring the engineer to manually select a port.

2. Unified Serial and SSH interface — A single connection panel must support USB-UART serial communication and SSH remote access, using a shared abstraction layer underneath.

3. Persistent web-based terminal — The browser terminal must retain session state, command history, and connection settings across page refreshes and browser restarts.

4. Device profile system — Engineers must be able to save and reload complete device configurations (baud rate, parity, SSH credentials) with a single click.

## **3.2 Secondary Objectives**

5. Script execution — Support automated command sequences loaded from a file, reducing repetitive manual input during testing.

6. Session logging — Capture and export terminal output so engineers can review and share debug logs after a session ends.

# **4\. Scope**

## **4.1 What is Included**

| Module | Features Included |
| ----- | ----- |
| **Connection Engine** | Serial port communication (jSerialComm), SSH client (JSch) |
| **Device Manager** | USB device enumeration, auto-detection, connection state management |
| **Web Interface** | Browser terminal (xterm.js), dashboard, profile management |
| **Persistence Layer** | MySQL database storing device profiles and session metadata |
| **Session Manager** | WebSocket-based real-time I/O with history buffer |
| **Security Layer** | User authentication, session management, role-based access control |

## **4.2 What is Excluded**

The following are deliberately out of scope for the current project to maintain a focused, deliverable outcome:

* Telnet protocol support

* Mobile application development

* Multi-user real-time collaboration (deferred to a future version)

* Hardware flow control signals (RTS/CTS, DTR/DSR)

## **4.3 Platform Constraints**

Anchor runs on any machine with Java 11+ and targets modern browsers (Chrome 60+, Firefox 55+) that support the WebSocket standard. Serial communication is supported with standard USB-to-Serial adapters using the FTDI, CH340, or CP2102 chipsets.

# **5\. System Architecture**

## **5.1 Architectural Overview**

Anchor is structured across three layers, each with a distinct responsibility:

* Client Layer — The browser-facing interface, built with HTML/CSS, JSP pages, and the xterm.js terminal emulator.

* Application Layer — Apache Tomcat hosts the Java Servlets and WebSocket endpoints that handle business logic and real-time I/O.

* Hardware / External Layer — jSerialComm communicates with physical USB serial devices; JSch handles SSH sessions; MySQL stores all persistent data.

Communication between the browser and the server uses two channels: standard HTTP/REST for configuration and management operations, and WebSocket for the low-latency terminal I/O stream.

## **5.2 Class Design**

The connection model is built around an abstract base class, Connection, which defines the common interface — connect, disconnect, send, and receive. Two concrete subclasses implement protocol-specific behaviour:

* SerialConnection — wraps jSerialComm to manage a physical USB serial port, including baud rate, data bits, parity, and stop bits.

* SshConnection — wraps JSch to establish an authenticated SSH shell session, supporting both password and key-based authentication.

This design means that the rest of the system (the WebSocket handler, session manager, etc.) works identically regardless of whether the underlying connection is serial or SSH.

**Security Class Hierarchy:**

The authentication system is built around three core classes:

* User — A model class encapsulating user data (id, username, passwordHash, salt, email, role, createdAt). Demonstrates encapsulation with private fields and public getters.

* UserDAO — Data Access Object implementing CRUD operations for users using JDBC. Uses PreparedStatement for all queries to prevent SQL injection.

* AuthenticationService — Singleton service class that coordinates login, registration, and session validation. Delegates password hashing to PasswordHasher and database operations to UserDAO.

* PasswordHasher — Utility class using java.security.MessageDigest for SHA-256 hashing with per-user salt generation via SecureRandom.

* AuthenticationFilter — Implements javax.servlet.Filter to intercept requests and validate session tokens before allowing access to protected resources.

## **5.3 Database Schema**

The database is organised into four tables that cleanly separate concerns:

* device\_profiles — the master record for each saved device, storing its name and connection type (SERIAL or SSH).

* serial\_config — stores serial-specific settings (port pattern, baud rate, data bits, parity, stop bits) linked to a profile.

* ssh\_config — stores SSH-specific settings (hostname, port, username, authentication method) linked to a profile.

* session\_logs — records the start time, end time, and log file path for each terminal session, enabling historical review.

* users — stores user credentials including username, hashed password (SHA-256), email, role (ADMIN/USER), and account creation timestamp.

* user\_sessions — tracks active login sessions with session tokens, user ID, creation time, expiry time, and IP address for security auditing.

# **6\. Module Specifications**

## **Module 1 — Device Scanner Service**

Purpose: Continuously monitor the operating system for USB serial device changes and notify the rest of the application in real time.

How it works: A dedicated background thread polls the list of available COM/tty ports every 500 milliseconds. When a new port appears, it fires a DEVICE\_CONNECTED event. When a port disappears (cable pulled or device reset), it fires DEVICE\_DISCONNECTED. Listeners — such as the Connection Manager — respond to these events to auto-reconnect or clean up resources.

Key classes: DeviceScanner, DeviceEvent, DeviceListener.

## **Module 2 — Connection Manager**

Purpose: Act as the single point of control for all active connections in the application.

How it works: Implemented as a Singleton, the Connection Manager tracks every open connection. It uses a Factory method to create the right type of connection (serial or SSH) based on the profile being loaded. It also handles cleanup when connections are closed or when the Device Scanner reports a disconnection.

Key classes: ConnectionManager, ConnectionFactory, Connection.

## **Module 3 — WebSocket Terminal Handler**

Purpose: Provide the real-time, bidirectional channel between the browser terminal and the backend connection.

How it works: Terminal keystrokes typed in xterm.js are sent as binary WebSocket frames to the server, which forwards them to the open serial or SSH connection. Data coming back from the device is pushed to the browser the same way. Control operations (connect, disconnect, resize) use JSON-formatted messages on the same socket. The latency target for a keystroke round-trip is under 50 ms.

## **Module 4 — Profile Management**

Purpose: Allow engineers to save, load, update, and delete device configurations through the web interface.

How it works: A REST API exposed via Java Servlets provides CRUD operations on the device\_profiles, serial\_config, and ssh\_config database tables. The endpoints are /api/profiles (list and create) and /api/profiles/{id} (read, update, delete). All database access uses prepared statements to prevent SQL injection.

## **Module 5 — Authentication & Security**

Purpose: Secure access to Anchor through user authentication, session management, and role-based authorization.

How it works: The security module implements a complete authentication flow using Java Servlets and Filters. Users must register and log in before accessing any terminal functionality. Passwords are hashed using SHA-256 with salt before storage. Session tokens are generated using Java's SecureRandom and stored server-side with configurable expiry.

Key classes: AuthenticationServlet, AuthenticationFilter, UserDAO, PasswordHasher, SessionManager.

**Authentication Flow:**

1. **Registration** — User submits credentials via JSP form → AuthenticationServlet validates input → PasswordHasher generates salt and SHA-256 hash → UserDAO stores user in database.

2. **Login** — User submits credentials → AuthenticationServlet retrieves stored hash → PasswordHasher verifies password → SessionManager creates session token → Token stored in HttpSession and database.

3. **Request Authorization** — AuthenticationFilter intercepts all requests → Validates session token existence and expiry → Redirects to login page if invalid → Allows request to proceed if valid.

4. **Logout** — SessionManager invalidates token in database → HttpSession is destroyed → User redirected to login page.

**Security Features:**

| Feature | Implementation | Java Concept Demonstrated |
| ----- | ----- | ----- |
| Password Hashing | SHA-256 with per-user salt | java.security.MessageDigest |
| Session Tokens | 256-bit cryptographically secure random | java.security.SecureRandom |
| Request Filtering | Servlet Filter on protected endpoints | javax.servlet.Filter |
| SQL Injection Prevention | Prepared statements for all queries | java.sql.PreparedStatement |
| XSS Prevention | Input sanitization and output encoding | String manipulation, JSP escaping |
| Session Timeout | Configurable expiry (default 30 minutes) | Timer threads, HttpSession |
| Role-Based Access | ADMIN/USER roles with permission checks | Enum, conditional authorization |

#

# **7\. Technology Choices**

Each technology in the stack was selected for a specific reason, not as a default:

| Component | Technology | Why It Was Chosen |
| ----- | ----- | ----- |
| **Runtime** | Java 11+ | Cross-platform, strong threading, mature ecosystem |
| **Web Server** | Apache Tomcat 9 | Native Servlet/JSP support, WebSocket integration |
| **Serial I/O** | jSerialComm 2.x | Pure Java, no native dependencies, actively maintained |
| **SSH Client** | JSch 0.1.55 | Industry-standard library with a well-documented API |
| **Database** | MySQL 8.0 | Reliable, SQL-compliant, supported via JDBC |
| **Browser Terminal** | xterm.js | De-facto standard for web terminals, TypeScript-based |
| **Build Tool** | Maven | Dependency management and reproducible builds |

# 

# **8\. Risk Analysis**

Five key risks have been identified, assessed, and planned for:

| Risk | Probability | Impact | Mitigation Strategy |
| ----- | ----- | ----- | ----- |
| USB driver compatibility across operating systems | Medium | High | Abstract hardware layer; tested on Windows, Linux, and macOS |
| WebSocket drops on network instability | Medium | Medium | Implement reconnection logic with exponential backoff |
| Thread synchronization bugs in Device Scanner | Medium | High | Thread-safe collections and comprehensive unit testing |
| Browser compatibility with xterm.js | Low | Medium | Target modern browsers only; provide compatibility matrix |
| SSH key authentication complexity | Low | Medium | Start with password auth in Phase 1; add key auth in Phase 2 |
| Session hijacking attacks | Medium | High | Secure token generation, HTTPS enforcement, session expiry |
| Brute force login attempts | Medium | Medium | Account lockout after failed attempts, rate limiting |
| Credential storage compromise | Low | High | Salted SHA-256 hashing, never store plaintext passwords |

# **9\. Development Milestones**

Development is split into two phases. Phase 1 establishes core functionality; Phase 2 adds persistence, automation, and logging.

| Phase | Milestone | Deliverables |
| ----- | ----- | ----- |
| **Phase 1** | Serial Terminal MVP | Working serial connection via browser with manual port selection |
| **Phase 1** | User Authentication | Login/registration system with password hashing and session management |
| **Phase 1** | Auto-Detection | Device Scanner thread with automatic connection prompts |
| **Phase 1** | SSH Integration | SSH connection support through the same interface |
| **Phase 2** | Profile System | Database integration for saving and loading configurations |
| **Phase 2** | Role-Based Access Control | Admin panel for user management, permission-based feature access |
| **Phase 2** | Script Runner | File-based command automation for repeated tasks |
| **Phase 2** | Session Logging | Terminal output capture and export for debug analysis |

# 

# **10\. Deliverables**

Upon project completion, the following artefacts will be submitted:

| Deliverable | Format | Description |
| ----- | ----- | ----- |
| **Source Code** | Git Repository | Complete Java project with Maven build configuration |
| **Web Archive** | .war file | Deployable web application archive for Apache Tomcat |
| **Database Scripts** | .sql files | Schema creation scripts and sample seed data |
| **User Manual** | PDF/Markdown | Step-by-step installation guide and usage instructions |
| **API Documentation** | Javadoc | Auto-generated API reference for all public classes |
| **Test Report** | PDF | Unit test coverage results and quality summary |

# **11\. Course Outcome Mapping**

Anchor satisfies all four course outcomes for AID3PR03A through concrete implementation choices:

| Course Outcome | How Anchor Demonstrates It |
| ----- | ----- |
| **CO1: Core OOP Concepts** | Abstract Connection class with SerialConnection and SshConnection subclasses — demonstrates inheritance, polymorphism, and encapsulation. UserDAO and SessionManager classes demonstrate encapsulation of data access logic. |
| **CO2: Multithreading & Exception Handling** | DeviceScanner background thread, per-connection I/O threads, session timeout monitoring thread, and proper exception propagation for hardware and authentication errors |
| **CO3: Servlets & JSP** | Dashboard JSP pages, REST API Servlets for profile management, WebSocket endpoint for the terminal, AuthenticationServlet for login/registration, and AuthenticationFilter for request interception |
| **CO4: Database Connectivity** | MySQL integration for profile storage and user management using JDBC, connection pooling, and prepared statements for SQL injection prevention |
| **CO5: Java Security APIs** | Password hashing using java.security.MessageDigest (SHA-256), secure token generation using java.security.SecureRandom, and session management with HttpSession |

# 

# **12\. Conclusion**

Anchor addresses a genuine, measurable inefficiency in embedded systems engineering. By replacing a fragmented collection of desktop tools with a unified, intelligent, browser-based interface, it has the potential to save engineering teams hours of lost productivity each week while also improving the quality of debugging sessions.

From a technical standpoint, the project provides strong coverage of the AID3PR03A course outcomes — applying OOP principles through a well-structured class hierarchy, demonstrating multithreading and exception handling in the Device Scanner, building a web application using Servlets and JSP, and integrating a relational database through JDBC.

The phased development plan ensures that a working core product is delivered in Phase 1, with more advanced features (profiles, scripting, logging) added in Phase 2\. This approach reduces risk and ensures measurable progress at every stage.

# **13\. References**

7. jSerialComm Library Documentation — https://fazecast.github.io/jSerialComm/

8. JSch (Java Secure Channel) — http://www.jcraft.com/jsch/

9. xterm.js Documentation — https://xtermjs.org/docs/

10. Java WebSocket API (JSR 356\) — https://jcp.org/en/jsr/detail?id=356

11. Apache Tomcat 9 Documentation — https://tomcat.apache.org/tomcat-9.0-doc/