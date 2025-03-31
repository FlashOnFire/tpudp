package fr.polytech;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class ChatUDPServer {
    private static final String baseRoom = "general";
    private static final ArrayList<String> rooms = new ArrayList<>();
    private static final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        rooms.add(baseRoom);

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
                        name,
                        baseRoom,
                        () -> sessions.remove(name),
                        ChatUDPServer::broadcast,
                        (String target, String msg) -> sendPrivateMessage(name, target, msg),
                        ChatUDPServer::forgeUserListPacket,
                        ChatUDPServer::forgeRoomListPacket,
                        (String room) -> {
                            if (createRoom(room)) {
                                switchRoom(name, room);
                                return true;
                            }
                            return false;
                        },
                        ChatUDPServer::deleteRoom,
                        (String room, String message) -> ChatUDPServer.sendRoomMessage(name, room, message),
                        (String roomName) -> switchRoom(name, roomName)
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
                sessions.entrySet().stream().filter((entry) -> !entry.getKey().equals(name))
                        .forEach((entry) -> entry.getValue().send(userListBuffer));
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void broadcast(String message) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putInt(PacketType.BROADCAST.getId());

        byte[] byteMessage = message.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(byteMessage.length);
        buffer.put(message.getBytes(StandardCharsets.UTF_8));

        sessions.values()
                .forEach((session) -> session.send(buffer));
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
        String userList = String.join(",", sessions.keySet());
        byte[] userListBytes = userList.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putInt(PacketType.USER_LIST.getId());
        buffer.putInt(userListBytes.length);
        buffer.put(userListBytes);

        return buffer;
    }

    private static ByteBuffer forgeRoomListPacket() {
        String roomList = String.join(",", rooms);
        byte[] roomListBytes = roomList.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putInt(PacketType.ROOM_LIST.getId());
        buffer.putInt(roomListBytes.length);
        buffer.put(roomListBytes);

        return buffer;
    }

    private static boolean createRoom(String room) {
        if (rooms.contains(room)) {
            return false;
        }

        rooms.add(room);
        ByteBuffer buffer = forgeRoomListPacket();
        sessions.values().forEach((s) -> s.send(buffer));

        return true;
    }

    private static boolean deleteRoom(String room) {
        if (!rooms.contains(room)) {
            return false;
        }

        sessions.values().stream()
                .filter((session) -> session.getCurrentRoom().equals(room))
                .forEach(session -> switchRoom(session.getName(), baseRoom));

        rooms.remove(room);
        ByteBuffer buffer = forgeRoomListPacket();
        sessions.values().forEach((s) -> s.send(buffer));

        return true;
    }

    private static void sendRoomMessage(String username, String room, String message) {
        if (!rooms.contains(room)) {
            return;
        }

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putInt(PacketType.ROOM_MESSAGE.getId());
        byte[] byteUsername = username.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(byteUsername.length);
        buffer.put(byteUsername);

        byte[] byteMessage = message.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(byteMessage.length);
        buffer.put(byteMessage);

        sessions.entrySet()
                .stream().filter(entry -> !entry.getKey().equals(username))
                .filter(entry -> entry.getValue().getCurrentRoom().equals(room))
                .forEach((entry) -> entry.getValue().send(buffer));
    }

    private static void switchRoom(String username, String room) {
        if (!rooms.contains(room)) {
            return;
        }

        Session session = sessions.get(username);

        sendRoomMessage("Server", session.getCurrentRoom(), username + " left this room");
        sessions.get(username).setCurrentRoom(room);
        sendRoomMessage("Server", room, username + " joined this room");

        session.send(forgeRoomSwitchPacket(room));
    }

    private static ByteBuffer forgeRoomSwitchPacket(String room) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putInt(PacketType.ROOM_SWITCH.getId());

        byte[] byteRoom = room.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(byteRoom.length);
        buffer.put(byteRoom);

        return buffer;
    }
}
