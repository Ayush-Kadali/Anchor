package com.anchor.servlets;

import com.anchor.models.Connection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

/*
 * StatusServlet
 * -------------
 * Shows a live status page of the server:
 * - How many active connections
 * - Which devices are in use and by whom
 * - Server info
 *
 * This is useful for the professor to see the system state
 * without opening the terminal.
 */
public class StatusServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        ConnectionManager mgr = ConnectionManager.getInstance();

        out.println("<!DOCTYPE html><html><head><title>Anchor - Server Status</title>");
        out.println("<meta http-equiv='refresh' content='5'>"); // auto-refresh every 5 sec
        out.println("</head><body style='font-family:Arial;margin:20px;'>");

        out.println("<h2>Anchor Server Status</h2>");
        out.println("<p>Server: " + request.getLocalAddr() + ":" + request.getLocalPort() + "</p>");
        out.println("<p>Auto-refreshes every 5 seconds.</p>");
        out.println("<hr>");

        // Active connections
        out.println("<h3>Active Connections: " + mgr.getActiveCount() + "</h3>");

        if (mgr.getActiveCount() == 0) {
            out.println("<p><i>No active device connections.</i></p>");
        } else {
            out.println("<table border='1' cellpadding='8' cellspacing='0'>");
            out.println("<tr><th>Connection ID</th><th>Type</th><th>Description</th><th>Connected</th><th>Owner</th><th>Viewers</th></tr>");

            Map<String, Connection> all = mgr.getAllConnections();
            for (Map.Entry<String, Connection> entry : all.entrySet()) {
                String connId = entry.getKey();
                Connection conn = entry.getValue();
                String owner = mgr.getOwnerName(connId);
                String desc = mgr.getDescription(connId);
                java.util.List<String> viewers = mgr.getViewerNames(connId);

                out.println("<tr>");
                out.println("<td>" + connId.substring(0, 8) + "...</td>");
                out.println("<td>" + conn.getType() + "</td>");
                out.println("<td>" + (desc != null ? desc : "-") + "</td>");
                out.println("<td>" + (conn.isConnected() ? "Yes" : "No") + "</td>");
                out.println("<td><b>" + (owner != null ? owner : "none") + "</b></td>");
                out.println("<td>" + (viewers.isEmpty() ? "none" : String.join(", ", viewers)) + "</td>");
                out.println("</tr>");
            }
            out.println("</table>");
        }

        out.println("<hr>");
        out.println("<a href='dashboard.jsp'>Back to Dashboard</a>");
        out.println("</body></html>");
    }
}
