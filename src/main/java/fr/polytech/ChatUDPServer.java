package fr.polytech;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class ChatUDPServer {
    private static final HashMap<String, Session> sessions = new HashMap<>();

    public static void main(String[] args) {

        try (DatagramSocket socket = new DatagramSocket(1234)) {
            System.out.println("Server is running on port 1234");

            while (true) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
                if (byteBuffer.getInt() != PacketType.HELLO.getId()) {
                    System.out.println("Received invalid packet type from new connection");
                    continue;
                }

                int nameLength = byteBuffer.getInt();
                String name = new String(byteBuffer.array(), byteBuffer.position(), nameLength);
                if (sessions.containsKey(name)) {
                    System.out.println("Rejecting connection using name " + name + " (already taken)");
                    DatagramPacket reply = new DatagramPacket(
                            ByteBuffer.allocate(4).putInt(PacketType.NAME_ALREADY_TAKEN.getId()).array(),
                            4,
                            packet.getAddress(),
                            packet.getPort()
                    );
                    socket.send(reply);
                    continue;
                }
                System.out.println("User " + name + " joined");

                Session session = new Session(
                        () -> sessions.remove(name),
                        (String bcMsg) -> broadcast(name, bcMsg),
                        (String target, String msg) -> sendPrivateMessage(name, target, msg),
                        ChatUDPServer::forgeUserListPacket
                );
                sessions.put(name, session);

                // send new port to client
                ByteBuffer portBuffer = ByteBuffer.allocate(8);
                portBuffer.putInt(PacketType.PORT.getId());
                portBuffer.putInt(session.getPort());
                DatagramPacket reply = new DatagramPacket(
                        portBuffer.array(),
                        portBuffer.position(),
                        packet.getAddress(),
                        packet.getPort()
                );
                socket.send(reply);

                // Notify other users
                ByteBuffer userListBuffer = forgeUserListPacket();
                sessions.values().forEach((s) -> {
                    s.send(userListBuffer);
                });
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void broadcast(String username, String message) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putInt(PacketType.BROADCAST.getId());

        byte[] byteMessage = message.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(byteMessage.length);
        buffer.put(message.getBytes(StandardCharsets.UTF_8));

        sessions.entrySet().stream()
                .filter((entry) -> !entry.getKey().equals(username))
                .forEach((entry) -> entry.getValue().send(buffer));
    }

    private static boolean sendPrivateMessage(String username, String target, String message) {
        if (!sessions.containsKey(username)) {
            return false;
        }

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putInt(PacketType.PRIVATE.getId());
        byte[] byteUsername = username.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(byteUsername.length);
        buffer.put(byteUsername);
        byte[] byteMessage = message.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(byteMessage.length);
        buffer.put(byteMessage);

        sessions.get(target).send(buffer);

        return true;
    }

    private static ByteBuffer forgeUserListPacket() {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putInt(PacketType.USER_LIST.getId());
        buffer.put(String.join(",", sessions.keySet()).getBytes());

        return buffer;
    }
}
