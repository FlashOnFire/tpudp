package fr.polytech;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Session {

    DatagramSocket socket;
    Thread thread;

    public Session() {
        try {
            socket = new DatagramSocket();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        thread = new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("Received: " + message);
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            } finally {
                socket.close();
            }
        });

        thread.start();
    }

    public int getPort() {
        return socket.getLocalPort();
    }
}
