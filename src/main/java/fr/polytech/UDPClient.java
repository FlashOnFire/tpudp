package fr.polytech;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPClient {
    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket()) {
            System.out.println("Client is running on port 1234");

            System.out.println("Sending a message to the server");
            DatagramPacket packet = new DatagramPacket("Hello".getBytes(), 5, InetAddress.getByName("localhost"), 1234);
            socket.send(packet);

            byte[] buffer = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            socket.receive(reply);
            String message = new String(reply.getData(), 0, reply.getLength());
            System.out.println("Received: " + message);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
