package fr.polytech;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UDP Chat Client implementation that handles communication with a chat server.
 * This client supports features such as:
 * <ul>
 *   <li>User authentication with username</li>
 *   <li>Heartbeat mechanism to maintain connection</li>
 *   <li>Broadcast and private messaging</li>
 *   <li>Room management (create, join, delete)</li>
 *   <li>Command-based interface for interaction</li>
 * </ul>
 */
public class ChatUDPClient {
    public static void main(String[] args) {
        // Get username
        System.out.println("Enter your name (max 32chars): ");
        Scanner scanner = new Scanner(System.in);
        String input;
        do {
            input = scanner.nextLine();
        } while (input.isBlank() || input.length() > 32);

        // Create socket
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

            // Receive new communication port or name already taken packet
            byte[] buffer = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            socket.receive(reply);
            byteBuffer = ByteBuffer.wrap(reply.getData(), 0, reply.getLength());
            int packetType = byteBuffer.getInt();

            if (packetType == PacketType.NAME_ALREADY_TAKEN.getId()) {
                System.out.println("Name already taken");
                // if name already taken, exit
                return;
            } else if (packetType != PacketType.PORT.getId()) {
                System.out.println("Invalid packet type");
                System.out.println("Received: " + packetType);
                // if packet type is neither PORT nor NAME_ALREADY_TAKEN, exit
                return;
            }

            // Get new port
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


            // Create lists for users and rooms
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

                                System.out.println("<" + username + "> " + roomMessage);
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
                        System.out.println("/currentroom       - Show your current room");
                        System.out.println("/help              - Show this help message");
                        System.out.println("/quit              - Exit the chat application");
                        System.out.println("===================");
                    } else if (input.equals("/users")) {
                        System.out.println("===== ONLINE USERS =====");
                        for (String user : userList) {
                            System.out.println("- " + user);
                        }
                        System.out.println("=======================");
                    } else if (input.equals("/currentroom")) {
                        System.out.println("Current room: " + currentRoom.get());
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
                        if (!input.startsWith("/msg ") || parts.length < 3) {
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
                        if (!input.startsWith("/bc ")) {
                            System.out.println("Usage: /bc <message>");
                            continue;
                        }

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
                        if (!input.startsWith("/room ")) {
                            System.out.println("Usage: /room <room_name>");
                            continue;
                        }

                        String roomName = input.substring(6);

                        if (roomName.isBlank()) {
                            System.out.println("Error: Room name cannot be empty");
                            continue;
                        }

                        if (currentRoom.get().equals(roomName)) {
                            System.out.println("You are already in this room");
                            continue;
                        }

                        if (!roomList.contains(roomName)) {
                            System.out.println("Room does not exist");
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
                    // if not a command, send message as is to current room
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
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
