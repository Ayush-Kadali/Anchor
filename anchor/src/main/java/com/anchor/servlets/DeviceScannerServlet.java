package com.anchor.servlets;

import com.fazecast.jSerialComm.SerialPort;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * DeviceScannerServlet - Scans for available serial ports
 *
 * Demonstrates:
 * - Integration with jSerialComm library
 * - REST API endpoint returning JSON
 * - Hardware enumeration
 *
 * NOTE: This is a prototype exploring device detection.
 * Full implementation will include:
 * - Background thread for continuous monitoring
 * - Event-based device connect/disconnect notifications
 * - WebSocket integration for real-time updates
 */
public class DeviceScannerServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        try {
            // Use jSerialComm to enumerate serial ports
            SerialPort[] ports = SerialPort.getCommPorts();

            StringBuilder json = new StringBuilder();
            json.append("{\"devices\":[");

            for (int i = 0; i < ports.length; i++) {
                SerialPort port = ports[i];

                if (i > 0) json.append(",");

                json.append("{");
                json.append("\"name\":\"").append(escapeJson(port.getDescriptivePortName())).append("\",");
                json.append("\"port\":\"").append(escapeJson(port.getSystemPortName())).append("\",");
                json.append("\"description\":\"").append(escapeJson(port.getPortDescription())).append("\"");
                json.append("}");
            }

            json.append("],");
            json.append("\"count\":").append(ports.length).append(",");
            json.append("\"status\":\"success\"");
            json.append("}");

            out.print(json.toString());

            System.out.println("[Anchor] Device scan completed. Found " + ports.length + " ports.");

        } catch (Exception e) {
            // Handle case where jSerialComm native library is not available
            out.print("{\"devices\":[],\"count\":0,\"status\":\"error\",\"message\":\"" +
                      escapeJson(e.getMessage()) + "\"}");
            System.err.println("[Anchor] Device scan error: " + e.getMessage());
        }
    }

    /**
     * Basic JSON string escaping
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
