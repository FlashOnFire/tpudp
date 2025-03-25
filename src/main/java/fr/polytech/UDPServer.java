package fr.polytech;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UDPServer {
    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket(1234)) {
            System.out.println("Server is running on port 1234");
            byte[] buffer = new byte[1024];

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Received: " + message);

            DatagramPacket reply = new DatagramPacket(message.getBytes(), message.length(), packet.getAddress(), packet.getPort());
            socket.send(reply);
            System.out.println("Reply sent");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
