package fr.polytech;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.function.*;

/**
 * Represents a user session in the chat application.
 * <p>
 * A Session maintains the UDP connection (through heartbeat system) with a client and handles all incoming
 * packets from that client.
 * <p>
 * Each Session corresponds to one unique connected user and manages their current room,
 * message routing, and connection status.
 */
public class Session {
    /**
     * Socket for UDP communication with the client
     */
    private DatagramSocket socket;

    /**
     * Client's IP address (set after first heartbeat)
     */
    private InetAddress address;
    /**
     * Client's port number for sending responses (set after first heartbeat)
     */
    private int destinationPort;

    /**
     * Flag indicating if initial heartbeat has already been received
     */
    private boolean firstHeartbeatReceived = false;

    /**
     * Name of the chat room where the user is currently active
     */
    private String currentRoom;
    /**
     * Immutable username of the client associated with this session
     */
    private final String name;

    /**
     * Creates a new Session for a client connection.
     * <p>
     * This constructor initializes a UDP socket for communication with the client and starts
     * a daemon thread to handle incoming packets. The session processes different packet types
     * including heartbeats, broadcasts, private messages, room management, and more.
     *
     * @param name               The username of the client
     * @param firstRoom          The initial room the client joins
     * @param sessionTimeoutHook Hook to execute when the session times out
     * @param broadcastHook      Hook used to broadcast messages
     * @param privateMessageHook Hook to handle private messages between users (returns success/failure)
     * @param userListSupplier   Supplier that provides the current user list as a ByteBuffer
     * @param roomListSupplier   Supplier that provides the current room list as a ByteBuffer
     * @param roomCreationHook   Hook to handle room creation requests (returns success/failure)
     * @param roomDeletionHook   Hook to handle room deletion requests (returns success/failure)
     * @param roomMessageHook    Hook to handle messages sent to a specific room
     * @param roomSwitchHook     Hook to handle room switching operations
     */
    public Session(
            String name,
            String firstRoom,
            Runnable sessionTimeoutHook,
            Consumer<String> broadcastHook,
            BiPredicate<String, String> privateMessageHook,
            Supplier<ByteBuffer> userListSupplier,
            Supplier<ByteBuffer> roomListSupplier,
            Predicate<String> roomCreationHook,
            Predicate<String> roomDeletionHook,
            BiConsumer<String, String> roomMessageHook,
            Consumer<String> roomSwitchHook) {
        this.name = name;
        this.currentRoom = firstRoom;

        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(10000);
        } catch (Exception e) {
            e.printStackTrace();
            sessionTimeoutHook.run();
            socket.close();
        }

        // Start a new thread to handle incoming packets without blocking the main thread
        Thread thread = new Thread(() -> {
            try {
                while (true) {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    ByteBuffer bb = ByteBuffer.wrap(packet.getData());
                    int packetType = bb.getInt();
                    if (packetType == PacketType.HEARTBEAT.getId()) {
                        if (!firstHeartbeatReceived) {
                            address = packet.getAddress();
                            destinationPort = packet.getPort();

                            send(userListSupplier.get());
                            send(roomListSupplier.get());

                            System.out.println("Received first heartbeat from " + address + ":" + destinationPort);

                            ByteBuffer buf = ByteBuffer.allocate(1024);
                            buf.putInt(PacketType.ROOM_SWITCH.getId());
                            byte[] roomNameBytes = currentRoom.getBytes();
                            buf.putInt(roomNameBytes.length);
                            buf.put(roomNameBytes);

                            send(buf);

                            firstHeartbeatReceived = true;
                        }
                    } else if (packetType == PacketType.BROADCAST.getId()) {
                        String message = Utils.extractString(bb);
                        broadcastHook.accept(message);
                    } else if (packetType == PacketType.PRIVATE.getId()) {
                        String recipient = Utils.extractString(bb);
                        String message = Utils.extractString(bb);

                        if (privateMessageHook.test(recipient, message)) {
                            System.out.println("Message sent to " + recipient);
                        } else {
                            System.out.println("Failed to send message to " + recipient);
                        }
                    } else if (packetType == PacketType.CREATE_ROOM.getId()) {
                        String roomName = Utils.extractString(bb);

                        if (roomCreationHook.test(roomName)) {
                            System.out.println("Room " + roomName + " created");
                        } else {
                            System.out.println("Failed to create room " + roomName);
                        }
                    } else if (packetType == PacketType.DELETE_ROOM.getId()) {
                        String roomName = Utils.extractString(bb);

                        if (roomDeletionHook.test(roomName)) {
                            System.out.println("Room " + roomName + " deleted");
                        } else {
                            System.out.println("Failed to delete room " + roomName);
                        }
                    } else if (packetType == PacketType.ROOM_MESSAGE.getId()) {
                        String message = Utils.extractString(bb);
                        roomMessageHook.accept(currentRoom, message);
                    } else if (packetType == PacketType.ROOM_SWITCH.getId()) {
                        String roomName = Utils.extractString(bb);
                        roomSwitchHook.accept(roomName);
                    } else {
                        System.out.println("Received invalid packet type");
                    }
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Session timed out");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                socket.close();
                sessionTimeoutHook.run();
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Sends a message to the client using the established UDP socket.
     * This method verifies that a client connection has been established
     * (via heartbeat) before attempting to send data.
     *
     * @param buffer The ByteBuffer containing the data to be sent to the client.
     */
    public void send(ByteBuffer buffer) {
        if (address == null || destinationPort == 0) {
            // First heartbeat not received yet, cannot send data
            return;
        }

        try {
            DatagramPacket packet = new DatagramPacket(
                    buffer.array(),
                    buffer.position(),
                    address,
                    destinationPort
            );
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the local port number to which this session's socket is bound.
     *
     * @return the local port number to which this socket is bound
     */
    public int getPort() {
        return socket.getLocalPort();
    }

    /**
     * Returns the name of the session user.
     *
     * @return the name of the user associated with this session
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the current room where the user is located.
     *
     * @return the name of the room the user is currently in
     */
    public String getCurrentRoom() {
        return currentRoom;
    }

    /**
     * Sets the current room for this session.
     * Updates the room where the user is currently located.
     * (does not send a packet to the client, only updates the local state)
     *
     * @param room the name of the room to set as current
     */
    public void setCurrentRoom(String room) {
        currentRoom = room;
    }
}
