package com.jwetherell.bitcoin;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

public class Peer {

    public static final String SENDER = "S";
    public static final String RECEIVER = "R";

    // Which port should we listen to
    private static int port = 5000;
    // Which address
    private static final String group = "225.4.5.6";

    public static MulticastSocket createReceiver() throws IOException {
        // Create the socket and bind it to port 'port'.
        MulticastSocket s = new MulticastSocket(port);
        // join the multicast group
        s.joinGroup(InetAddress.getByName(group));
        // Now the socket is set up and we are ready to receive packets
        return s;
    }

    public static void destoryReceiver(MulticastSocket s) throws UnknownHostException, IOException {
        // Leave the multicast group and close the socket
        s.leaveGroup(InetAddress.getByName(group));
        s.close();
    }

    public static void recvData(MulticastSocket s, byte[] buffer) throws IOException {
        // Create a DatagramPacket and do a receive
        DatagramPacket pack = new DatagramPacket(buffer, buffer.length);
        s.receive(pack);
        // Finally, let us do something useful with the data we just received, like print it on stdout :-)
        System.out.println(s.getLocalPort()+" received data from: " + pack.getAddress().toString() + ":" + pack.getPort() + " with length: " + pack.getLength());
        System.out.write(pack.getData(), 0, pack.getLength());
        System.out.println();
        // We have finished receiving data
    }

    public static MulticastSocket createSender() throws IOException {
        // Create the socket but we don't bind it as we are only going to send data
        MulticastSocket s = new MulticastSocket();
        // Note that we don't have to join the multicast group if we are only
        // sending data and not receiving
        return s;
    }

    public static void destroySender(MulticastSocket s) throws IOException {
        // When we have finished sending data close the socket
        s.close();
    }

    public static void sendData(MulticastSocket s, int ourTTL, byte[] buffer) throws IOException {
        // Create a DatagramPacket 
        DatagramPacket pack = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(group), port);
        // Get the current TTL, set our TTL, do a send, reset the TTL  
        int ttl = s.getTimeToLive(); 
        s.setTimeToLive(ourTTL); 
        s.send(pack); 
        s.setTimeToLive(ttl);
    }
}
