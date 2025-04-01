package fr.polytech;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

public class ChatUDPClient {

    public static void main(String[] args) {

        System.out.println("Enter your name (max 32chars): ");
        Scanner scanner = new Scanner(System.in);
        String input;
        do {
            input = scanner.nextLine();
        } while (input.isBlank() || input.length() > 32);

        try (DatagramSocket socket = new DatagramSocket()) {
            System.out.println("Client is running ");

            // Send hello packet
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            byteBuffer.putInt(PacketType.HELLO.getId());
            Utils.putString(byteBuffer, input);

            DatagramPacket packet = new DatagramPacket(
                    byteBuffer.array(),
                    byteBuffer.position(),
                    InetAddress.getByName("localhost"),
                    1234
            );
            socket.send(packet);

            // Receive new communication port
            byte[] buffer = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            socket.receive(reply);
            byteBuffer = ByteBuffer.wrap(reply.getData(), 0, reply.getLength());
            int packetType = byteBuffer.getInt();

            if (packetType == PacketType.NAME_ALREADY_TAKEN.getId()) {
                System.out.println("Name already taken");
                return;
            } else if (packetType != PacketType.PORT.getId()) {
                System.out.println("Invalid packet type");
                System.out.println("Received: " + packetType);
                return;
            }

            int port = byteBuffer.getInt();
            System.out.println("Received new port: " + port);

            // Create heartbeat thread
            Thread heartbeat = new Thread(() -> {
                try {
                    ByteBuffer hbBuffer = ByteBuffer.allocate(4);
                    hbBuffer.putInt(PacketType.HEARTBEAT.getId());
                    DatagramPacket hbPacket = new DatagramPacket(
                            hbBuffer.array(),
                            hbBuffer.position(),
                            InetAddress.getByName("localhost"),
                            port
                    );

                    while (!socket.isClosed()) {
                        socket.send(hbPacket);
                        Thread.sleep(5000);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            heartbeat.setDaemon(true);

            ArrayList<String> userList = new ArrayList<>();
            ArrayList<String> roomList = new ArrayList<>();
            AtomicReference<String> currentRoom = new AtomicReference<>();

            // Create receiving thread
            Thread receivingThread = new Thread(() -> {
                try {
                    while (!socket.isClosed()) {
                        byte[] receiveBuffer = new byte[1024];
                        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                        socket.receive(receivePacket);
                        ByteBuffer receiveByteBuffer = ByteBuffer.wrap(
                                receivePacket.getData(),
                                0,
                                receivePacket.getLength()
                        );
                        PacketType type = PacketType.fromId(receiveByteBuffer.getInt());

                        switch (type) {
                            case BROADCAST:
                                System.out.println("[Broadcast]: " + Utils.extractString(receiveByteBuffer));
                                break;
                            case PRIVATE:
                                String recipient = Utils.extractString(receiveByteBuffer);
                                String privateMessage = Utils.extractString(receiveByteBuffer);

                                System.out.println("[" + recipient + " -> You]: " + privateMessage);
                                break;
                            case USER_LIST:
                                String userListStr = Utils.extractString(receiveByteBuffer);

                                userList.clear();
                                userList.addAll(List.of(userListStr.split(",")));

                                System.out.println("User list: " + userList);
                                break;
                            case ROOM_LIST:
                                String roomListStr = Utils.extractString(receiveByteBuffer);

                                roomList.clear();
                                roomList.addAll(List.of(roomListStr.split(",")));

                                System.out.println("Room list: " + roomList);
                                break;
                            case ROOM_SWITCH:
                                String roomName = Utils.extractString(receiveByteBuffer);

                                System.out.println("Joined room: " + roomName);
                                currentRoom.set(roomName);
                                break;
                            case ROOM_MESSAGE:
                                String username = Utils.extractString(receiveByteBuffer);
                                String roomMessage = Utils.extractString(receiveByteBuffer);

                                System.out.println(currentRoom + " - " + username + " :" + roomMessage);
                                break;
                            default:
                                System.out.println("Unknown packet type: " + type);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            receivingThread.setDaemon(true);

            // Start threads
            receivingThread.start();
            heartbeat.start();

            // Process user input
            while (true) {
                do {
                    input = scanner.nextLine();
                } while (input.isBlank() || input.length() > 32);

                if (input.startsWith("/")) {
                    if (input.equals("/help")) {
                        System.out.println("===== COMMANDS =====");
                        System.out.println("/bc <message>      - Broadcast message to all users");
                        System.out.println("/msg <user> <msg>  - Send private message to a specific user");
                        System.out.println("/room <name>       - Join an existing room");
                        System.out.println("/createroom <name> - Create a new room");
                        System.out.println("/deleteroom <name> - Delete an existing room");
                        System.out.println("/users             - Display all online users");
                        System.out.println("/rooms             - Display all available rooms");
                        System.out.println("/help              - Show this help message");
                        System.out.println("/quit              - Exit the chat application");
                        System.out.println("===================");
                    } else if (input.equals("/users")) {
                        System.out.println("===== ONLINE USERS =====");
                        for (String user : userList) {
                            System.out.println("- " + user);
                        }
                        System.out.println("=======================");
                    } else if (input.equals("/rooms")) {
                        System.out.println("======= ROOMS =======");
                        for (String user : roomList) {
                            System.out.println("- " + user);
                        }
                        System.out.println("=======================");
                    } else if (input.equals("/quit")) {
                        break;
                    } else if (input.startsWith("/msg")) {
                        String[] parts = input.split(" ", 3);
                        if (parts.length < 3) {
                            System.out.println("Usage: /msg <user> <message>");
                            continue;
                        }
                        String recipient = parts[1];
                        String message = parts[2];

                        if (!userList.contains(recipient)) {
                            System.out.println("User does not exist");
                            continue;
                        }

                        byteBuffer = ByteBuffer.allocate(1024);
                        byteBuffer.putInt(PacketType.PRIVATE.getId());
                        Utils.putString(byteBuffer, recipient);
                        Utils.putString(byteBuffer, message);

                        packet = new DatagramPacket(
                                byteBuffer.array(),
                                byteBuffer.position(),
                                InetAddress.getByName("localhost"),
                                port
                        );
                        socket.send(packet);

                        System.out.println("[You -> " + recipient + "]: " + message);
                    } else if (input.startsWith("/bc")) {
                        String message = input.substring(4);

                        if (message.isBlank()) {
                            System.out.println("Error: Message cannot be empty");
                            continue;
                        }

                        byteBuffer = ByteBuffer.allocate(1024);
                        byteBuffer.putInt(PacketType.BROADCAST.getId());
                        Utils.putString(byteBuffer, message);

                        packet = new DatagramPacket(
                                byteBuffer.array(),
                                byteBuffer.position(),
                                InetAddress.getByName("localhost"),
                                port
                        );
                        socket.send(packet);
                    } else if (input.startsWith("/room")) {
                        String roomName = input.substring(6);

                        if (roomName.isBlank()) {
                            System.out.println("Error: Room name cannot be empty");
                            continue;
                        }

                        if (currentRoom.get().equals(roomName)) {
                            System.out.println("You are already in this room");
                            continue;
                        }

                        byteBuffer = ByteBuffer.allocate(1024);
                        byteBuffer.putInt(PacketType.ROOM_SWITCH.getId());
                        Utils.putString(byteBuffer, roomName);

                        packet = new DatagramPacket(
                                byteBuffer.array(),
                                byteBuffer.position(),
                                InetAddress.getByName("localhost"),
                                port
                        );
                        socket.send(packet);
                    } else if (input.startsWith("/createroom")) {
                        String roomName = input.substring(12);

                        if (roomName.isBlank()) {
                            System.out.println("Error: Room name cannot be empty");
                            continue;
                        }

                        if (roomList.contains(roomName)) {
                            System.out.println("Room already exists");
                            continue;
                        }

                        byteBuffer = ByteBuffer.allocate(1024);
                        byteBuffer.putInt(PacketType.CREATE_ROOM.getId());
                        Utils.putString(byteBuffer, roomName);

                        packet = new DatagramPacket(
                                byteBuffer.array(),
                                byteBuffer.position(),
                                InetAddress.getByName("localhost"),
                                port
                        );
                        socket.send(packet);
                    } else if (input.startsWith("/deleteroom")) {
                        String roomName = input.substring(12);

                        if (roomName.isBlank()) {
                            System.out.println("Error: Room name cannot be empty");
                            continue;
                        }

                        if (!roomList.contains(roomName)) {
                            System.out.println("Room does not exist");
                            continue;
                        }

                        byteBuffer = ByteBuffer.allocate(1024);
                        byteBuffer.putInt(PacketType.DELETE_ROOM.getId());
                        Utils.putString(byteBuffer, roomName);

                        packet = new DatagramPacket(
                                byteBuffer.array(),
                                byteBuffer.position(),
                                InetAddress.getByName("localhost"),
                                port
                        );
                        socket.send(packet);
                    } else {
                        System.out.println("Unknown command (type /help for help)");
                    }
                } else {
                    // room message
                    if (currentRoom.get() == null) {
                        System.out.println("You are not in a room");
                        continue;
                    }

                    byteBuffer = ByteBuffer.allocate(1024);
                    byteBuffer.putInt(PacketType.ROOM_MESSAGE.getId());
                    Utils.putString(byteBuffer, input);

                    packet = new DatagramPacket(
                            byteBuffer.array(),
                            byteBuffer.position(),
                            InetAddress.getByName("localhost"),
                            port
                    );
                    socket.send(packet);
                    System.out.println(currentRoom + " - You: " + input);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
