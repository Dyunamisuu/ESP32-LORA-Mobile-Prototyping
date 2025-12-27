package org.loraos;

public class Simulation {
    public static void main(String[] args) throws Exception {
        LoRaChannel channel = new LoRaChannel();

        ChannelRadio radioA = new ChannelRadio(channel);
        ChannelRadio radioB = new ChannelRadio(channel);

        channel.register(radioA);
        channel.register(radioB);

        MeshNode nodeA = new MeshNode((byte) 1, radioA);
        MeshNode nodeB = new MeshNode((byte) 2, radioB);

        Thread tA = new Thread(nodeA, "Node-A");
        Thread tB = new Thread(nodeB, "Node-B");
        tA.start();
        tB.start();

        // Node A stuurt naar B
        nodeA.sendTo((byte) 2, "Hallo van A naar B!", (byte) 0, (byte) 3);

        Thread.sleep(2000); // laat routing gebeuren

        // Node B stuurt terug
        nodeB.sendTo((byte) 1, "Hallo terug van B!", (byte) 0, (byte) 3);

        Thread.sleep(1000);
        nodeA.stop();
        nodeB.stop();
        tA.join(100);
        tB.join(100);
    }
}
