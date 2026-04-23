<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.anchor.servlets.ConnectionManager" %>
<%@ page import="com.anchor.models.Connection" %>
<%@ page import="com.anchor.models.User" %>
<%@ page import="com.anchor.servlets.LoginServlet" %>
<%@ page import="java.util.*" %>
<%
    /*
     * admin.jsp - Admin Dashboard
     *
     * Shows all active connections, all users, and the device block list.
     * Provides kick, block, unblock, and privacy toggle actions.
     *
     * Access restricted to ADMIN role (enforced by AuthenticationFilter).
     */
    if (session.getAttribute("user") == null) {
        response.sendRedirect("login.jsp");
        return;
    }

    String role = (String) session.getAttribute("role");
    if (!"ADMIN".equals(role)) {
        response.sendRedirect("dashboard.jsp?error=admin_only");
        return;
    }

    String username = (String) session.getAttribute("user");
    ConnectionManager mgr = ConnectionManager.getInstance();
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Anchor - Admin Dashboard</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }
        .header { background: #1a3a5c; color: #fff; padding: 15px 20px; margin-bottom: 20px; }
        .header a { color: #aad4ff; margin-left: 15px; }
        h1 { margin: 0; font-size: 20px; }
        h2 { color: #1a3a5c; border-bottom: 2px solid #1a3a5c; padding-bottom: 5px; margin-top: 30px; }
        table { width: 100%; border-collapse: collapse; margin: 10px 0; background: #fff; }
        th { background: #1a3a5c; color: #fff; padding: 10px; text-align: left; }
        td { padding: 8px; border-bottom: 1px solid #ddd; vertical-align: top; }
        tr:nth-child(even) td { background: #fafafa; }
        button { padding: 5px 10px; margin: 2px; cursor: pointer; }
        .btn-kick { background: #f39c12; color: #fff; border: none; }
        .btn-block { background: #e74c3c; color: #fff; border: none; }
        .btn-unblock { background: #27ae60; color: #fff; border: none; }
        .btn-private { background: #9b59b6; color: #fff; border: none; }
        .btn-public { background: #3498db; color: #fff; border: none; }
        .btn-make-owner { background: #16a085; color: #fff; border: none; }
        .badge-public { background: #3498db; color: #fff; padding: 2px 8px; border-radius: 3px; font-size: 0.85em; }
        .badge-private { background: #9b59b6; color: #fff; padding: 2px 8px; border-radius: 3px; font-size: 0.85em; }
        .badge-admin { background: #e74c3c; color: #fff; padding: 2px 8px; border-radius: 3px; font-size: 0.85em; }
        .badge-user { background: #7f8c8d; color: #fff; padding: 2px 8px; border-radius: 3px; font-size: 0.85em; }
        .empty { color: #999; font-style: italic; padding: 15px; }
        #statusMsg { padding: 10px; margin: 10px 0; background: #d4edda; border: 1px solid #c3e6cb; color: #155724; display: none; }
        #errorMsg { padding: 10px; margin: 10px 0; background: #f8d7da; border: 1px solid #f5c6cb; color: #721c24; display: none; }
    </style>
</head>
<body>
    <div class="header">
        <h1>Anchor Admin Dashboard</h1>
        <div style="font-size:0.9em; margin-top:5px;">
            Logged in as <b><%= username %></b> (ADMIN)
            <a href="dashboard.jsp">Dashboard</a>
            <a href="logs.jsp">Logs</a>
            <a href="status" target="_blank">Status</a>
            <a href="logout">Logout</a>
        </div>
    </div>

    <div id="statusMsg"></div>
    <div id="errorMsg"></div>

    <h2>Active Device Connections</h2>
    <%
        List<Map<String, String>> activeConns = mgr.getActiveConnectionsList("ADMIN");
        if (activeConns.isEmpty()) {
    %>
        <div class="empty">No active device connections.</div>
    <% } else { %>
        <table>
            <tr>
                <th>Connection</th>
                <th>Type</th>
                <th>Device</th>
                <th>Owner</th>
                <th>Viewers</th>
                <th>Actions (per viewer)</th>
            </tr>
            <%
                for (Map<String, String> conn : activeConns) {
                    String connId = conn.get("id");
                    List<String> viewers = mgr.getViewerNames(connId);
            %>
                <tr>
                    <td><%= connId.substring(0, 8) %>...</td>
                    <td><%= conn.get("type") %></td>
                    <td><%= conn.get("description") %></td>
                    <td><b><%= conn.get("owner") %></b></td>
                    <td>
                        <% if (viewers.isEmpty()) { %>
                            <i>none</i>
                        <% } else { %>
                            <% for (String v : viewers) { %>
                                <%= v %><br>
                            <% } %>
                        <% } %>
                    </td>
                    <td>
                        <% for (String v : viewers) { %>
                            <div style="margin-bottom:4px;">
                                <b><%= v %>:</b>
                                <button class="btn-make-owner"
                                        onclick="action('transfer','<%= connId %>','<%= v %>')">Make Owner</button>
                                <button class="btn-kick"
                                        onclick="action('kick','<%= connId %>','<%= v %>')">Kick</button>
                                <button class="btn-block"
                                        onclick="action('block','<%= connId %>','<%= v %>')">Block</button>
                            </div>
                        <% } %>
                    </td>
                </tr>
            <% } %>
        </table>
    <% } %>

    <h2>Device Block List</h2>
    <%
        Map<String, Set<String>> blocks = mgr.getAllBlocks();
        boolean anyBlocks = false;
        for (Set<String> s : blocks.values()) if (!s.isEmpty()) anyBlocks = true;
        if (!anyBlocks) {
    %>
        <div class="empty">No users are currently blocked.</div>
    <% } else { %>
        <table>
            <tr>
                <th>Device</th>
                <th>Blocked Users</th>
                <th>Actions</th>
            </tr>
            <%
                for (Map.Entry<String, Set<String>> entry : blocks.entrySet()) {
                    String deviceKey = entry.getKey();
                    Set<String> blockedUsers = entry.getValue();
                    if (blockedUsers.isEmpty()) continue;
            %>
                <tr>
                    <td><%= deviceKey %></td>
                    <td>
                        <% for (String u : blockedUsers) { %>
                            <%= u %><br>
                        <% } %>
                    </td>
                    <td>
                        <% for (String u : blockedUsers) { %>
                            <div style="margin-bottom:4px;">
                                <b><%= u %>:</b>
                                <button class="btn-unblock"
                                        onclick="unblock('<%= deviceKey %>','<%= u %>')">Unblock</button>
                            </div>
                        <% } %>
                    </td>
                </tr>
            <% } %>
        </table>
    <% } %>

    <h2>All Registered Users</h2>
    <%
        Map<String, User> allUsers = LoginServlet.getAllUsers();
        if (allUsers.isEmpty()) {
    %>
        <div class="empty">No users registered.</div>
    <% } else { %>
        <table>
            <tr>
                <th>Username</th>
                <th>Role</th>
                <th>Visibility</th>
                <th>Actions</th>
            </tr>
            <%
                for (Map.Entry<String, User> e : allUsers.entrySet()) {
                    User u = e.getValue();
            %>
                <tr>
                    <td><b><%= u.getUsername() %></b></td>
                    <td>
                        <% if ("ADMIN".equals(u.getRole())) { %>
                            <span class="badge-admin">ADMIN</span>
                        <% } else { %>
                            <span class="badge-user">USER</span>
                        <% } %>
                    </td>
                    <td>
                        <% if (u.isPublic()) { %>
                            <span class="badge-public">PUBLIC</span>
                        <% } else { %>
                            <span class="badge-private">PRIVATE</span>
                        <% } %>
                    </td>
                    <td>
                        <% if (u.isPublic()) { %>
                            <button class="btn-private"
                                    onclick="privacy('<%= u.getUsername() %>',false)">Make Private</button>
                        <% } else { %>
                            <button class="btn-public"
                                    onclick="privacy('<%= u.getUsername() %>',true)">Make Public</button>
                        <% } %>
                    </td>
                </tr>
            <% } %>
        </table>
    <% } %>

    <script>
        function action(type, connId, user) {
            if (!confirm(type.toUpperCase() + ' ' + user + '?')) return;
            fetch('admin/action?action=' + type + '&connId=' + connId + '&username=' + encodeURIComponent(user))
                .then(function(r) { return r.json(); })
                .then(handleResponse);
        }
        function unblock(deviceKey, user) {
            if (!confirm('Unblock ' + user + ' from ' + deviceKey + '?')) return;
            fetch('admin/action?action=unblock&deviceKey=' + encodeURIComponent(deviceKey) + '&username=' + encodeURIComponent(user))
                .then(function(r) { return r.json(); })
                .then(handleResponse);
        }
        function privacy(user, makePublic) {
            var label = makePublic ? 'make ' + user + ' PUBLIC' : 'make ' + user + ' PRIVATE';
            if (!confirm(label + '?')) return;
            fetch('admin/action?action=privacy&username=' + encodeURIComponent(user) + '&public=' + makePublic)
                .then(function(r) { return r.json(); })
                .then(handleResponse);
        }
        function handleResponse(data) {
            if (data.status === 'success') {
                var s = document.getElementById('statusMsg');
                s.textContent = data.message;
                s.style.display = 'block';
                setTimeout(function() { location.reload(); }, 1200);
            } else {
                var e = document.getElementById('errorMsg');
                e.textContent = 'Error: ' + data.message;
                e.style.display = 'block';
            }
        }
    </script>
</body>
</html>
