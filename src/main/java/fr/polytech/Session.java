package fr.polytech;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.function.*;

public class Session {
    private DatagramSocket socket;

    private InetAddress address;
    private int destPort;

    private boolean firstHeartbeat = false;

    private String currentRoom;
    private final String name;

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

        Thread thread = new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    ByteBuffer bb = ByteBuffer.wrap(packet.getData());
                    int packetType = bb.getInt();
                    if (packetType == PacketType.HEARTBEAT.getId()) {
                        if (!firstHeartbeat) {
                            address = packet.getAddress();
                            destPort = packet.getPort();

                            send(userListSupplier.get());
                            send(roomListSupplier.get());

                            System.out.println("Received first heartbeat from " + address + ":" + destPort);

                            ByteBuffer buf = ByteBuffer.allocate(1024);
                            buf.putInt(PacketType.ROOM_SWITCH.getId());
                            byte[] roomNameBytes = currentRoom.getBytes();
                            buf.putInt(roomNameBytes.length);
                            buf.put(roomNameBytes);

                            send(buf);

                            firstHeartbeat = true;
                        }
                    } else if (packetType == PacketType.BROADCAST.getId()) {
                        int messageLength = bb.getInt();
                        byte[] messageBytes = new byte[messageLength];
                        bb.get(messageBytes);
                        String message = new String(messageBytes);
                        broadcastHook.accept(message);
                    } else if (packetType == PacketType.PRIVATE.getId()) {
                        int recipientLength = bb.getInt();
                        byte[] recipientBytes = new byte[recipientLength];
                        bb.get(recipientBytes);
                        String recipient = new String(recipientBytes);

                        int messageLength = bb.getInt();
                        byte[] messageBytes = new byte[messageLength];
                        bb.get(messageBytes);
                        String message = new String(messageBytes);

                        if (privateMessageHook.test(recipient, message)) {
                            System.out.println("Message sent to " + recipient);
                        } else {
                            System.out.println("Failed to send message to " + recipient);
                        }
                    } else if (packetType == PacketType.CREATE_ROOM.getId()) {
                        int roomNameLength = bb.getInt();
                        byte[] roomNameBytes = new byte[roomNameLength];
                        bb.get(roomNameBytes);
                        String roomName = new String(roomNameBytes);

                        if (roomCreationHook.test(roomName)) {
                            System.out.println("Room " + roomName + " created");
                        } else {
                            System.out.println("Failed to create room " + roomName);
                        }
                    } else if (packetType == PacketType.DELETE_ROOM.getId()) {
                        int roomNameLength = bb.getInt();
                        byte[] roomNameBytes = new byte[roomNameLength];
                        bb.get(roomNameBytes);
                        String roomName = new String(roomNameBytes);

                        if (roomDeletionHook.test(roomName)) {
                            System.out.println("Room " + roomName + " deleted");
                        } else {
                            System.out.println("Failed to delete room " + roomName);
                        }
                    } else if (packetType == PacketType.ROOM_MESSAGE.getId()) {
                        int messageLength = bb.getInt();
                        byte[] messageBytes = new byte[messageLength];
                        bb.get(messageBytes);
                        String message = new String(messageBytes);
                        roomMessageHook.accept(currentRoom, message);
                    } else if (packetType == PacketType.ROOM_SWITCH.getId()) {
                        int roomNameLength = bb.getInt();
                        byte[] roomNameBytes = new byte[roomNameLength];

                        bb.get(roomNameBytes);
                        String roomName = new String(roomNameBytes);
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

        thread.start();
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    public void send(ByteBuffer buffer) {
        if (address == null || destPort == 0) {
            System.out.println(
                    "No destination address or port set (probably didnt receive the first heartbeat yet), not sending msg..");
            return;
        }

        try {
            DatagramPacket packet = new DatagramPacket(
                    buffer.array(),
                    buffer.position(),
                    address,
                    destPort
            );
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getName() {
        return name;
    }

    public String getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(String room) {
        currentRoom = room;
    }
}
