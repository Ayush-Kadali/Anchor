package com.anchor.servlets;

import com.anchor.models.User;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;

/*
 * AdminServlet - Handles admin actions.
 *
 * Only accessible to users with ADMIN role - enforced by AuthenticationFilter
 * via URL pattern check.
 *
 * GET /admin/action?action=... with these actions:
 *   kick    - kick a viewer from a connection: &connId=X&username=Y
 *   block   - block user from a device + kick: &connId=X&username=Y
 *   unblock - remove user from device block list: &deviceKey=X&username=Y
 *   privacy - toggle user public/private: &username=X&public=true|false
 *
 * Returns JSON: {"status":"success", "message":"..."}
 * Or error:    {"status":"error", "message":"..."}
 *
 * Java concepts: HttpServlet, session role verification, ConnectionManager delegation.
 */
public class AdminServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        // double-check admin role even though filter should have blocked non-admins
        HttpSession session = request.getSession(false);
        if (session == null || !"ADMIN".equals(session.getAttribute("role"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"status\":\"error\",\"message\":\"admin role required\"}");
            return;
        }

        String action = request.getParameter("action");
        if (action == null || action.isEmpty()) {
            out.print("{\"status\":\"error\",\"message\":\"action parameter required\"}");
            return;
        }

        ConnectionManager mgr = ConnectionManager.getInstance();

        try {
            switch (action) {
                case "kick":
                    handleKick(request, out, mgr);
                    break;
                case "block":
                    handleBlock(request, out, mgr);
                    break;
                case "unblock":
                    handleUnblock(request, out, mgr);
                    break;
                case "privacy":
                    handlePrivacy(request, out);
                    break;
                case "transfer":
                    handleTransfer(request, out, mgr);
                    break;
                default:
                    out.print("{\"status\":\"error\",\"message\":\"unknown action\"}");
            }
        } catch (Exception e) {
            out.print("{\"status\":\"error\",\"message\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleKick(HttpServletRequest request, PrintWriter out, ConnectionManager mgr) {
        String connId = request.getParameter("connId");
        String username = request.getParameter("username");

        if (connId == null || username == null) {
            out.print("{\"status\":\"error\",\"message\":\"connId and username required\"}");
            return;
        }

        boolean kicked = mgr.kickViewer(connId, username);
        if (kicked) {
            out.print("{\"status\":\"success\",\"message\":\"kicked " + escape(username) + "\"}");
        } else {
            out.print("{\"status\":\"error\",\"message\":\"user not found as viewer\"}");
        }
    }

    private void handleBlock(HttpServletRequest request, PrintWriter out, ConnectionManager mgr) {
        String connId = request.getParameter("connId");
        String username = request.getParameter("username");

        if (connId == null || username == null) {
            out.print("{\"status\":\"error\",\"message\":\"connId and username required\"}");
            return;
        }

        // block first, then kick - so they're removed AND prevented from rejoining
        mgr.blockUser(connId, username);
        mgr.kickViewer(connId, username);

        String deviceKey = mgr.getDeviceKey(connId);
        out.print("{\"status\":\"success\",\"message\":\"blocked " + escape(username) +
                  " from " + escape(deviceKey) + "\"}");
    }

    private void handleUnblock(HttpServletRequest request, PrintWriter out, ConnectionManager mgr) {
        String deviceKey = request.getParameter("deviceKey");
        String username = request.getParameter("username");

        if (deviceKey == null || username == null) {
            out.print("{\"status\":\"error\",\"message\":\"deviceKey and username required\"}");
            return;
        }

        mgr.unblockUser(deviceKey, username);
        out.print("{\"status\":\"success\",\"message\":\"unblocked " + escape(username) + "\"}");
    }

    /*
     * Admin-initiated ownership transfer.
     * Takes a viewer username and makes them the new owner,
     * demoting the current owner to viewer.
     */
    private void handleTransfer(HttpServletRequest request, PrintWriter out, ConnectionManager mgr) {
        String connId = request.getParameter("connId");
        String username = request.getParameter("username");

        if (connId == null || username == null) {
            out.print("{\"status\":\"error\",\"message\":\"connId and username required\"}");
            return;
        }

        boolean transferred = mgr.adminTransferOwnership(connId, username);
        if (transferred) {
            out.print("{\"status\":\"success\",\"message\":\"" + escape(username) +
                      " is now the owner\"}");
        } else {
            out.print("{\"status\":\"error\",\"message\":\"transfer failed - user not in viewer list or not connected\"}");
        }
    }

    private void handlePrivacy(HttpServletRequest request, PrintWriter out) {
        String username = request.getParameter("username");
        String publicStr = request.getParameter("public");

        if (username == null || publicStr == null) {
            out.print("{\"status\":\"error\",\"message\":\"username and public parameters required\"}");
            return;
        }

        User user = LoginServlet.getUser(username);
        if (user == null) {
            out.print("{\"status\":\"error\",\"message\":\"user not found\"}");
            return;
        }

        boolean makePublic = Boolean.parseBoolean(publicStr);
        user.setPublic(makePublic);
        out.print("{\"status\":\"success\",\"message\":\"" + escape(username) +
                  " is now " + (makePublic ? "public" : "private") + "\"}");
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
