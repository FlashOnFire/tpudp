package fr.polytech;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Session {
    private DatagramSocket socket;

    private InetAddress address;
    private int destPort;

    private boolean firstHeartbeat = false;

    public Session(Runnable sessionTimeoutHook, Consumer<String> broadcastHook, BiPredicate<String, String> privateMessageHook, Supplier<ByteBuffer> userListSupplier) {
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
                        if (firstHeartbeat) {
                            address = packet.getAddress();
                            destPort = packet.getPort();

                            send(userListSupplier.get());
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
                    buffer.array(), buffer.position(), address,
                    destPort
            );
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
