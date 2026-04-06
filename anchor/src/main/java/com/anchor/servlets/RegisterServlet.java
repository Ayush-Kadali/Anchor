package com.anchor.servlets;

import com.anchor.models.User;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/*
 * RegisterServlet
 * ---------------
 * Handles new user registration.
 *
 * GET  /register → show register.jsp form
 * POST /register → create new user, redirect to login
 *
 * Right now users are stored in LoginServlet's HashMap (in memory).
 * When we add MySQL, this will use UserDAO instead.
 *
 * This shows:
 * - Servlet doGet/doPost pattern
 * - Input validation
 * - User object creation (which triggers password hashing)
 * - Redirect after success
 *
 * Java concepts: HttpServlet, request.getParameter(), sendRedirect
 */
public class RegisterServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // just show the registration form
        request.getRequestDispatcher("register.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");

        // basic validation
        if (username == null || password == null ||
            username.trim().isEmpty() || password.trim().isEmpty()) {
            request.setAttribute("error", "All fields are required");
            request.getRequestDispatcher("register.jsp").forward(request, response);
            return;
        }

        // check passwords match
        if (!password.equals(confirmPassword)) {
            request.setAttribute("error", "Passwords do not match");
            request.getRequestDispatcher("register.jsp").forward(request, response);
            return;
        }

        // check if username already taken
        // we're accessing LoginServlet's user map directly for now
        // this is hacky but works for prototype
        if (LoginServlet.userExists(username.trim())) {
            request.setAttribute("error", "Username already taken");
            request.getRequestDispatcher("register.jsp").forward(request, response);
            return;
        }

        // create the user - this triggers password hashing in User constructor
        User newUser = new User(username.trim(), password, "USER");
        LoginServlet.addUser(newUser);

        System.out.println("[Anchor] New user registered: " + username);

        // send to login page with success message
        response.sendRedirect("login.jsp?registered=true");
    }
}
