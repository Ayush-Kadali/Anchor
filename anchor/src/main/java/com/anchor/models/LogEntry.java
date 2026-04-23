package com.anchor.models;

/*
 * LogEntry - Data class for a single log record.
 *
 * Each entry represents either an input (command typed by owner)
 * or an output (data received from the device).
 *
 * Fields capture metadata needed for filtering:
 *   - timestamp: when this happened
 *   - username: who caused this (owner for INPUT, null for OUTPUT)
 *   - direction: INPUT or OUTPUT
 *   - message: the actual text
 *   - connectionType: SERIAL or SSH (for filtering)
 *   - deviceName: descriptive name (for filtering)
 *   - portOrHost: port identifier for serial, host:port for SSH
 *
 * Java concept: Plain data class (POJO) with encapsulation.
 */
public class LogEntry {

    private long timestamp;
    private String username;
    private String direction;        // "INPUT" or "OUTPUT"
    private String message;
    private String connectionType;   // "SERIAL" or "SSH"
    private String deviceName;
    private String portOrHost;

    public LogEntry(long timestamp, String username, String direction,
                    String message, String connectionType,
                    String deviceName, String portOrHost) {
        this.timestamp = timestamp;
        this.username = username;
        this.direction = direction;
        this.message = message;
        this.connectionType = connectionType;
        this.deviceName = deviceName;
        this.portOrHost = portOrHost;
    }

    public long getTimestamp() { return timestamp; }
    public String getUsername() { return username; }
    public String getDirection() { return direction; }
    public String getMessage() { return message; }
    public String getConnectionType() { return connectionType; }
    public String getDeviceName() { return deviceName; }
    public String getPortOrHost() { return portOrHost; }
}
