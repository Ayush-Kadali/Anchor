# Installation & Setup

## Prerequisites

Before you begin, make sure you have:

- **Java 11 or higher** - Check with `java -version`
- **Apache Maven 3.6+** - Check with `mvn -version`
- **Git** - to clone the repository

### Installing Prerequisites

**macOS (with Homebrew):**
```bash
brew install openjdk@11 maven git
```

**Ubuntu / Debian:**
```bash
sudo apt install openjdk-11-jdk maven git
```

**Windows:**
- Java: Download from https://adoptium.net
- Maven: Download from https://maven.apache.org/download.cgi
- Git: Download from https://git-scm.com

Set JAVA_HOME environment variable to your Java installation path.

## Clone the Repository

```bash
git clone https://github.com/Ayush-Kadali/Anchor.git
cd Anchor
```

## Build the Project

```bash
cd anchor
mvn clean package
```

This will:
1. Download all dependencies from Maven Central
2. Compile all Java source files
3. Package into `target/anchor.war` (Web Application Archive)

First build takes a few minutes as Maven downloads dependencies. Subsequent builds are fast.

## Run the Project

### Option 1: Run with Embedded Jetty (Easiest)

From the `anchor` directory:

```bash
mvn jetty:run
```

This starts an embedded Jetty server. Open http://localhost:8080/anchor in your browser.

Press `Ctrl+C` to stop the server.

### Option 2: Deploy to Apache Tomcat

1. Download and install Apache Tomcat 9+ from https://tomcat.apache.org
2. Copy the WAR file to Tomcat's webapps directory:
   ```bash
   cp target/anchor.war /path/to/tomcat/webapps/
   ```
3. Start Tomcat:
   ```bash
   /path/to/tomcat/bin/catalina.sh run
   ```
4. Open http://localhost:8080/anchor

## Access the Application

### From the same machine
Open: http://localhost:8080/anchor

### From other devices on the same network
1. Find the server's IP address:
   - macOS / Linux: `ifconfig | grep "inet "`
   - Windows: `ipconfig`
2. On any other device (laptop, phone, tablet) connected to the same WiFi, open:
   `http://SERVER_IP:8080/anchor`

## Demo Credentials

| Username | Password | Role |
|----------|----------|------|
| admin | admin123 | ADMIN |
| demo | demo123 | USER |

Or register a new account via the registration page.

## Quick Start Guide

1. **Login** with admin / admin123
2. **Scan for devices** - click "Scan for Devices" (shows any connected USB serial devices)
3. **Connect WebSocket** - click "Connect WebSocket" to start the terminal
4. **Connect to a device**:
   - Serial: type `connect serial /dev/ttyUSB0 9600` (adjust port/baud)
   - SSH: type `connect ssh 127.0.0.1 22 username password`
5. Type commands in the terminal - they are sent to the device
6. **Multi-user**: open a second browser tab, login as demo, click Join on the active connection
7. **Transfer ownership**: owner types `give demo` to give control to user demo

## Terminal Commands

| Command | What It Does |
|---------|-------------|
| `connect serial <port> <baud>` | Open serial connection |
| `connect ssh <host> <port> <user> <pass>` | Open SSH connection |
| `join <connectionId>` | Join an existing active connection |
| `list` | List all active connections |
| `give` | Transfer ownership to the first viewer |
| `give <username>` | Transfer ownership to a specific user |
| `status` | Show your current connection and role |
| `disconnect` | Close the device connection |

## Testing SSH to localhost (macOS)

To test SSH without any remote server, you can SSH to your own machine:

1. Enable Remote Login:
   - **System Settings** → **General** → **Sharing** → **Remote Login** → On
2. In the Anchor terminal:
   ```
   connect ssh 127.0.0.1 22 YOUR_MAC_USERNAME YOUR_MAC_PASSWORD
   ```
3. Run commands: `ls`, `whoami`, `pwd`

## MySQL Setup (Optional - Future Feature)

