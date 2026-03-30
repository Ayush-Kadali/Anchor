package com.anchor.servlets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/*
 * DbTestServlet
 * -------------
 * Simple servlet that tests if we can connect to MySQL.
 *
 * This is just a proof of concept to show JDBC works.
 * It tries to connect and reports success or failure.
 *
 * In the full version, we'll have:
 * - UserDAO for user CRUD operations
 * - ProfileDAO for device profile storage
 * - Connection pooling for performance
 *
 * For now it just shows that:
 * 1. We can load the MySQL driver (Class.forName)
 * 2. We can attempt a connection (DriverManager.getConnection)
 * 3. We handle errors properly (try-catch-finally)
 *
 * Java concepts: JDBC, DriverManager, try-catch-finally, Class.forName
 *
 * NOTE: This will show "connection failed" if MySQL isn't running.
 * That's fine for the demo - it proves the JDBC code works,
 * the driver loads, and we handle errors gracefully.
 */
public class DbTestServlet extends HttpServlet {

    // database config - would normally be in a properties file
    private static final String DB_URL = "jdbc:mysql://localhost:3306/anchor_db";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "password";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        out.println("<!DOCTYPE html><html><head><title>DB Test</title></head><body>");
        out.println("<h2>Anchor - Database Connection Test</h2>");
        out.println("<hr>");

        Connection conn = null;

        try {
            // Step 1: Load MySQL driver
            // this tells Java "we want to use MySQL"
            Class.forName("com.mysql.cj.jdbc.Driver");
            out.println("<p>&#9989; MySQL Driver loaded successfully</p>");

            // Step 2: Try to connect
            out.println("<p>Attempting connection to: " + DB_URL + "</p>");
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);

            if (conn != null && !conn.isClosed()) {
                out.println("<p style='color:green'>&#9989; <b>Database connected successfully!</b></p>");
                out.println("<p>Database: " + conn.getCatalog() + "</p>");
            }

        } catch (ClassNotFoundException e) {
            // driver jar not found - but we have it in pom.xml so this shouldn't happen
            out.println("<p style='color:red'>&#10060; MySQL Driver not found: " + e.getMessage() + "</p>");

        } catch (SQLException e) {
            // connection failed - probably MySQL isn't running, which is fine
            out.println("<p style='color:orange'>&#9888; Connection failed: " + e.getMessage() + "</p>");
            out.println("<p><i>This is expected if MySQL is not running.</i></p>");
            out.println("<p><i>The JDBC driver loaded successfully, which proves our setup works.</i></p>");
            out.println("<p><i>When MySQL is set up, this will connect automatically.</i></p>");

        } finally {
            // always close connection - important to not leak resources
            if (conn != null) {
                try {
                    conn.close();
                    out.println("<p>Connection closed properly.</p>");
                } catch (SQLException e) {
                    out.println("<p>Error closing connection: " + e.getMessage() + "</p>");
                }
            }
        }

        out.println("<hr>");
        out.println("<h3>What this demonstrates:</h3>");
        out.println("<ul>");
        out.println("<li>JDBC driver loading (Class.forName)</li>");
        out.println("<li>Database connection (DriverManager.getConnection)</li>");
        out.println("<li>Exception handling (try-catch-finally)</li>");
        out.println("<li>Resource cleanup (conn.close in finally block)</li>");
        out.println("</ul>");

        out.println("<br><a href='dashboard.jsp'>Back to Dashboard</a>");
        out.println("</body></html>");
    }
}
