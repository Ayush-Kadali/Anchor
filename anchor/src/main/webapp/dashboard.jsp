<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    /*
     * Dashboard - Main application page
     *
     * First thing: check if user is logged in.
     * session is a JSP implicit object (HttpSession).
     * getAttribute("user") returns what LoginServlet stored.
     * If null, user is not logged in - redirect to login page.
     */
    if (session.getAttribute("user") == null) {
        response.sendRedirect("login.jsp");
        return; // important: stop processing rest of page
    }
    // cast from Object to String - getAttribute returns Object
    String username = (String) session.getAttribute("user");
    String role = (String) session.getAttribute("role");
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Anchor - Dashboard</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 10px; background: #f5f5f5; }
        .header { background: #fff; padding: 10px; border: 1px solid #ccc; margin-bottom: 10px;
                   display: flex; justify-content: space-between; align-items: center; }
        .main { display: flex; gap: 10px; }
        .sidebar { width: 300px; background: #fff; border: 1px solid #ccc; padding: 10px; }
        .content { flex: 1; }
        .device-item { padding: 8px; margin: 5px 0; background: #f0f0f0; border: 1px solid #ddd; cursor: pointer; }
        .device-item:hover { background: #e0e0e0; }
        .terminal-box { background: #1e1e1e; color: #0f0; padding: 10px; font-family: monospace;
                         font-size: 13px; height: 350px; overflow-y: auto; white-space: pre-wrap;
                         border: 1px solid #ccc; }
        .terminal-input { width: 100%; padding: 8px; font-family: monospace; margin-top: 5px;
                           background: #2d2d2d; color: #0f0; border: 1px solid #555; }
        .status-bar { background: #fff; border: 1px solid #ccc; padding: 8px; margin-top: 10px; font-size: 0.85em; }
        button { padding: 6px 12px; cursor: pointer; margin: 2px; }
        .section { background: #fff; border: 1px solid #ccc; padding: 10px; margin-bottom: 10px; }
        h3 { margin-top: 0; border-bottom: 1px solid #eee; padding-bottom: 5px; }
        a { color: #007bff; }
        .tab { display: inline-block; padding: 5px 15px; border: 1px solid #ccc; cursor: pointer; background: #f5f5f5; }
        .tab.active { background: #007bff; color: #fff; }
        .info-box { background: #f0f0f0; padding: 8px; margin-top: 10px; font-size: 0.85em;
                     border: 1px solid #ddd; }
    </style>
</head>
<body>

    <!-- HEADER -->
    <div class="header">
        <div>
            <b>Anchor</b> - Prototype
            &nbsp;|&nbsp;
            Logged in as: <b><%= username %></b> (<%= role %>)
            &nbsp;|&nbsp;
            Server: <b><%= request.getLocalAddr() %>:<%= request.getLocalPort() %></b>
        </div>
        <div>
            <a href="dbtest">Test DB</a> &nbsp;
            <a href="logout" style="color:red;">Logout</a>
        </div>
    </div>

    <div class="main">

        <!-- ==================== SIDEBAR ==================== -->
        <div class="sidebar">

            <!-- Device Scanner -->
            <div class="section">
                <h3>Detected Devices</h3>
                <div id="deviceList"><i>Click scan to detect...</i></div>
                <br>
                <button onclick="scanDevices()">Scan for Devices</button>
            </div>

            <!-- Quick Connect -->
            <div class="section">
                <h3>Quick Connect</h3>
                <div>
                    <span class="tab active" onclick="showTab('serial', this)">Serial</span>
                    <span class="tab" onclick="showTab('ssh', this)">SSH</span>
                </div>
                <br>

                <!-- Serial Config -->
                <div id="serial-config">
                    <label>Port:</label><br>
                    <input type="text" id="serialPort" placeholder="/dev/ttyUSB0 or COM3"
                           style="width:100%; padding:5px;"><br><br>
                    <label>Baud Rate:</label><br>
                    <select id="baudRate" style="width:100%; padding:5px;">
                        <option>9600</option>
                        <option>115200</option>
                        <option>19200</option>
                    </select><br><br>
                    <button onclick="connectSerial()">Connect Serial</button>
                    <button onclick="sendDisconnect()">Disconnect</button>
                </div>

                <!-- SSH Config -->
                <div id="ssh-config" style="display:none;">
                    <label>Host:</label><br>
                    <input type="text" id="sshHost" placeholder="192.168.1.1"
                           style="width:100%; padding:5px;"><br><br>
                    <label>Port:</label><br>
                    <input type="text" id="sshPort" value="22"
                           style="width:100%; padding:5px;"><br><br>
                    <label>Username:</label><br>
                    <input type="text" id="sshUser" placeholder="root"
                           style="width:100%; padding:5px;"><br><br>
                    <button onclick="connectSsh()">Connect SSH</button>
                    <button onclick="sendDisconnect()">Disconnect</button>
                </div>
            </div>

            <!-- Network Info -->
            <div class="section">
                <h3>Network Access</h3>
                <p style="font-size:0.85em;">
                    Other devices on the network can access this server at:<br>
                    <b>http://<%= request.getLocalAddr() %>:<%= request.getLocalPort() %>/anchor</b>
                </p>
                <p style="font-size:0.8em; color:#666;">
                    Multiple users can connect. First user to a device
                    becomes owner (read/write). Others become viewers (read-only).
                </p>
            </div>
        </div>

        <!-- ==================== MAIN CONTENT ==================== -->
        <div class="content">

            <!-- Terminal -->
            <div class="section">
                <h3>
                    Terminal
                    <span id="wsStatus" style="font-size:0.8em; color:#999; font-weight:normal;">
                        [Disconnected]
                    </span>
                    <span id="deviceRole" style="font-size:0.8em; font-weight:normal;"></span>
                </h3>
                <div class="terminal-box" id="terminalOutput">Welcome to Anchor Terminal.

Step 1: Click "Connect WebSocket" to connect to the server.
Step 2: Use "Scan for Devices" to find serial ports.
Step 3: Click a device or enter port manually, then "Connect Serial".

Or type commands directly in the input box below:
  connect serial COM3 9600
  connect ssh 192.168.1.1 22 root password
  disconnect
  status
</div>
                <input type="text" class="terminal-input" id="terminalInput"
                       placeholder="Type command here and press Enter..."
                       onkeypress="handleTerminalKey(event)" disabled>
                <br><br>
                <button onclick="connectWebSocket()">Connect WebSocket</button>
                <button onclick="disconnectWebSocket()">Disconnect WS</button>
                <button onclick="sendStatus()">Status</button>
                <button onclick="clearTerminal()">Clear</button>
            </div>

            <!-- Status Bar -->
            <div class="status-bar">
                <b>Prototype Status:</b>
                Login [done] |
                Registration [done] |
                Auth Filter [done] |
                Device Scan [done] |
                WebSocket Terminal [done] |
                Serial Connection [done] |
                SSH Connection [done] |
                Multi-user (Owner/Viewer) [done] |
                Network Access [done] |
                JDBC Test [done] |
                ConnectionManager [done] |
                xterm.js [wip] |
                MySQL Storage [wip]
                <br>
                <span style="font-size:0.8em;">[done] = Working &nbsp; [wip] = In Progress</span>
            </div>
        </div>
    </div>

    <script>
        /*
         * ================================================================
         * JAVASCRIPT - Client-side code
         * ================================================================
         *
         * This runs in the BROWSER, not on the server.
         * It communicates with the server via:
         *   1. fetch() for AJAX calls (device scanning)
         *   2. WebSocket for real-time terminal I/O
         *
         * The server-side counterparts are:
         *   fetch('api/devices')    → DeviceScannerServlet.java
         *   new WebSocket('/terminal') → TerminalWebSocket.java
         */

        var ws = null;
        var currentUsername = '<%= username %>';

        // ==================== WEBSOCKET ====================
        //
        // WebSocket = persistent bidirectional connection
        // Unlike HTTP (request → response), WebSocket stays open
        // and either side can send data at any time.
        //
        // We need this for the terminal because:
        // - User types a key → must reach device instantly
        // - Device sends output → must appear in browser instantly
        // - HTTP would require polling (wasteful and slow)

        function connectWebSocket() {
            // build URL: ws://hostname:port/anchor/terminal
            // this maps to @ServerEndpoint("/terminal") in TerminalWebSocket.java
            var wsUrl = 'ws://' + window.location.host + '<%= request.getContextPath() %>/terminal';
            appendTerminal('Connecting to: ' + wsUrl + '\n');

            try {
                ws = new WebSocket(wsUrl);

                // called when connection opens
                // server-side: @OnOpen in TerminalWebSocket.java fires
                ws.onopen = function() {
                    document.getElementById('wsStatus').textContent = '[Connected]';
                    document.getElementById('wsStatus').style.color = 'green';
                    document.getElementById('terminalInput').disabled = false;
                    document.getElementById('terminalInput').focus();

                    // tell the server our username so it can track owner/viewer
                    ws.send('user ' + currentUsername);
                };

                // called when server sends data
                // server-side: session.getBasicRemote().sendText() in TerminalWebSocket.java
                ws.onmessage = function(event) {
                    appendTerminal(event.data);

                    // update role display if message contains role info
                    if (event.data.indexOf('OWNER') !== -1) {
                        document.getElementById('deviceRole').textContent = '[OWNER - can type]';
                        document.getElementById('deviceRole').style.color = 'green';
                    } else if (event.data.indexOf('VIEWER') !== -1) {
                        document.getElementById('deviceRole').textContent = '[VIEWER - read only]';
                        document.getElementById('deviceRole').style.color = 'orange';
                    }
                };

                // called when connection closes
                // server-side: @OnClose fires, ConnectionManager.disconnect() cleans up
                ws.onclose = function() {
                    document.getElementById('wsStatus').textContent = '[Disconnected]';
                    document.getElementById('wsStatus').style.color = '#999';
                    document.getElementById('deviceRole').textContent = '';
                    document.getElementById('terminalInput').disabled = true;
                    appendTerminal('\n--- WebSocket disconnected ---\n');
                };

                ws.onerror = function() {
                    appendTerminal('\nWebSocket error. Is the server running?\n');
                };
            } catch(e) {
                appendTerminal('Failed to connect: ' + e + '\n');
            }
        }

        function disconnectWebSocket() {
            if (ws) { ws.close(); ws = null; }
        }

        // handle Enter key in terminal input
        function handleTerminalKey(event) {
            if (event.key === 'Enter') {
                var input = document.getElementById('terminalInput');
                var cmd = input.value;
                if (ws && ws.readyState === WebSocket.OPEN && cmd.length > 0) {
                    // send to server → TerminalWebSocket.onMessage()
                    ws.send(cmd);
                }
                input.value = '';
            }
        }

        // append text to terminal display
        function appendTerminal(text) {
            var terminal = document.getElementById('terminalOutput');
            terminal.textContent += text;
            // auto-scroll to bottom
            terminal.scrollTop = terminal.scrollHeight;
        }

        function clearTerminal() {
            document.getElementById('terminalOutput').textContent = '';
        }

        // ==================== DEVICE SCANNING ====================
        //
        // Uses fetch() to make an AJAX call to DeviceScannerServlet.
        // AJAX = Asynchronous JavaScript and XML (we use JSON, not XML)
        // Page does NOT reload - only the device list div updates.
        //
        // Server-side: DeviceScannerServlet calls SerialPort.getCommPorts()
        // from jSerialComm library to list all USB serial ports.

        function scanDevices() {
            var deviceList = document.getElementById('deviceList');
            deviceList.innerHTML = '<i>Scanning...</i>';

            // HTTP GET to /api/devices → DeviceScannerServlet.doGet()
            fetch('api/devices')
                .then(function(response) { return response.json(); })
                .then(function(data) {
                    if (data.devices && data.devices.length > 0) {
                        var html = '';
                        for (var i = 0; i < data.devices.length; i++) {
                            var d = data.devices[i];
                            // onclick fills in the serial port field
                            html += '<div class="device-item" onclick="selectDevice(\'' + d.port + '\')">';
                            html += '<b>' + d.name + '</b><br>';
                            html += '<small>' + d.port + ' - ' + d.description + '</small>';
                            html += '</div>';
                        }
                        deviceList.innerHTML = html;
                    } else {
                        deviceList.innerHTML = '<i>No serial devices found.<br>Connect a USB device and scan again.</i>';
                    }
                    deviceList.innerHTML += '<br><small>Found ' + data.count + ' port(s)</small>';
                })
                .catch(function(err) {
                    deviceList.innerHTML = '<i style="color:red;">Scan failed: ' + err + '</i>';
                });
        }

        // clicking a device fills in the port field
        function selectDevice(port) {
            document.getElementById('serialPort').value = port;
        }

        // ==================== CONNECTION COMMANDS ====================
        //
        // These send commands to TerminalWebSocket via WebSocket.
        // The server parses the command and uses ConnectionManager
        // to create/manage actual hardware connections.
        //
        // If someone else is already using the device,
        // ConnectionManager adds you as a VIEWER (read-only).
        // When the owner disconnects, you get promoted to OWNER.

        function connectSerial() {
            var port = document.getElementById('serialPort').value;
            var baud = document.getElementById('baudRate').value;
            if (!port) { alert('Enter a port name'); return; }

            ensureWebSocket(function() {
                // this command is parsed by TerminalWebSocket.handleSerialConnect()
                // which calls ConnectionManager.connectSerial()
                ws.send('connect serial ' + port + ' ' + baud);
            });
        }

        function connectSsh() {
            var host = document.getElementById('sshHost').value;
            var port = document.getElementById('sshPort').value;
            var user = document.getElementById('sshUser').value;
            var pass = prompt('Enter SSH password:');
            if (!host || !user || !pass) { alert('Fill all fields'); return; }

            ensureWebSocket(function() {
                ws.send('connect ssh ' + host + ' ' + port + ' ' + user + ' ' + pass);
            });
        }

        function sendDisconnect() {
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send('disconnect');
                document.getElementById('deviceRole').textContent = '';
            }
        }

        function sendStatus() {
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send('status');
            }
        }

        // helper: connect WebSocket first if not connected, then run callback
        function ensureWebSocket(callback) {
            if (!ws || ws.readyState !== WebSocket.OPEN) {
                connectWebSocket();
                // wait for connection to establish
                setTimeout(function() {
                    if (ws && ws.readyState === WebSocket.OPEN) {
                        callback();
                    } else {
                        appendTerminal('WebSocket not ready. Try again.\n');
                    }
                }, 800);
            } else {
                callback();
            }
        }

        // ==================== TAB SWITCHING ====================

        function showTab(tab, elem) {
            document.querySelectorAll('.tab').forEach(function(t) { t.classList.remove('active'); });
            elem.classList.add('active');
            document.getElementById('serial-config').style.display = tab === 'serial' ? 'block' : 'none';
            document.getElementById('ssh-config').style.display = tab === 'ssh' ? 'block' : 'none';
        }

        // scan devices on page load
        scanDevices();
    </script>
</body>
</html>
