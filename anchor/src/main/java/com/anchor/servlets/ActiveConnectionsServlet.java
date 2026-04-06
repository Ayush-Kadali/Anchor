package com.anchor.servlets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/*
 * ActiveConnectionsServlet
 * ------------------------
 * REST API that returns currently active device connections as JSON.
 * Dashboard polls this every few seconds to show a live list
 * with "Join" buttons so users can join existing sessions.
 */
public class ActiveConnectionsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        ConnectionManager mgr = ConnectionManager.getInstance();
        List<Map<String, String>> active = mgr.getActiveConnectionsList();

        StringBuilder json = new StringBuilder();
        json.append("{\"connections\":[");

        for (int i = 0; i < active.size(); i++) {
            Map<String, String> conn = active.get(i);
            if (i > 0) json.append(",");
            json.append("{");
            json.append("\"id\":\"").append(conn.get("id")).append("\",");
            json.append("\"type\":\"").append(conn.get("type")).append("\",");
            json.append("\"description\":\"").append(conn.get("description")).append("\",");
            json.append("\"owner\":\"").append(conn.get("owner")).append("\"");
            json.append("}");
        }

        json.append("],\"count\":").append(active.size()).append("}");
        out.print(json.toString());
    }
}
