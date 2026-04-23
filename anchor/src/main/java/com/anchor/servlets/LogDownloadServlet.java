package com.anchor.servlets;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.anchor.models.LogEntry;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * LogDownloadServlet
 *
 * Generates a PDF report of terminal session log entries collected by the
 * {@link ConnectionManager} singleton. The servlet is exposed at
 * {@code /api/logs/download} and accepts the following optional query
 * parameters which are applied as an AND-combined filter over all log entries:
 *
 * <ul>
 *   <li>{@code user}    - exact, case-insensitive match on the username</li>
 *   <li>{@code type}    - connection type, SERIAL or SSH</li>
 *   <li>{@code device}  - substring (case-insensitive) match on device name</li>
 *   <li>{@code port}    - substring (case-sensitive) match on port/host</li>
 *   <li>{@code from}    - minimum timestamp (inclusive, ms since epoch)</li>
 *   <li>{@code to}      - maximum timestamp (inclusive, ms since epoch)</li>
 * </ul>
 *
 * The resulting PDF contains a title, a subtitle describing the filters that
 * were applied, and a table with one row per log entry. When no entries match
 * the filters a placeholder paragraph is emitted instead of an empty table.
 *
 * This servlet is expected to be registered via {@code web.xml} or a
 * programmatic mapping; no {@code @WebServlet} annotation is declared here.
 */
