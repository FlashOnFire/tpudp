package fr.polytech;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

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
            byte[] nameBytes = input.getBytes(StandardCharsets.UTF_8);
            byteBuffer.putInt(nameBytes.length);
            byteBuffer.put(nameBytes);
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

                    while (true) {
                        socket.send(hbPacket);
                        Thread.sleep(5000);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            heartbeat.setDaemon(true);
            heartbeat.start();

            ArrayList<String> userList = new ArrayList<>();

            // Start receiving thread
            Thread receivingThread = new Thread(() -> {
                try {
                    while (true) {
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
                                int messageLength = receiveByteBuffer.getInt();
                                byte[] messageBytes = new byte[messageLength];
                                receiveByteBuffer.get(messageBytes);
                                String message = new String(messageBytes, StandardCharsets.UTF_8);
                                System.out.println("[Broadcast]: " + message);
                                break;
                            case PRIVATE:
                                int recipientLength = receiveByteBuffer.getInt();
                                byte[] recipientBytes = new byte[recipientLength];
                                receiveByteBuffer.get(recipientBytes);
                                String recipient = new String(recipientBytes);

                                int privateMessageLength = receiveByteBuffer.getInt();
                                byte[] privateMessageBytes = new byte[privateMessageLength];
                                receiveByteBuffer.get(privateMessageBytes);
                                String privateMessage = new String(privateMessageBytes);

                                System.out.println("[" + recipient + " -> You]: " + privateMessage);
                                break;
                            case USER_LIST:
                                String userListStr = new String(
                                        receiveByteBuffer.array(),
                                        4,
                                        receivePacket.getLength() - 4,
                                        StandardCharsets.UTF_8
                                );
                                userList.clear();
                                userList.addAll(List.of(userListStr.split(",")));

                                System.out.println("User list: " + userList);
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
            receivingThread.start();

            // Send messages
            label:
            while (true) {
                System.out.println("Enter a command or a recipient: ");
                do {
                    input = scanner.nextLine();
                    if (!input.equals("bc")
                            && !input.equals("quit")
                            && !input.equals("list")
                            && !input.equals("help")
                            && !userList.contains(input)) {
                        System.out.println("User does not exist");
                        input = "";
                    }
                } while (input.isBlank() || input.length() > 32);

                switch (input) {
                    case "list":
                        System.out.println("===== ONLINE USERS =====");
                        for (String user : userList) {
                            System.out.println("- " + user);
                        }
                        System.out.println("=======================");
                        break;
                    case "help":
                        System.out.println("===== COMMANDS =====");
                        System.out.println("bc   - Broadcast message to all users");
                        System.out.println("quit - Exit the chat application");
                        System.out.println("list - Display all online users");
                        System.out.println("help - Show this help message");
                        System.out.println("===================");
                        break;
                    case "quit":
                        break label;
                    default:
                        byteBuffer = ByteBuffer.allocate(1024);
                        if (input.equals("bc")) {
                            byteBuffer.putInt(PacketType.BROADCAST.getId());
                        } else {
                            if (!userList.contains(input)) {
                                System.out.println("User does not exist");
                                break;
                            }

                            byteBuffer.putInt(PacketType.PRIVATE.getId());
                            byte[] recipientBytes = input.getBytes(StandardCharsets.UTF_8);
                            byteBuffer.putInt(recipientBytes.length);
                            byteBuffer.put(recipientBytes);
                        }

                        System.out.println("Enter your message: ");
                        do {
                            input = scanner.nextLine();
                        } while (input.isBlank() || input.length() > 1024);

                        byte[] messageBytes = input.getBytes(StandardCharsets.UTF_8);
                        byteBuffer.putInt(messageBytes.length);
                        byteBuffer.put(messageBytes);

                        packet = new DatagramPacket(
                                byteBuffer.array(),
                                byteBuffer.position(),
                                InetAddress.getByName("localhost"),
                                port
                        );
                        socket.send(packet);

                        System.out.println("[You -> " + (input.equals("bc") ? "All" : input) + "]: " + input);
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
