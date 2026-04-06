package com.anchor.models;

import com.fazecast.jSerialComm.SerialPort;
import java.util.UUID;

/**
 * Serial port connection implementation.
 *
 * Demonstrates:
 * - Inheritance (extends Connection)
 * - jSerialComm library usage
 * - Resource management
 *
 * NOTE: Prototype implementation - exploring jSerialComm integration
 * TODO: Add proper error handling and reconnection logic
 */
public class SerialConnection extends Connection {

    private String portName;
    private int baudRate;
    private int dataBits;
    private int stopBits;
    private int parity;

    private SerialPort serialPort;

    public SerialConnection(String portName, int baudRate) {
        this.portName = portName;
        this.baudRate = baudRate;
        this.dataBits = 8;
        this.stopBits = SerialPort.ONE_STOP_BIT;
        this.parity = SerialPort.NO_PARITY;
        this.connectionId = UUID.randomUUID().toString();
    }

    @Override
    public boolean connect() {
        try {
            serialPort = SerialPort.getCommPort(portName);
            serialPort.setBaudRate(baudRate);
            serialPort.setNumDataBits(dataBits);
            serialPort.setNumStopBits(stopBits);
            serialPort.setParity(parity);

            // Set timeouts
            serialPort.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                100, // Read timeout
                0    // Write timeout
            );

            if (serialPort.openPort()) {
                connected = true;
                connectedAt = System.currentTimeMillis();
                System.out.println("[Anchor] Serial connected: " + portName);
                return true;
            }
        } catch (Exception e) {
            System.err.println("[Anchor] Serial connect error: " + e.getMessage());
        }
        return false;
    }

    @Override
    public void disconnect() {
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
            System.out.println("[Anchor] Serial disconnected: " + portName);
        }
        connected = false;
    }

    @Override
    public boolean send(byte[] data) {
        if (!connected || serialPort == null) return false;

        int written = serialPort.writeBytes(data, data.length);
        return written == data.length;
    }

    @Override
    public byte[] receive() {
        if (!connected || serialPort == null) return new byte[0];

        int available = serialPort.bytesAvailable();
        if (available <= 0) return new byte[0];

        byte[] buffer = new byte[available];
        serialPort.readBytes(buffer, available);
        return buffer;
    }

    @Override
    public String getType() {
        return "SERIAL";
    }

    // Getters for serial-specific properties
    public String getPortName() { return portName; }
    public int getBaudRate() { return baudRate; }
}
