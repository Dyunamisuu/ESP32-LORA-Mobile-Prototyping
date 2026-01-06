package org.loraos;

public class Simulation {
    public static void main(String[] args) throws Exception {
        System.out.println("LoRa Mesh with ECDH Key Exchange + Ratcheting\n");

        LoRaChannel channel = new LoRaChannel();

        ChannelRadio radioA = new ChannelRadio(channel);
        ChannelRadio radioB = new ChannelRadio(channel);
        ChannelRadio radioC = new ChannelRadio(channel);
        channel.register(radioA);
        channel.register(radioB);
        channel.register(radioC);

        MeshNode nodeA = new MeshNode((byte) 1, radioA);
        MeshNode nodeB = new MeshNode((byte) 2, radioB);
        MeshNode nodeC = new MeshNode((byte) 3, radioC);

        Thread tA = new Thread(nodeA, "Node-A");
        Thread tB = new Thread(nodeB, "Node-B");
        Thread tC = new Thread(nodeC, "Node-C");
        tA.start();
        tB.start();
        tC.start();

        Thread.sleep(500);

        System.out.println("=== Test 1: Key Exchange + Multiple Messages (A -> B) ===");
        nodeA.sendChat((byte) 2, "First message", true);
        Thread.sleep(300);
        nodeA.sendChat((byte) 2, "Second message", true);
        Thread.sleep(300);
        nodeA.sendChat((byte) 2, "Third message", true);

        Thread.sleep(1000);

        System.out.println("\n=== Test 2: Mesh Routing (A -> C via B) ===");
        nodeA.sendChat((byte) 3, "Hello node 3 via mesh!", false);

        Thread.sleep(1000);

        System.out.println("\n=== Test 3: Reverse Direction (B -> A) ===");
        nodeB.sendTo((byte) 1, "PING", MessageType.CMD, (byte) 5);

        Thread.sleep(1500);
        

        nodeA.stop();
        nodeB.stop();
        nodeC.stop();
        tA.interrupt();
        tB.interrupt();
        tC.interrupt();
        tA.join(500);
        tB.join(500);
        tC.join(500);
    }
}
