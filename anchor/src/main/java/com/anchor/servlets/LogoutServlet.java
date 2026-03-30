package com.anchor.servlets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * LogoutServlet - Handles user logout and session invalidation
 *
 * Demonstrates:
 * - Session invalidation
 * - Redirect after logout
 */
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session != null) {
            String username = (String) session.getAttribute("user");
            session.invalidate();
            System.out.println("[Anchor] User logged out: " + username);
        }

        response.sendRedirect("login.jsp");
    }
}
