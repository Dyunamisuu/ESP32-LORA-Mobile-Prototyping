package org.loraos;

public class Simulation {
    public static void main(String[] args) throws Exception {
        // 1. Kanaal voor simulatie nodes
        LoRaChannel channel = new LoRaChannel();

        // 2. Drie radios op hetzelfde kanaal
        ChannelRadio radioA = new ChannelRadio(channel);
        ChannelRadio radioB = new ChannelRadio(channel);
        ChannelRadio radioC = new ChannelRadio(channel);
        channel.register(radioA);
        channel.register(radioB);
        channel.register(radioC);

        // 3. Drie MeshNodes (met encryptie in MeshNode)
        MeshNode nodeA = new MeshNode((byte) 1, radioA);
        MeshNode nodeB = new MeshNode((byte) 2, radioB);
        MeshNode nodeC = new MeshNode((byte) 3, radioC);

        // 4. Threads starten
        Thread tA = new Thread(nodeA, "Node-A");
        Thread tB = new Thread(nodeB, "Node-B");
        Thread tC = new Thread(nodeC, "Node-C");
        tA.start();
        tB.start();
        tC.start();

        Thread.sleep(1000);

        // 5. Test: A -> C via B (CHAT, mag hoppen)
        System.out.println("=== A -> C (mesh chat, via B) ===");
        nodeA.sendChat((byte) 3, "Hallo via B naar C!", false); // false = forwarden toegestaan

        Thread.sleep(1000);

        // 6. Test: A -> B privé (NO_FORWARD)
        System.out.println("=== A -> B (private chat, NO_FORWARD) ===");
        nodeA.sendChat((byte) 2, "Privé bericht voor B", true); // true = NO_FORWARD

        Thread.sleep(1000);

        // 7. Test: B -> A (CMD: PING, mag hoppen)
        System.out.println("=== B -> A (CMD: PING) ===");
        nodeB.sendTo((byte) 1, "PING", MessageType.CMD, (byte) 5);

        Thread.sleep(2000);

        // 8. Cleanup
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
