package fr.polytech;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ChatUDPClient {
    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket()) {
            System.out.println("Client is running ");

            String message = "Hello";
            DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(),
                                                       InetAddress.getByName("localhost"), 1234);

            socket.send(packet);
            byte[] buffer = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            socket.receive(reply);
            String replyMessage = new String(reply.getData(), 0, reply.getLength());
            int port = Integer.parseInt(replyMessage);
            System.out.println("Received new port: " + port);

            DatagramPacket newPacket = new DatagramPacket("Hello".getBytes(), 5, InetAddress.getByName("localhost"), port);
            socket.send(newPacket);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
