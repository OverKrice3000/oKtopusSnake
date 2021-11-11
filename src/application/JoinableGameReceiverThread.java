package application;

import application.graphics.Application;
import application.messages.AnnouncementMessage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.*;

public class JoinableGameReceiverThread extends Thread {

    private final MulticastSocket socket = new MulticastSocket();
    private final Application app;
    private long outdatedGamesLastChecked;

    public JoinableGameReceiverThread(Application app) throws IOException {
        this.app = app;
        System.out.println();
        /*Enumeration enumeration =  NetworkInterface.getNetworkInterfaces();
        while(enumeration.hasMoreElements()){
            System.out.println(enumeration.nextElement());
        }*/
        socket.joinGroup(new InetSocketAddress("239.192.0.4", 9192), NetworkInterface.getByName("eth6"));
        socket.setSoTimeout(3000);
    }
    @Override
    public void run() {
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
                    System.out.println(packet.getOffset());
                    ByteArrayInputStream byteIn = new ByteArrayInputStream(buf);
                    ObjectInputStream objIn = new ObjectInputStream(byteIn);
                    AnnouncementMessage message = (AnnouncementMessage) objIn.readObject();
                    app.processAnnouncementMessage(message, (Inet4Address)packet.getAddress());
                }
                catch(ClassNotFoundException | SocketTimeoutException ignored){

                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
