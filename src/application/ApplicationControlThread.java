package application;

import application.enums.Direction;
import application.enums.NodeRole;
import application.enums.PlayerType;
import application.gamedata.GameConfig;
import application.gamedata.PlayerInfo;
import application.graphics.Application;
import application.messages.*;

import java.io.*;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;

/**
 * This class represents thread, which controls game process
 */
public class ApplicationControlThread extends Thread {
    private final int myId;
    private int mySeq = 0;
    /**
     * Latest state of the game
     */
    private GameState currentState;
    /**
     * Graphic application
     */
    private final Application app;

    private NodeRole role;
    private final MulticastSocket socket;

    private long lastAnnounceUpdate;
    private long lastStateUpdate;

    private Inet4Address masterAddr;
    private int masterPort;
    private int masterId;

    /**
     * This constructor is invoked when player decided to start a new game.
     * <p>
     * It initializes resources for network interaction
     * and determines starting state of the game.
     * @param gameConfig config, chosen by a player.
     * @param app graphic application.
     */
    public ApplicationControlThread(GameConfig gameConfig, Application app, MulticastSocket socket) {
        this.app = app;
        this.socket = socket;
        myId = 0;
        role = NodeRole.MASTER;
        PlayerInfo master = new PlayerInfo("Master", myId, "", socket.getLocalPort(), NodeRole.MASTER, PlayerType.HUMAN); //TODO name from application
        currentState = new GameState(gameConfig, master);

        lastAnnounceUpdate = System.currentTimeMillis();
        lastStateUpdate = lastAnnounceUpdate;
    }

    public ApplicationControlThread(Application app, MulticastSocket socket, Inet4Address masterAddr, int masterPort) throws IOException, ClassNotFoundException {
        this.app = app;
        this.socket = socket;
        role = NodeRole.NORMAL;

        this.masterAddr = masterAddr;
        this.masterPort = masterPort;

        JoinMessage message = new JoinMessage(
                mySeq++, 0, 0,
                PlayerType.HUMAN,
                false, "Player");
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ObjectOutputStream objOut = new ObjectOutputStream(byteOut);
        objOut.writeObject(message);
        DatagramPacket sendPacket = new DatagramPacket(
                byteOut.toByteArray(), byteOut.toByteArray().length,
                masterAddr, masterPort);
        socket.send(sendPacket);

        socket.setSoTimeout(1000);

        byte[] buf = new byte[4096];
        DatagramPacket recvPacket = new DatagramPacket(buf, 4096);
        socket.receive(recvPacket);
        System.out.println("RECEIVED!");
        ByteArrayInputStream byteIn = new ByteArrayInputStream(buf);
        ObjectInputStream objIn = new ObjectInputStream(byteIn);
        Object recvObj = objIn.readObject(); // TODO class not found exception
        if(recvObj.getClass() != AckMessage.class)
            throw new ClassNotFoundException();

        AckMessage answer = (AckMessage)recvObj;
        myId = answer.receiverId;
        masterId = answer.senderId;

        lastAnnounceUpdate = System.currentTimeMillis();
        lastStateUpdate = lastAnnounceUpdate;
    }

    private int processTimeoutTasks() throws IOException, InterruptedException {
        int minimalTimeout = 1000;
        int currentTimeout;
        if(role == NodeRole.MASTER) {
            currentTimeout = (int) (1000 - (System.currentTimeMillis() - lastAnnounceUpdate));
            if (currentTimeout <= 0) {
                boolean canJoin = (currentState.findSuitableCoord() != null);
                AnnouncementMessage message = new AnnouncementMessage(
                        mySeq++, 0, 0,
                        currentState.players.values().toArray(new PlayerInfo[0]),
                        currentState.config, canJoin);
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                ObjectOutputStream objOut = new ObjectOutputStream(byteOut);
                objOut.writeObject(message);
                DatagramPacket packet = new DatagramPacket(
                        byteOut.toByteArray(), byteOut.toByteArray().length,
                        Inet4Address.getByName("239.192.0.4"), 9192);
                socket.send(packet);
                lastAnnounceUpdate = System.currentTimeMillis();
            }
        }

        if(role == NodeRole.MASTER) {
            currentTimeout = (int) (currentState.config.iterationDelayMs - (System.currentTimeMillis() - lastStateUpdate));
            if (currentTimeout <= 0) {
                currentState.changeState();
                for (Integer playerId : currentState.players.keySet()) {
                    StateMessage message = new StateMessage(mySeq++, myId, playerId, currentState);
                    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                    ObjectOutputStream objOut = new ObjectOutputStream(byteOut);
                    objOut.writeObject(message);
                    DatagramPacket packet = new DatagramPacket(
                            byteOut.toByteArray(), byteOut.toByteArray().length,
                            Inet4Address.getByName(currentState.players.get(playerId).ipAddress),
                            currentState.players.get(playerId).port);
                    socket.send(packet);
                }
                app.paintState(currentState);

                lastStateUpdate = System.currentTimeMillis();
                currentTimeout = currentState.config.iterationDelayMs;
            }
            if (currentTimeout < minimalTimeout)
                minimalTimeout = currentTimeout;
        }

        return minimalTimeout;
    }

