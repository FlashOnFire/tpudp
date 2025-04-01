package fr.polytech;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Utils {
    public static void putString(ByteBuffer buffer, String string) {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
    }

    public static String extractString(ByteBuffer buffer) {
        int length = buffer.getInt();
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
