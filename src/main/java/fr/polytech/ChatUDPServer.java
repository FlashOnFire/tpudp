package fr.polytech;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.util.ArrayList;

public class ChatUDPServer {
    public static void main(String[] args) {

        ArrayList<Session> sessions = new ArrayList<>();

        try (DatagramSocket socket = new DatagramSocket(1234)) {
            System.out.println("Server is running on port 1234");

            while (true) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Received new connection: " + message);

                Session session = new Session();
                sessions.add(session);

                // send new port to client
                DatagramPacket reply = new DatagramPacket(String.valueOf(session.getPort()).getBytes(), String.valueOf(session.getPort()).length(), packet.getAddress(), packet.getPort());
                socket.send(reply);
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
