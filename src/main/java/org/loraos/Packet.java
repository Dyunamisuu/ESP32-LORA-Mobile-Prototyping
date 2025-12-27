package org.loraos;

public class Packet {
    public final byte srcId;
    public final byte dstId;
    public final byte type;
    public final byte ttl;
    public final int seq;
    public final byte[] payload;

    public Packet(byte srcId, byte dstId, byte type, byte ttl, int seq, byte[] payload) {
        this.srcId = srcId;
        this.dstId = dstId;
        this.type = type;
        this.ttl = ttl;
        this.seq = seq;
        this.payload = payload;
    }

    public static Packet fromBytes(byte[] data) {
        if (data.length < 7) throw new IllegalArgumentException("packet too short");
        byte srcId = data[0];
        byte dstId = data[1];
        byte type  = data[2];
        byte ttl   = data[3];
        int seq    = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
        int len    = data[6] & 0xFF;
        if (data.length < 7 + len) throw new IllegalArgumentException("payload len mismatch");
        byte[] payload = new byte[len];
        System.arraycopy(data, 7, payload, 0, len);
        return new Packet(srcId, dstId, type, ttl, seq, payload);
    }

    public byte[] toBytes() {
        int len = payload == null ? 0 : payload.length;
        byte[] data = new byte[7 + len];
        data[0] = srcId;
        data[1] = dstId;
        data[2] = type;
        data[3] = ttl;
        data[4] = (byte) ((seq >> 8) & 0xFF);
        data[5] = (byte) (seq & 0xFF);
        data[6] = (byte) len;
        if (len > 0) {
            System.arraycopy(payload, 0, data, 7, len);
        }
        return data;
    }

    @Override
    public String toString() {
        String pl = payload == null ? "" : new String(payload);
        return "Packet{" +
                "src=" + (srcId & 0xFF) +
                ", dst=" + (dstId & 0xFF) +
                ", type=" + (type & 0xFF) +
                ", ttl=" + (ttl & 0xFF) +
                ", seq=" + seq +
                ", payload='" + pl + '\'' +
                '}';
    }
}
