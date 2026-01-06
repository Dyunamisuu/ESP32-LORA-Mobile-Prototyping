package org.loraos;

import com.fazecast.jSerialComm.SerialPort;

public class SerialLoRaInterface implements LoRaInterface {
    private SerialPort serialPort;
    private final byte[] readBuffer = new byte[512];

    public SerialLoRaInterface(String portName, int baudRate) throws Exception {
        serialPort = SerialPort.getCommPort(portName);
        serialPort.setBaudRate(baudRate);
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
        if (!serialPort.openPort()) {
            throw new RuntimeException("Cannot open serial port: " + portName);
        }
        System.out.println("Serial port opened: " + portName);
    }

    @Override
    public void send(Packet packet) throws Exception {
        byte[] data = packet.toBytes();
        serialPort.writeBytes(data, data.length);
        System.out.println("Serial TX: " + packet);
    }

    @Override
    public Packet receiveBlocking() throws InterruptedException {
        while (true) {
            int available = serialPort.bytesAvailable();
            if (available >= 7) {
                int read = serialPort.readBytes(readBuffer, 7);
                if (read >= 7) {
                    try {
                        Packet p = Packet.fromBytes(readBuffer);
                        return p;
                    } catch (IllegalArgumentException ignored) {

                    }
                }
            }
            Thread.sleep(10);
        }
    }

    public void close() {
        if (serialPort != null) {
            serialPort.closePort();
        }
    }
}
