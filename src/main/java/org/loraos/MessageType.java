package org.loraos;

public final class MessageType {
    public static final byte CHAT = 0;
    public static final byte CMD  = 1;
    public static final byte ACK  = 2;
    public static final byte KEY_EXCHANGE = 3;

    public static final byte FLAG_NO_FORWARD = 1 << 4;

    public static byte withNoForward(byte baseType) {
        return (byte) (baseType | FLAG_NO_FORWARD);
    }

    public static byte baseType(byte type) {
        return (byte) (type & 0x0F);
    }

    public static boolean isNoForward(byte type) {
        return (type & FLAG_NO_FORWARD) != 0;
    }

    private MessageType() {}
}
