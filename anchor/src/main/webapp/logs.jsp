<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    // Access control: only authenticated users may view / download logs.
    Object sessionUser = session.getAttribute("user");
    if (sessionUser == null) {
        response.sendRedirect("login.jsp");
        return;
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <title>Anchor - Log Report</title>
    <style>
        * { box-sizing: border-box; }
        body {
            font-family: Arial, Helvetica, sans-serif;
            background: #ffffff;
            color: #222;
            margin: 0;
            padding: 0;
        }
        header {
            background: #1a3a5c;
            color: #fff;
            padding: 18px 28px;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        header h1 { margin: 0; font-size: 20px; font-weight: 600; }
        header a {
            color: #cfe2ff;
            text-decoration: none;
            font-size: 14px;
        }
        header a:hover { text-decoration: underline; }
        main {
            max-width: 780px;
            margin: 30px auto;
            padding: 0 20px;
        }
        .card {
            border: 1px solid #d0d7de;
            border-radius: 6px;
            padding: 24px 26px;
            background: #fff;
            box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
        }
        .card h2 {
            margin-top: 0;
            color: #1a3a5c;
            font-size: 18px;
            border-bottom: 2px solid #1a3a5c;
            padding-bottom: 8px;
        }
        .help {
            font-size: 13px;
            color: #555;
            background: #f5f8fc;
            border-left: 3px solid #1a3a5c;
            padding: 10px 12px;
            margin: 12px 0 20px;
        }
        .grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 14px 18px;
        }
        .field { display: flex; flex-direction: column; }
        .field.full { grid-column: 1 / span 2; }
        label {
            font-size: 13px;
            color: #1a3a5c;
            font-weight: 600;
            margin-bottom: 4px;
        }
        input[type="text"],
        input[type="datetime-local"],
        select {
            padding: 7px 9px;
            border: 1px solid #c1c7cd;
            border-radius: 4px;
            font-size: 14px;
            font-family: inherit;
            background: #fff;
        }
        input:focus, select:focus {
            outline: none;
            border-color: #1a3a5c;
            box-shadow: 0 0 0 2px rgba(26, 58, 92, 0.15);
        }
        .actions {
            margin-top: 22px;
            display: flex;
            gap: 12px;
            align-items: center;
        }
        button.primary {
            background: #1a3a5c;
            color: #fff;
            border: none;
            padding: 10px 18px;
            border-radius: 4px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
        }
        button.primary:hover { background: #143047; }
        a.secondary {
            color: #1a3a5c;
            text-decoration: none;
            font-size: 14px;
            padding: 10px 14px;
            border: 1px solid #1a3a5c;
            border-radius: 4px;
        }
        a.secondary:hover { background: #f0f5fa; }
        .back-link {
            display: inline-block;
            margin-top: 16px;
            font-size: 14px;
            color: #1a3a5c;
            text-decoration: none;
        }
        .back-link:hover { text-decoration: underline; }
    </style>
</head>
<body>
    <header>
        <h1>Anchor - Log Report</h1>
        <a href="dashboard.jsp">Back to Dashboard</a>
    </header>

    <main>
        <div class="card">
            <h2>Download Session Logs (PDF)</h2>

            <div class="help">
                Use the fields below to narrow the report. <strong>Empty filters mean
                "include everything"</strong>. Date range uses your browser's local time
                and is converted to milliseconds automatically at submission.
            </div>

            <form id="logForm" method="get" action="api/logs/download"
                  onsubmit="return convertDates();">

                <div class="grid">
                    <div class="field">
                        <label for="user">Username</label>
                        <input type="text" id="user" name="user"
                               placeholder="e.g. ayush" autocomplete="off" />
                    </div>

                    <div class="field">
                        <label for="type">Connection Type</label>
                        <select id="type" name="type">
                            <option value="">Any</option>
                            <option value="SERIAL">SERIAL</option>
                            <option value="SSH">SSH</option>
                        </select>
                    </div>

                    <div class="field">
                        <label for="device">Device Name Contains</label>
                        <input type="text" id="device" name="device"
                               placeholder="e.g. USB or raspberrypi" autocomplete="off" />
                    </div>

                    <div class="field">
                        <label for="port">Port/Host Contains</label>
                        <input type="text" id="port" name="port"
                               placeholder="e.g. COM3 or 192.168" autocomplete="off" />
                    </div>

                    <div class="field">
                        <label for="fromDt">From (start timestamp)</label>
                        <input type="datetime-local" id="fromDt" />
                    </div>

                    <div class="field">
                        <label for="toDt">To (end timestamp)</label>
                        <input type="datetime-local" id="toDt" />
                    </div>
                </div>

                <!-- Hidden fields actually submitted with the form -->
                <input type="hidden" name="from" id="from" />
                <input type="hidden" name="to" id="to" />

                <div class="actions">
                    <button type="submit" class="primary">Download PDF</button>
                    <a href="#" id="previewLink" class="secondary"
                       onclick="return openPreview();">Preview logs</a>
                </div>
            </form>

            <a class="back-link" href="dashboard.jsp">&larr; Back to Dashboard</a>
        </div>
    </main>

    <script>
        /**
         * Convert the datetime-local picker values (which are strings like
         * "2026-04-23T14:30") into milliseconds-since-epoch and inject them
         * into the hidden "from" / "to" inputs prior to form submission.
         * Empty pickers are left as empty hidden inputs so the servlet treats
         * them as "no filter".
         */
        function convertDates() {
            try {
                var fromDt = document.getElementById('fromDt').value;
                var toDt   = document.getElementById('toDt').value;
                var fromHidden = document.getElementById('from');
                var toHidden   = document.getElementById('to');

                fromHidden.value = '';
                toHidden.value   = '';

                if (fromDt) {
                    var fMs = new Date(fromDt).getTime();
                    if (!isNaN(fMs)) fromHidden.value = String(fMs);
                }
                if (toDt) {
                    var tMs = new Date(toDt).getTime();
                    if (!isNaN(tMs)) toHidden.value = String(tMs);
                }
                return true;
            } catch (e) {
                console.error('Date conversion failed', e);
                return true; // let the form submit anyway
            }
        }

        /**
         * Open the download endpoint in a new tab with a preview flag so the
         * user can review contents without a forced download dialog (the
         * server still returns application/pdf; browsers typically render
         * inline when opened in a new tab).
         */
        function openPreview() {
            convertDates();
            var form = document.getElementById('logForm');
            var params = new URLSearchParams();
            ['user', 'type', 'device', 'port', 'from', 'to'].forEach(function (n) {
                var el = form.elements[n];
                if (el && el.value) params.append(n, el.value);
            });
            params.append('preview', 'true');
            window.open('api/logs/download?' + params.toString(), '_blank');
            return false;
        }
    </script>
</body>
</html>