Currently users are stored in memory. To use MySQL (when UserDAO is implemented):

1. Install MySQL 8:
   ```bash
   brew install mysql          # macOS
   sudo apt install mysql-server  # Ubuntu
   ```

2. Start MySQL:
   ```bash
   brew services start mysql      # macOS
   sudo systemctl start mysql     # Linux
   ```

3. Create the database:
   ```sql
   CREATE DATABASE anchor_db;
   USE anchor_db;

   CREATE TABLE users (
       id INT AUTO_INCREMENT PRIMARY KEY,
       username VARCHAR(50) UNIQUE NOT NULL,
       password_hash VARCHAR(255) NOT NULL,
       salt VARCHAR(255) NOT NULL,
       email VARCHAR(100),
       role VARCHAR(20) DEFAULT 'USER',
       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );
   ```

4. Update database credentials in `DbTestServlet.java`:
   ```java
   private static final String DB_URL = "jdbc:mysql://localhost:3306/anchor_db";
   private static final String DB_USER = "root";
   private static final String DB_PASS = "your_password";
   ```

## Project Structure

```
Anchor/
├── anchor/                              Main project directory
│   ├── pom.xml                          Maven configuration
│   ├── README.md                        Project readme (this file)
│   └── src/main/
│       ├── java/com/anchor/
│       │   ├── models/                  Data classes
│       │   │   ├── Connection.java      Abstract base class
│       │   │   ├── SerialConnection.java  Serial (jSerialComm)
│       │   │   ├── SshConnection.java   SSH (JSch)
│       │   │   └── User.java            User with SHA-256 hashing
│       │   └── servlets/                HTTP request handlers
│       │       ├── AuthenticationFilter.java
│       │       ├── ConnectionManager.java    Singleton, multi-user
│       │       ├── TerminalWebSocket.java    @ServerEndpoint
│       │       ├── DeviceScannerServlet.java REST API
│       │       ├── LoginServlet.java
│       │       ├── LogoutServlet.java
│       │       ├── RegisterServlet.java
│       │       ├── ActiveConnectionsServlet.java
│       │       ├── StatusServlet.java
│       │       └── DbTestServlet.java
│       └── webapp/
│           ├── WEB-INF/web.xml          URL routing
│           ├── css/style.css
│           ├── dashboard.jsp
│           ├── index.jsp
│           ├── login.jsp
│           └── register.jsp
├── Anchor_Architecture.html             Visual architecture diagrams
├── Anchor_Synopsis.md                   Project synopsis
├── PROTOTYPE_WALKTHROUGH.md             Walkthrough notes
├── README.md                            Project readme (root)
└── INSTALL.md                           This file
```

## Common Issues

### "Port 8080 already in use"
Another application (or an old server instance) is using port 8080.

Kill it:
```bash
# macOS / Linux
lsof -ti:8080 | xargs kill -9

# Windows
netstat -ano | findstr :8080
taskkill /PID <pid> /F
```

### "Cannot connect from other device"
- Both devices must be on the same WiFi network
- Firewall might be blocking port 8080 - add an exception
- Make sure you're using the server's IP, not `localhost` or `127.0.0.1`

### "Serial port access denied"
On macOS/Linux, serial ports may need permissions:
```bash
# macOS - usually works out of the box
# Linux - add user to dialout group
sudo usermod -a -G dialout $USER
# Then log out and back in
```

### "SSH connection refused"
Make sure SSH is enabled on the target machine:
- **macOS**: System Settings → Sharing → Remote Login
- **Linux**: `sudo systemctl start ssh`
- **Windows**: Install OpenSSH Server in Optional Features

## Development

### Rebuild after changes
```bash
mvn clean package
mvn jetty:run
```

### Run tests
```bash
mvn test
```
(Currently no tests written - planned for future)

### View build output
The compiled WAR file is at: `anchor/target/anchor.war`

## Support

For issues or questions, contact the team listed in the main README.
