package org.loraos;

import java.util.concurrent.CopyOnWriteArrayList;

public class LoRaChannel {

    private final CopyOnWriteArrayList<ChannelRadio> radios = new CopyOnWriteArrayList<>();

    private final double packetLoss = 0.1;
    private final long avgLatencyMs = 50;

    public void register(ChannelRadio radio) {
        radios.add(radio);
    }

    public void sendFrom(ChannelRadio sender, Packet packet) {
        new Thread(() -> {
            try {
                Thread.sleep((long) (Math.random() * avgLatencyMs * 2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            for (ChannelRadio receiver : radios) {
                if (receiver == sender) continue;

                if (Math.random() > packetLoss) {
                    try {
                        receiver.deliver(packet);
                    } catch (Exception e) {
                        System.err.println("Failed to deliver packet: " + e.getMessage());
                    }
                }
            }
        }).start();
    }
}
