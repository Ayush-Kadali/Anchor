<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    // Check authentication - if not logged in, go to login page
    if (session.getAttribute("user") == null) {
        response.sendRedirect("login.jsp");
        return;
    }
    String username = (String) session.getAttribute("user");
    String role = (String) session.getAttribute("role");
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Anchor - Dashboard</title>
    <style>
        /* bare minimum styling - just enough to be usable */
        body { font-family: Arial, sans-serif; margin: 10px; background: #f5f5f5; }
        .header { background: #fff; padding: 10px; border: 1px solid #ccc; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center; }
        .main { display: flex; gap: 10px; }
        .sidebar { width: 280px; background: #fff; border: 1px solid #ccc; padding: 10px; }
        .content { flex: 1; }
        .device-item { padding: 8px; margin: 5px 0; background: #f0f0f0; border: 1px solid #ddd; cursor: pointer; }
        .device-item:hover { background: #e0e0e0; }
        .terminal-box { background: #1e1e1e; color: #0f0; padding: 10px; font-family: monospace; height: 300px; overflow-y: auto; white-space: pre-wrap; border: 1px solid #ccc; }
        .terminal-input { width: 100%; padding: 8px; font-family: monospace; margin-top: 5px; }
        .status-bar { background: #fff; border: 1px solid #ccc; padding: 8px; margin-top: 10px; font-size: 0.85em; }
        button { padding: 6px 12px; cursor: pointer; }
        .section { background: #fff; border: 1px solid #ccc; padding: 10px; margin-bottom: 10px; }
        h3 { margin-top: 0; border-bottom: 1px solid #eee; padding-bottom: 5px; }
        a { color: #007bff; }
        .tab { display: inline-block; padding: 5px 15px; border: 1px solid #ccc; cursor: pointer; background: #f5f5f5; }
        .tab.active { background: #007bff; color: #fff; }
    </style>
</head>
<body>

    <!-- HEADER -->
    <div class="header">
        <div>
            <b>Anchor</b> - Prototype v0.1
            &nbsp; | &nbsp;
            Logged in as: <b><%= username %></b> (<%= role %>)
            &nbsp; | &nbsp;
            Session ID: <%= session.getId().substring(0, 8) %>...
        </div>
        <div>
            <a href="dbtest">Test DB Connection</a> &nbsp;
            <a href="logout" style="color:red;">Logout</a>
        </div>
    </div>

    <div class="main">
        <!-- SIDEBAR: Devices + Connection -->
        <div class="sidebar">

            <!-- Device Scanner Section -->
            <div class="section">
                <h3>Detected Devices</h3>
                <div id="deviceList"><i>Click scan to detect...</i></div>
                <br>
                <button onclick="scanDevices()">Scan for Devices</button>
                <p style="font-size:0.8em; color:#666; margin-top:5px;">
                    Uses jSerialComm → SerialPort.getCommPorts()
                </p>
            </div>

            <!-- Quick Connect Section -->
            <div class="section">
                <h3>Quick Connect</h3>
                <div>
                    <span class="tab active" onclick="showTab('serial', this)">Serial</span>
                    <span class="tab" onclick="showTab('ssh', this)">SSH</span>
                </div>
                <br>

                <div id="serial-config">
                    <label>Port:</label><br>
                    <input type="text" id="serialPort" placeholder="/dev/ttyUSB0 or COM3" style="width:100%; padding:5px;"><br><br>
                    <label>Baud Rate:</label><br>
                    <select id="baudRate" style="width:100%; padding:5px;">
                        <option>9600</option>
                        <option>115200</option>
                        <option>19200</option>
                    </select><br><br>
                    <button onclick="connectSerial()">Connect Serial</button>
                </div>

                <div id="ssh-config" style="display:none;">
                    <label>Host:</label><br>
                    <input type="text" id="sshHost" placeholder="192.168.1.1" style="width:100%; padding:5px;"><br><br>
                    <label>Port:</label><br>
                    <input type="text" id="sshPort" value="22" style="width:100%; padding:5px;"><br><br>
                    <label>Username:</label><br>
                    <input type="text" id="sshUser" placeholder="root" style="width:100%; padding:5px;"><br><br>
                    <button onclick="connectSsh()">Connect SSH</button>
                </div>
            </div>
        </div>

        <!-- MAIN CONTENT: Terminal + Status -->
        <div class="content">

            <!-- Terminal Section -->
            <div class="section">
                <h3>
                    Terminal
                    <span id="wsStatus" style="font-size:0.8em; color:#999; font-weight:normal;">
                        [Disconnected]
                    </span>
                </h3>
                <div class="terminal-box" id="terminalOutput">Click "Connect WebSocket" to start the terminal.
This connects to our TerminalWebSocket.java endpoint.
Currently it echoes back - later it will relay to serial/SSH.
</div>
                <input type="text" class="terminal-input" id="terminalInput"
                       placeholder="Type command here and press Enter..."
                       onkeypress="handleTerminalKey(event)" disabled>
                <br><br>
                <button onclick="connectWebSocket()">Connect WebSocket</button>
                <button onclick="disconnectWebSocket()">Disconnect</button>
                <button onclick="clearTerminal()">Clear</button>
            </div>

            <!-- Status Section -->
            <div class="status-bar">
                <b>Prototype Status:</b>
                Login [done] |
                Registration [done] |
                Auth Filter [done] |
                Device Scan [done] |
                WebSocket Terminal [done] |
                JDBC Test [done] |
                Serial Connect [wip] |
                SSH Connect [wip] |
                xterm.js [wip] |
                MySQL Storage [wip]
                <br>
                <span style="font-size:0.8em;">[done] = Working &nbsp; [wip] = In Progress</span>
            </div>
        </div>
    </div>

    <script>
        var ws = null;

        // ========== WEBSOCKET ==========
        // This connects to our TerminalWebSocket.java (@ServerEndpoint("/terminal"))
        // Uses the browser's built-in WebSocket API

        function connectWebSocket() {
            // build WebSocket URL from current page location
            var wsUrl = 'ws://' + window.location.host + '<%= request.getContextPath() %>/terminal';
            appendTerminal('Connecting to: ' + wsUrl + '\n');

            try {
                ws = new WebSocket(wsUrl);

                ws.onopen = function() {
                    document.getElementById('wsStatus').textContent = '[Connected]';
                    document.getElementById('wsStatus').style.color = 'green';
                    document.getElementById('terminalInput').disabled = false;
                    document.getElementById('terminalInput').focus();
                    appendTerminal('--- WebSocket connected ---\n');
                };

                ws.onmessage = function(event) {
                    // message from server (TerminalWebSocket.onMessage)
                    appendTerminal(event.data);
                };

                ws.onclose = function() {
                    document.getElementById('wsStatus').textContent = '[Disconnected]';
                    document.getElementById('wsStatus').style.color = '#999';
                    document.getElementById('terminalInput').disabled = true;
                    appendTerminal('\n--- WebSocket disconnected ---\n');
                };

                ws.onerror = function(err) {
                    appendTerminal('\nWebSocket error. Is the server running?\n');
                };
            } catch(e) {
                appendTerminal('Failed to connect: ' + e + '\n');
            }
        }

        function disconnectWebSocket() {
            if (ws) { ws.close(); ws = null; }
        }

        function handleTerminalKey(event) {
            if (event.key === 'Enter') {
                var input = document.getElementById('terminalInput');
                var cmd = input.value;
                if (ws && ws.readyState === WebSocket.OPEN) {
                    ws.send(cmd);  // sends to TerminalWebSocket.onMessage()
                    appendTerminal(cmd + '\n');
                }
                input.value = '';
            }
        }

        function appendTerminal(text) {
            var terminal = document.getElementById('terminalOutput');
            terminal.textContent += text;
            terminal.scrollTop = terminal.scrollHeight;
        }

        function clearTerminal() {
            document.getElementById('terminalOutput').textContent = '';
        }

        // ========== DEVICE SCANNING ==========
        // Calls DeviceScannerServlet (GET /api/devices)
        // Returns JSON with list of serial ports from jSerialComm

        function scanDevices() {
            var deviceList = document.getElementById('deviceList');
            deviceList.innerHTML = '<i>Scanning...</i>';

            fetch('api/devices')
                .then(function(response) { return response.json(); })
                .then(function(data) {
                    if (data.devices && data.devices.length > 0) {
                        var html = '';
                        for (var i = 0; i < data.devices.length; i++) {
                            var d = data.devices[i];
                            html += '<div class="device-item" onclick="selectDevice(\'' + d.port + '\')">';
                            html += '<b>' + d.name + '</b><br>';
                            html += '<small>' + d.port + '</small>';
                            html += '</div>';
                        }
                        deviceList.innerHTML = html;
                    } else {
                        deviceList.innerHTML = '<i>No serial devices found. Try connecting a USB device.</i>';
                    }
                    deviceList.innerHTML += '<br><small>Found ' + data.count + ' port(s). Status: ' + data.status + '</small>';
                })
                .catch(function(err) {
                    deviceList.innerHTML = '<i style="color:red;">Scan failed: ' + err + '</i>';
                });
        }

        function selectDevice(port) {
            document.getElementById('serialPort').value = port;
            appendTerminal('\nDevice selected: ' + port + '\n');
        }

        // ========== CONNECTION (placeholder) ==========
        function connectSerial() {
            var port = document.getElementById('serialPort').value;
            var baud = document.getElementById('baudRate').value;
            if (!port) { alert('Enter a port name'); return; }
            appendTerminal('\n[Attempting serial connection: ' + port + ' @ ' + baud + ' baud]\n');
            appendTerminal('[SerialConnection.java would call: SerialPort.getCommPort("' + port + '")]\n');
            appendTerminal('[Then: serialPort.setBaudRate(' + baud + ')]\n');
            appendTerminal('[Then: serialPort.openPort()]\n');
            appendTerminal('[This is where jSerialComm connects to the physical device]\n');
            appendTerminal('[Not fully wired up yet - WebSocket → SerialConnection bridge needed]\n\n');
        }

        function connectSsh() {
            var host = document.getElementById('sshHost').value;
            var port = document.getElementById('sshPort').value;
            var user = document.getElementById('sshUser').value;
            if (!host) { alert('Enter a host'); return; }
            appendTerminal('\n[Attempting SSH connection: ' + user + '@' + host + ':' + port + ']\n');
            appendTerminal('[SshConnection.java would call: jsch.getSession("' + user + '", "' + host + '", ' + port + ')]\n');
            appendTerminal('[Then: session.connect(10000)]\n');
            appendTerminal('[Then: session.openChannel("shell")]\n');
            appendTerminal('[This is where JSch establishes the SSH tunnel]\n');
            appendTerminal('[Not fully wired up yet - WebSocket → SshConnection bridge needed]\n\n');
        }

        // ========== TAB SWITCHING ==========
        function showTab(tab, elem) {
            document.querySelectorAll('.tab').forEach(function(t) { t.classList.remove('active'); });
            elem.classList.add('active');
            document.getElementById('serial-config').style.display = tab === 'serial' ? 'block' : 'none';
            document.getElementById('ssh-config').style.display = tab === 'ssh' ? 'block' : 'none';
        }

        // scan on page load
        scanDevices();
    </script>
</body>
</html>
