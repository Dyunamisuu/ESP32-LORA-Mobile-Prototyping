package org.loraos;

public interface LoRaInterface {
    void send(Packet packet) throws Exception;
    Packet receiveBlocking() throws Exception;
}
