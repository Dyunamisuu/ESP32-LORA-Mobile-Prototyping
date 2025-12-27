package org.loraos;

import java.util.concurrent.CopyOnWriteArrayList;

public class LoRaChannel {
    // Alle radios (ChannelRadio) die op dit kanaal zitten
    private final CopyOnWriteArrayList<ChannelRadio> radios = new CopyOnWriteArrayList<>();

    // Simulatieparameters
    private final double packetLoss = 0.1; // 10% kans dat een packet wegvalt
    private final long avgLatencyMs = 50;  // gemiddelde latency ~50ms

    public void register(ChannelRadio radio) {
        radios.add(radio);
    }

    // Wordt aangeroepen door een ChannelRadio wanneer die een packet wil uitzenden
    public void sendFrom(ChannelRadio sender, Packet packet) {
        new Thread(() -> {
            try {
                // Simuleer variabele latency
                Thread.sleep((long) (Math.random() * avgLatencyMs * 2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // Stuur naar alle andere radios op het kanaal
            for (ChannelRadio receiver : radios) {
                if (receiver == sender) continue;

                // Simuleer packet loss
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
