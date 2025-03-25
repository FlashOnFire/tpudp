package fr.polytech;

import java.net.BindException;
import java.net.DatagramSocket;

public class UDPPortScanner {
    public static void main(String[] args) {
        for (int i = 0; i < 65536; i++) {
            try (DatagramSocket _ = new DatagramSocket(i)) {
                System.out.println("Port " + i + " is available");
            } catch (BindException e) {
                System.out.println("Port " + i + " is already in use");
            }
            catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