    private void processReceivedPacket(DatagramPacket recvPacket) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteIn = new ByteArrayInputStream(recvPacket.getData());
        ObjectInputStream objIn = new ObjectInputStream(byteIn);
        Object recvObj = objIn.readObject();

        if(role == NodeRole.MASTER && recvObj.getClass() == SteerMessage.class){
            SteerMessage message = (SteerMessage)recvObj;
            currentState.changeSnakeDirection(message.senderId, message.direction);
        }
        else if(role == NodeRole.MASTER && recvObj.getClass() == JoinMessage.class){
            JoinMessage recvMessage = (JoinMessage)recvObj;
            boolean canJoin = currentState.findSuitableCoord() != null;
            if(canJoin) {
                int unusedId = findUnusedId();
                AckMessage message = new AckMessage(recvMessage.seq, myId, unusedId);
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                ObjectOutputStream objOut = new ObjectOutputStream(byteOut);
                objOut.writeObject(message);
                DatagramPacket packet = new DatagramPacket(
                        byteOut.toByteArray(), byteOut.toByteArray().length,
                        recvPacket.getAddress(),
                        recvPacket.getPort());
                socket.send(packet);

                PlayerInfo newPlayer = new PlayerInfo(
                        recvMessage.name, unusedId,
                        recvPacket.getAddress().getHostAddress(), recvPacket.getPort(),
                        NodeRole.NORMAL, recvMessage.playerType
                );
                currentState.players.put(newPlayer.id, newPlayer);
                currentState.addNewSnake(newPlayer.id);
            }
            else{
                ErrorMessage message = new ErrorMessage(recvMessage.seq, myId, 0, "Game if full!");
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                ObjectOutputStream objOut = new ObjectOutputStream(byteOut);
                objOut.writeObject(message);
                DatagramPacket packet = new DatagramPacket(
                        byteOut.toByteArray(), byteOut.toByteArray().length,
                        recvPacket.getAddress(),
                        recvPacket.getPort());
                socket.send(packet);
            }
        }
        else if(role == NodeRole.NORMAL && recvObj.getClass() == StateMessage.class){
            StateMessage recvMessage = (StateMessage) recvObj;
            if(currentState.getStateId() < recvMessage.senderId) {
                currentState = recvMessage.state;
                app.paintState(currentState);
            }
        }
    }

    private int findUnusedId(){
        int iterations = currentState.players.size() + 1;
        for(int i = 0; i < iterations; i++){
            if(!currentState.players.containsKey(i))
                return i;
        }
        throw new IllegalStateException("SYSTEM ERROR: Could not find unused id");
    }

    public void run(){
        byte[] buf = new byte[4096];
        DatagramPacket recvPacket = new DatagramPacket(buf, 4096);
        int currentSockTimeout = 0;
        long lastUpdate = System.currentTimeMillis();
        while(true){
            try {
                if(interrupted()) {
                    break;
                }
                currentSockTimeout -= System.currentTimeMillis() - lastUpdate;
                if(currentSockTimeout <= 0)
                    currentSockTimeout = processTimeoutTasks();
                socket.setSoTimeout(currentSockTimeout);
                socket.receive(recvPacket);
                processReceivedPacket(recvPacket);
            } catch(SocketTimeoutException | ClassNotFoundException e){
                continue;
            } catch (InterruptedException e) {
                break;
            } catch (IOException e){
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    public void changeSnakeDirection(Direction direction){
        if(role == NodeRole.MASTER) {
            currentState.changeSnakeDirection(myId, direction);
        }
        else{
            try {
                SteerMessage message = new SteerMessage(mySeq++, myId, masterId, direction);
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                ObjectOutputStream objOut = new ObjectOutputStream(byteOut);
                objOut.writeObject(message);
                DatagramPacket packet = new DatagramPacket(byteOut.toByteArray(), byteOut.toByteArray().length,
                                                                            masterAddr, masterPort);
                socket.send(packet);
            } catch(IOException e){
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }
    /**
     * This constructor is invoked when player decided to join existing game.
     * <p>
     * It attempts to establish connection with "MASTER" node of existing game.
     * @param address address of "MASTER" node of existing game.
     * @param port port of "MASTER" node of existing game.
     * @param app graphic application.
     */
    /*public ApplicationControlThread(String address, String port, Application app){
        this.app = app;
    }*/
}
