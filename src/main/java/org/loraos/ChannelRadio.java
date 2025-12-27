package org.loraos;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ChannelRadio implements LoRaInterface {
    private final LoRaChannel channel;
    private final BlockingQueue<Packet> inbox = new LinkedBlockingQueue<>();

    public ChannelRadio(LoRaChannel channel) {
        this.channel = channel;
    }

    @Override
    public void send(Packet packet) throws Exception {
        channel.sendFrom(this, packet);
    }

    @Override
    public Packet receiveBlocking() throws InterruptedException {
        return inbox.take();
    }

    public void deliver(Packet packet) {
        inbox.add(packet);
    }
}
