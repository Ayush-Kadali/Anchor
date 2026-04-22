<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Anchor - Login</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
    <div class="login-container">
        <div class="login-box">
            <div class="prototype-badge">PROTOTYPE v0.1</div>
            <h1>Anchor</h1>
            <p class="subtitle">Unified Terminal Interface</p>

            <% if (request.getAttribute("error") != null) { %>
                <div class="error-message">
                    <%= request.getAttribute("error") %>
                </div>
            <% } %>

            <form action="login" method="POST">
                <div class="form-group">
                    <label for="username">Username</label>
                    <input type="text" id="username" name="username"
                           placeholder="Enter username" required>
                </div>

                <div class="form-group">
                    <label for="password">Password</label>
                    <input type="password" id="password" name="password"
                           placeholder="Enter password" required>
                </div>

                <button type="submit" class="btn btn-primary">Login</button>
            </form>

            <% if (request.getParameter("registered") != null) { %>
                <p style="color: green; text-align: center;">Registration successful! Please login.</p>
            <% } %>

            <p style="text-align: center; margin-top: 20px; color: #666; font-size: 0.85rem;">
                Demo credentials: admin / admin123
            </p>
            <p style="text-align: center;">
                <a href="register.jsp">Create new account</a>
            </p>
        </div>
    </div>
</body>
</html>
