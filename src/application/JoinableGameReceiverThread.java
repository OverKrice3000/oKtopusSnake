package application;

import application.graphics.Application;
import application.messages.AnnouncementMessage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;
import java.net.*;
import java.util.Enumeration;

public class JoinableGameReceiverThread extends Thread {

    private final MulticastSocket socket;
    private final Application app;
    private long outdatedGamesLastChecked;

    public JoinableGameReceiverThread(Application app, MulticastSocket socket) {
        this.app = app;
        this.socket = socket;
    }
    @Override
    public void run() {
        /*Enumeration enumeration =  NetworkInterface.getNetworkInterfaces();
        while(enumeration.hasMoreElements()){
            System.out.println(enumeration.nextElement());
        }
        */
        try {
            byte[] buf = new byte[4096];
            DatagramPacket packet = new DatagramPacket(buf, 4096);
            outdatedGamesLastChecked = System.currentTimeMillis();
            while(true){
                if(interrupted()) {
                    app.clearJoinableGames();
                    break;
                }
                if(System.currentTimeMillis() - outdatedGamesLastChecked > 3000){
                    outdatedGamesLastChecked = System.currentTimeMillis();
                    app.removeOutdatedGames();
                }
                try {
                    socket.receive(packet);
                    ByteArrayInputStream byteIn = new ByteArrayInputStream(buf);
                    ObjectInputStream objIn = new ObjectInputStream(byteIn);
                    Object received =  objIn.readObject();
                    System.out.println(received.getClass());
                    if(received.getClass() != AnnouncementMessage.class || packet.getAddress().getClass() != Inet4Address.class)
                        continue;
                    AnnouncementMessage message = (AnnouncementMessage) received;
                    app.processAnnouncementMessage(message, (Inet4Address)packet.getAddress(), packet.getPort());
                }
                catch(ClassNotFoundException | SocketTimeoutException | StreamCorruptedException ignored){}
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
