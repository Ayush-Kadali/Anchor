package com.anchor.servlets;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;

/*
 * AuthenticationFilter
 * --------------------
 * This is a Servlet Filter. It runs BEFORE any servlet or JSP.
 * Think of it as a security guard at the door.
 *
 * How it works:
 * - Every request goes through this filter first
 * - It checks: does the user have a valid session?
 * - If yes: let them through (chain.doFilter)
 * - If no: redirect to login page
 *
 * We skip filtering for login page, css, and api calls
 * otherwise you'd get an infinite redirect loop
 * (login page → filter → redirect to login → filter → redirect...)
 *
 * Java concepts: javax.servlet.Filter, FilterChain, HttpSession
 */
public class AuthenticationFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // called once when filter loads
        System.out.println("[Anchor] AuthenticationFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // cast to HTTP versions so we can access session, URL etc
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // get the URL the user is trying to access
        String uri = httpRequest.getRequestURI();

        // these pages should be accessible WITHOUT login
        // otherwise login page itself would be blocked
        boolean isLoginPage = uri.endsWith("login.jsp") || uri.endsWith("/login");
        boolean isRegisterPage = uri.endsWith("register.jsp") || uri.endsWith("/register");
        boolean isStaticResource = uri.endsWith(".css") || uri.endsWith(".js") || uri.endsWith(".png");
        boolean isPublicApi = uri.contains("/api/");

        if (isLoginPage || isRegisterPage || isStaticResource || isPublicApi) {
            // let these through without checking session
            chain.doFilter(request, response);
            return;
        }

        // for everything else, check if user is logged in
        HttpSession session = httpRequest.getSession(false);

        if (session != null && session.getAttribute("user") != null) {
            // user is logged in, let them through
            chain.doFilter(request, response);
        } else {
            // not logged in, send to login page
            System.out.println("[Anchor] Blocked unauthorized access to: " + uri);
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/login.jsp");
        }
    }

    @Override
    public void destroy() {
        System.out.println("[Anchor] AuthenticationFilter destroyed");
    }
}
