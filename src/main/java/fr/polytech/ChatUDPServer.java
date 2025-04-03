package fr.polytech;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A UDP-based chat server that allows multiple clients to connect, join rooms, and communicate.
 * <p>
 * This server manages user sessions, chat rooms, and handles various message types including
 * broadcasts, private messages, and room-specific messages.
 * It maintains collections of active user sessions and available chat rooms with thread-safe data structures.
 * <p>
 * The server listens on port 1234 and assigns a separate port for each user session to avoid bottlenecks
 */
public class ChatUDPServer {
    /**
     * The default room that users join when connecting to the server
     */
    private static final String baseRoom = "general";

    /**
     * List of all currently available chat rooms in the server
     */
    private static final List<String> rooms = Collections.synchronizedList(new ArrayList<>());
    /**
     * Map of all active user sessions, indexed by username
     */
    private static final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        // Add the default room to the list of rooms
        rooms.add(baseRoom);

        // Create main server socket
        try (DatagramSocket socket = new DatagramSocket(1234)) {
            System.out.println("Server is running on port 1234");

            while (true) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Received a packet
                ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
                if (byteBuffer.getInt() != PacketType.HELLO.getId()) {
                    System.out.println("Received invalid packet type from new connection");
                    continue;
                }

                // The packet should contain the username
                String name = Utils.extractString(byteBuffer);

                // If username is already taken, reject the connection
                if (sessions.containsKey(name)) {
                    System.out.println("Rejecting connection using name " + name + " (already taken)");
                    DatagramPacket usernameTakenPacket = new DatagramPacket(
                            ByteBuffer.allocate(4).putInt(PacketType.NAME_ALREADY_TAKEN.getId()).array(),
                            4,
                            packet.getAddress(),
                            packet.getPort()
                    );
                    socket.send(usernameTakenPacket);
                    continue;
                }

                // Create a new session for the user
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

                // Send new port to the user
                ByteBuffer portBuffer = ByteBuffer.allocate(8);
                portBuffer.putInt(PacketType.PORT.getId());
                portBuffer.putInt(session.getPort());
                DatagramPacket portPacket = new DatagramPacket(
                        portBuffer.array(),
                        portBuffer.position(),
                        packet.getAddress(),
                        packet.getPort()
                );
                socket.send(portPacket);

                // Resend updated user list to everyone except the new user
                // (will be sent automatically to the new user once we receive the initial heartbeat)
                ByteBuffer userListBuffer = forgeUserListPacket();
                sessions.entrySet()
                        .stream()
                        .filter((entry) -> !entry.getKey().equals(name))
                        .forEach((entry) -> entry.getValue().send(userListBuffer));
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Sends a broadcast message to all connected clients.
     * Creates a packet with the BROADCAST packet type and sends it to every active session.
     *
     * @param message The message to broadcast to all connected users
     */
    private static void broadcast(String message) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putInt(PacketType.BROADCAST.getId());
        Utils.putString(buffer, message);

        sessions.values()
                .forEach((session) -> session.send(buffer));
    }

    /**
     * Sends a private message from one user to another.
     * Creates a packet with the PRIVATE packet type and sends it to the target user.
     *
     * @param username The name of the user sending the message
     * @param target   The name of the user receiving the message
     * @param message  The content of the private message
     * @return true if the message was sent successfully, false otherwise
     */
    private static boolean sendPrivateMessage(String username, String target, String message) {
        if (!sessions.containsKey(username)) {
            return false;
        }

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putInt(PacketType.PRIVATE.getId());
        Utils.putString(buffer, username);
        Utils.putString(buffer, message);

        sessions.get(target).send(buffer);

        return true;
    }

    private static ByteBuffer forgeUserListPacket() {
        String userList = String.join(",", sessions.keySet());

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putInt(PacketType.USER_LIST.getId());
        Utils.putString(buffer, userList);

        return buffer;
    }

    /**
     * Creates a packet containing the list of all available chat rooms.
     * <p>
     * This method formats the room names into a comma-separated string and
     * constructs a ByteBuffer containing a ROOM_LIST packet that can be
     * sent to clients.
     *
     * @return A ByteBuffer containing the ROOM_LIST packet with all room names
     */
    private static ByteBuffer forgeRoomListPacket() {
        String roomList = String.join(",", rooms);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putInt(PacketType.ROOM_LIST.getId());
        Utils.putString(buffer, roomList);

        return buffer;
    }

    /**
     * Creates a new chat room if it doesn't already exist.
     * <p>
     * This method adds the specified room name to the list of available chat rooms
     * and notifies all connected clients about the updated room list.
     *
     * @param room The name of the room to create
     * @return true if the room was created successfully, false if the room already exists
     */
    private static boolean createRoom(String room) {
        if (rooms.contains(room)) {
            return false;
        }

        rooms.add(room);
        ByteBuffer buffer = forgeRoomListPacket();
        sessions.values().forEach((s) -> s.send(buffer));

        return true;
    }

    /**
     * Deletes an existing chat room and moves all users in that room to the default room.
     * <p>
     * This method checks if the specified room exists, and if it does:
     * 1. Moves all users currently in that room to the base room
     * 2. Removes the room from the list of available rooms
     * 3. Notifies all connected clients about the updated room list
     *
     * @param room The name of the room to delete
     * @return true if the room was successfully deleted, false if the room doesn't exist
     */
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

    /**
     * Sends a message to all users in a specific chat room.
     * <p>
     * This method creates a ROOM_MESSAGE packet containing the sender's username and message content,
     * then sends it to all users who are currently in the specified room.
     * If the room doesn't exist, the method returns without sending any message.
     *
     * @param username The name of the user sending the message
     * @param room     The name of the room where the message should be sent
     * @param message  The content of the message to be sent
     */
    private static void sendRoomMessage(String username, String room, String message) {
        if (!rooms.contains(room)) {
            return;
        }

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putInt(PacketType.ROOM_MESSAGE.getId());
        Utils.putString(buffer, username);
        Utils.putString(buffer, message);

        sessions.values()
                .stream()
                .filter(s -> s.getCurrentRoom().equals(room))
                .forEach((s) -> s.send(buffer));
    }

    /**
     * Switches a user from their current chat room to another room.
     * <p>
     * This method handles the entire room switching process:
     * 1. Verifies the target room exists
     * 2. Notifies users in the old room that the user has left
     * 3. Updates the user's current room
     * 4. Sends a ROOM_SWITCH packet to the user
     * 5. Notifies users in the new room that the user has joined
     *
     * @param username The name of the user to be moved
     * @param room     The name of the room the user shall be moved to
     */
    private static void switchRoom(String username, String room) {
        if (!rooms.contains(room)) {
            return;
        }

        Session session = sessions.get(username);

        sendRoomMessage("Server", session.getCurrentRoom(), username + " left this room");
        sessions.get(username).setCurrentRoom(room);
        session.send(forgeRoomSwitchPacket(room));
        sendRoomMessage("Server", room, username + " joined this room");
    }

    /**
     * Creates a packet for notifying a client about a room switch.
     * <p>
     * This method constructs a ByteBuffer containing a ROOM_SWITCH packet
     * with the name of the room the user has been switched to. This packet
     * is sent to the client to update their current room state.
     *
     * @param room The name of the room the user shall be moved to
     * @return A ByteBuffer containing the ROOM_SWITCH packet with room name
     */
    private static ByteBuffer forgeRoomSwitchPacket(String room) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putInt(PacketType.ROOM_SWITCH.getId());

        Utils.putString(buffer, room);

        return buffer;
    }
}
