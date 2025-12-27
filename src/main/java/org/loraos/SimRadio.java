package org.loraos;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SimRadio implements LoRaInterface {
    private final BlockingQueue<Packet> inbox = new LinkedBlockingQueue<>();

    @Override
    public void send(Packet packet) {
        // In echte sim zou je hier latency/loss en meerdere ontvangers doen
        inbox.add(packet);
    }

    @Override
    public Packet receiveBlocking() throws InterruptedException {
        return inbox.take();
    }
}
