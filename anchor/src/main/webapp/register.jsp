<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <title>Anchor - Register</title>
</head>
<body>
    <h2>Anchor - Register New User</h2>
    <p><i>Prototype - users stored in memory for now</i></p>

    <% if (request.getAttribute("error") != null) { %>
        <p style="color: red;"><b><%= request.getAttribute("error") %></b></p>
    <% } %>

    <form action="register" method="POST">
        <table>
            <tr>
                <td>Username:</td>
                <td><input type="text" name="username" required></td>
            </tr>
            <tr>
                <td>Password:</td>
                <td><input type="password" name="password" required></td>
            </tr>
            <tr>
                <td>Confirm Password:</td>
                <td><input type="password" name="confirmPassword" required></td>
            </tr>
            <tr>
                <td></td>
                <td><button type="submit">Register</button></td>
            </tr>
        </table>
    </form>

    <br>
    <a href="login.jsp">Already have an account? Login</a>
</body>
</html>
