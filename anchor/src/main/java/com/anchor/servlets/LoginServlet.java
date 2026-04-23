package com.anchor.servlets;

import com.anchor.models.User;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * LoginServlet - Handles user authentication
 *
 * Demonstrates:
 * - HttpServlet lifecycle (doGet, doPost)
 * - Session management with HttpSession
 * - Request/Response handling
 *
 * NOTE: Prototype uses in-memory user storage.
 * Production will use MySQL database via UserDAO.
 */
public class LoginServlet extends HttpServlet {

    // In-memory user store (prototype only)
    // TODO: Replace with database integration
    private static final Map<String, User> users = new HashMap<>();

    @Override
    public void init() throws ServletException {
        // Initialize with demo user
        User admin = new User("admin", "admin123", "ADMIN");
        users.put(admin.getUsername(), admin);

        User demo = new User("demo", "demo123", "USER");
        users.put(demo.getUsername(), demo);

        System.out.println("[Anchor] LoginServlet initialized with demo users");
    }

    // these two methods let RegisterServlet access the user map
    // hacky but fine for prototype - will be replaced by database
    public static boolean userExists(String username) {
        return users.containsKey(username);
    }

    public static void addUser(User user) {
        users.put(user.getUsername(), user);
    }

    // used by ConnectionManager privacy filter and AdminServlet
    public static User getUser(String username) {
        return users.get(username);
    }

    // used by AdminServlet to list all users
    public static Map<String, User> getAllUsers() {
        return users;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Redirect to login page
        request.getRequestDispatcher("login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = request.getParameter("username");
        String password = request.getParameter("password");

        // Validate input
        if (username == null || password == null ||
            username.trim().isEmpty() || password.trim().isEmpty()) {
            request.setAttribute("error", "Username and password are required");
            request.getRequestDispatcher("login.jsp").forward(request, response);
            return;
        }

        // Authenticate user
        User user = users.get(username.trim());

        if (user != null && user.verifyPassword(password)) {
            // Create session
            HttpSession session = request.getSession(true);
            session.setAttribute("user", user.getUsername());
            session.setAttribute("role", user.getRole());
            session.setMaxInactiveInterval(30 * 60); // 30 minutes

            System.out.println("[Anchor] User logged in: " + username);
            response.sendRedirect("dashboard.jsp");
        } else {
            // Authentication failed
            request.setAttribute("error", "Invalid username or password");
            request.getRequestDispatcher("login.jsp").forward(request, response);
        }
    }
}
