package application;

import application.enums.NodeRole;
import application.enums.PlayerType;
import application.gamedata.GameConfig;
import application.gamedata.PlayerInfo;
import application.messages.AnnouncementMessage;
import application.messages.ErrorMessage;
import application.messages.Message;

import javax.xml.crypto.Data;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class JoinableTestThread extends Thread{
    @Override
    public void run() {
        try {
            MulticastSocket socket = new MulticastSocket(9192);
            socket.setTimeToLive(255);

            socket.joinGroup(InetAddress.getByName("239.192.0.4"));
            socket.setBroadcast(true);
            socket.setLoopbackMode(true);

            PlayerInfo player = new PlayerInfo("Master", 1, "", (short)25565, NodeRole.MASTER, PlayerType.HUMAN);
            PlayerInfo[] players = new PlayerInfo[1];
            players[0] = player;
            AnnouncementMessage message = new AnnouncementMessage(1, 1, 1, players, new GameConfig(), true);

            ByteArrayOutputStream byteOut = new ByteArrayOutputStream(4096);
            ObjectOutputStream objOut = new ObjectOutputStream(byteOut);
            objOut.writeObject(message);
            byte[] buf = byteOut.toByteArray();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, Inet4Address.getByName("239.192.0.4"), 9192);
            for(int i = 0; i < 10; i++){
                socket.send(packet);
                sleep(1000);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