public class LogDownloadServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final String FILE_TIMESTAMP_FORMAT = "yyyyMMdd-HHmmss";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Read and normalise filter parameters
        String userFilter = trimToNull(request.getParameter("user"));
        String typeFilter = trimToNull(request.getParameter("type"));
        String deviceFilter = trimToNull(request.getParameter("device"));
        String portFilter = trimToNull(request.getParameter("port"));
        Long fromFilter = parseLong(request.getParameter("from"));
        Long toFilter = parseLong(request.getParameter("to"));

        // Fetch logs from the ConnectionManager singleton
        List<LogEntry> allLogs;
        try {
            allLogs = ConnectionManager.getInstance().getAllLogs();
        } catch (Exception ex) {
            allLogs = new ArrayList<>();
        }

        // Apply the filters
        List<LogEntry> filtered = new ArrayList<>();
        if (allLogs != null) {
            for (LogEntry entry : allLogs) {
                if (entry == null) {
                    continue;
                }
                if (userFilter != null) {
                    String u = entry.getUsername();
                    if (u == null || !u.equalsIgnoreCase(userFilter)) {
                        continue;
                    }
                }
                if (typeFilter != null) {
                    String t = entry.getConnectionType();
                    if (t == null || !t.equalsIgnoreCase(typeFilter)) {
                        continue;
                    }
                }
                if (deviceFilter != null) {
                    String d = entry.getDeviceName();
                    if (d == null
                            || !d.toLowerCase().contains(deviceFilter.toLowerCase())) {
                        continue;
                    }
                }
                if (portFilter != null) {
                    String p = entry.getPortOrHost();
                    if (p == null || !p.contains(portFilter)) {
                        continue;
                    }
                }
                if (fromFilter != null && entry.getTimestamp() < fromFilter) {
                    continue;
                }
                if (toFilter != null && entry.getTimestamp() > toFilter) {
                    continue;
                }
                filtered.add(entry);
            }
        }

        // Configure response headers
        String fileTimestamp = new SimpleDateFormat(FILE_TIMESTAMP_FORMAT).format(new Date());
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"anchor-logs-" + fileTimestamp + ".pdf\"");

        // Build the PDF
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        OutputStream out = response.getOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(26, 58, 92));
            Paragraph title = new Paragraph("Anchor Terminal Log Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(8f);
            document.add(title);

            // Subtitle: generation time + filter description
            Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
            SimpleDateFormat tsFmt = new SimpleDateFormat(TIMESTAMP_FORMAT);
            Paragraph generated = new Paragraph(
                    "Generated: " + tsFmt.format(new Date()), subFont);
            generated.setAlignment(Element.ALIGN_CENTER);
            document.add(generated);

            Paragraph filterLine = new Paragraph(
                    "Filters: " + describeFilters(userFilter, typeFilter, deviceFilter,
                            portFilter, fromFilter, toFilter, tsFmt),
                    subFont);
            filterLine.setAlignment(Element.ALIGN_CENTER);
            filterLine.setSpacingAfter(14f);
            document.add(filterLine);

            if (filtered.isEmpty()) {
                Font emptyFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 12, Color.GRAY);
                Paragraph empty = new Paragraph("No log entries match the filters.", emptyFont);
                empty.setAlignment(Element.ALIGN_CENTER);
                empty.setSpacingBefore(40f);
                document.add(empty);
            } else {
                PdfPTable table = new PdfPTable(new float[]{3.2f, 1.6f, 1.3f, 1.1f, 2.2f, 5.5f});
                table.setWidthPercentage(100f);
                table.setHeaderRows(1);

                Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
                Color headerBg = new Color(26, 58, 92);
                addHeaderCell(table, "Time", headerFont, headerBg);
                addHeaderCell(table, "User", headerFont, headerBg);
                addHeaderCell(table, "Direction", headerFont, headerBg);
                addHeaderCell(table, "Type", headerFont, headerBg);
                addHeaderCell(table, "Device", headerFont, headerBg);
                addHeaderCell(table, "Message", headerFont, headerBg);

                Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
                Font monoFont = FontFactory.getFont(FontFactory.COURIER, 9, Color.BLACK);
                boolean shade = false;
                Color shadeColor = new Color(245, 248, 252);

                for (LogEntry entry : filtered) {
                    Color bg = shade ? shadeColor : Color.WHITE;
                    shade = !shade;

                    addBodyCell(table, tsFmt.format(new Date(entry.getTimestamp())), bodyFont, bg);
                    addBodyCell(table, nullToEmpty(entry.getUsername()), bodyFont, bg);
                    addBodyCell(table, nullToEmpty(entry.getDirection()), bodyFont, bg);
                    addBodyCell(table, nullToEmpty(entry.getConnectionType()), bodyFont, bg);

                    String device = nullToEmpty(entry.getDeviceName());
                    String portOrHost = nullToEmpty(entry.getPortOrHost());
                    String deviceCombined = device.isEmpty() ? portOrHost
                            : (portOrHost.isEmpty() ? device : device + "\n(" + portOrHost + ")");
                    addBodyCell(table, deviceCombined, bodyFont, bg);

                    addBodyCell(table, sanitiseMessage(entry.getMessage()), monoFont, bg);
                }

                document.add(table);
            }
        } catch (DocumentException de) {
            throw new ServletException("Failed to generate PDF", de);
        } finally {
            if (document.isOpen()) {
                try {
                    document.close();
                } catch (Exception ignore) {
                    // already handled
                }
            }
            try {
                out.flush();
            } catch (Exception ignore) {
                // client may have disconnected
            }
        }
    }

    // ---- helpers --------------------------------------------------------

    private static void addHeaderCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5f);
        table.addCell(cell);
    }

    private static void addBodyCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(4f);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        table.addCell(cell);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Long parseLong(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Remove trailing newlines/whitespace and escape characters that could
     * break the PDF rendering. OpenPDF handles most characters natively, but
     * stray control chars (except tab and newline within the message) are
     * replaced with a space.
     */
    private static String sanitiseMessage(String msg) {
        if (msg == null) {
            return "";
        }
        String trimmed = msg;
        // Strip trailing newlines / whitespace
        int end = trimmed.length();
        while (end > 0) {
            char c = trimmed.charAt(end - 1);
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
                end--;
            } else {
                break;
            }
        }
        trimmed = trimmed.substring(0, end);

        StringBuilder sb = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '\n' || c == '\t') {
                sb.append(c);
            } else if (c < 0x20 || c == 0x7F) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String describeFilters(String user, String type, String device,
                                          String port, Long from, Long to,
                                          SimpleDateFormat tsFmt) {
        StringBuilder sb = new StringBuilder();
        appendFilter(sb, "user", user);
        appendFilter(sb, "type", type);
        appendFilter(sb, "device", device);
        appendFilter(sb, "port", port);
        if (from != null) {
            appendFilter(sb, "from", tsFmt.format(new Date(from)));
        }
        if (to != null) {
            appendFilter(sb, "to", tsFmt.format(new Date(to)));
        }
        if (sb.length() == 0) {
            return "(none - all entries included)";
        }
        return sb.toString();
    }

    private static void appendFilter(StringBuilder sb, String name, String value) {
        if (value == null) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("  |  ");
        }
        sb.append(name).append('=').append(value);
    }
}
