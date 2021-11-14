package application;

import application.enums.Direction;
import application.enums.NodeRole;
import application.enums.PlayerType;
import application.gamedata.GameConfig;
import application.gamedata.PlayerInfo;
import application.graphics.Application;
import application.messages.*;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

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
    private boolean isThereDeputy = false;
    private final MulticastSocket socket;

    private long lastAnnounce;
    private long lastStateUpdate;
    private final TreeMap<Integer, Long> lastPings = new TreeMap<>();
    private final TreeMap<Integer, Long> lastRecvs = new TreeMap<>();
    private final TreeMap<Integer, ArrayList<ResendablePacket>> resendableQueues = new TreeMap<>();

    private Inet4Address masterAddr;
    private int masterPort;
    private int masterId;

    private class ResendablePacket{
        private final DatagramPacket packet;
        private long lastSent;
        private final int seq;

        private ResendablePacket(DatagramPacket packet, int seq) {
            this.packet = packet;
            this.seq = seq;
            lastSent = System.currentTimeMillis();
        }

        private void updateLastSend(){
            lastSent = System.currentTimeMillis();
        }
    }

    /**
     * This constructor is invoked when player decided to start a new game.
     * <p>
     * It initializes resources for network interaction
     * and determines starting state of the game.
     * @param gameConfig config, chosen by a player.
     * @param app graphic application.
     */
    public ApplicationControlThread(GameConfig gameConfig, Application app, MulticastSocket socket, String name) {
        this.app = app;
        this.socket = socket;
        myId = 0;
        role = NodeRole.MASTER;
        PlayerInfo master = new PlayerInfo(name, myId, "", socket.getLocalPort(), NodeRole.MASTER, PlayerType.HUMAN); //TODO name from application
        currentState = new GameState(gameConfig, master);

        lastAnnounce = System.currentTimeMillis();
        lastStateUpdate = lastAnnounce;
    }

    public ApplicationControlThread(Application app, MulticastSocket socket, GameConfig config, Inet4Address masterAddr, int masterPort, int masterId, String name) throws IOException {
        this.app = app;
        this.socket = socket;
        role = NodeRole.NORMAL;
        currentState = new GameState(config, new PlayerInfo("Master", masterId, masterAddr.getHostAddress(), masterPort, NodeRole.MASTER, PlayerType.HUMAN));

        this.masterAddr = masterAddr;
        this.masterPort = masterPort;

        JoinMessage message = new JoinMessage(
                mySeq++, 0, masterId,
                PlayerType.HUMAN,
                false, name);
        sendPacket(message, masterAddr, masterPort, false);

        byte[] buf = new byte[4096];
        DatagramPacket recvPacket = new DatagramPacket(buf, 4096);
        int recvAckTimeout = 1000;
        long lastRead = System.currentTimeMillis();

        Object recvObj = null;
        try {
            while (true) {
                recvAckTimeout -= System.currentTimeMillis() - lastRead;
                lastRead = System.currentTimeMillis();
                socket.receive(recvPacket);
                ByteArrayInputStream byteIn = new ByteArrayInputStream(buf);
                ObjectInputStream objIn = new ObjectInputStream(byteIn);
                socket.setSoTimeout(recvAckTimeout);
                try {
                    recvObj = objIn.readObject();
                    System.out.println(recvObj.getClass());
                    if (recvObj.getClass() == AckMessage.class)
                        break;
                    else if(recvObj.getClass() == ErrorMessage.class)
                        app.showErrorMessage(((ErrorMessage) recvObj).reason);
                } catch (ClassNotFoundException e) {
                    continue;
                }
            }
        } catch(SocketTimeoutException e){
            throw new IOException("Could not join to the game");
        }

        AckMessage answer = (AckMessage)recvObj;
        myId = answer.receiverId;
        this.masterId = answer.senderId;


        lastAnnounce = System.currentTimeMillis();
        lastStateUpdate = lastAnnounce;
        lastPings.put(this.masterId, lastAnnounce);
        lastRecvs.put(this.masterId, lastAnnounce);

        System.out.println("MASTER ID: " + this.masterId);
        System.out.println("FAKE MASTER ID: " + masterId);
        int queueInitCapacity = (int)(2. / config.pingDelayMs + 2. / config.iterationDelayMs);
        resendableQueues.put(this.masterId, new ArrayList<>(queueInitCapacity));
    }

    private int processTimeoutTasks() throws IOException, InterruptedException {
        int minimalTimeout = 1000;
        int currentTimeout;
        if(role == NodeRole.MASTER) {
            currentTimeout = (int) (1000 - (System.currentTimeMillis() - lastAnnounce));
            if (currentTimeout <= 0) {
                boolean canJoin = (currentState.findSuitableCoord() != null);
                AnnouncementMessage message = new AnnouncementMessage(mySeq++, 0, 0,
                        currentState.players.values().toArray(new PlayerInfo[0]),
                        currentState.config, canJoin);
                sendPacket(message, InetAddress.getByName("239.192.0.4"), 9192, false);
                lastAnnounce = System.currentTimeMillis();
            }
        }

        if(role == NodeRole.MASTER) {
            currentTimeout = (int) (currentState.config.iterationDelayMs - (System.currentTimeMillis() - lastStateUpdate));
            if (currentTimeout <= 0) {
                currentState.changeState();
                for (Integer playerId : currentState.players.keySet()) {
                    if(playerId == myId)
                        continue;
                    StateMessage message = new StateMessage(mySeq++, myId, playerId, currentState);
                    sendPacket(message, Inet4Address.getByName(currentState.players.get(playerId).ipAddress),
                            currentState.players.get(playerId).port, true);
                    lastPings.put(playerId, System.currentTimeMillis());
                }
                app.paintState(currentState);

                lastStateUpdate = System.currentTimeMillis();
                currentTimeout = currentState.config.iterationDelayMs;
            }
            if (currentTimeout < minimalTimeout)
                minimalTimeout = currentTimeout;
        }

        for(Map.Entry<Integer, Long> entry: lastPings.entrySet()){
            System.out.println("JUST ID: " + entry.getKey());
            currentTimeout = (int) (currentState.config.pingDelayMs - (System.currentTimeMillis() - entry.getValue()));
            if(currentTimeout <= 0){
                PingMessage message = new PingMessage(mySeq++, myId, entry.getKey());
                sendPacket(message, InetAddress.getByName(currentState.players.get(entry.getKey()).ipAddress),
                        currentState.players.get(entry.getKey()).port, true); //TODO ?
                entry.setValue(System.currentTimeMillis());
                currentTimeout = currentState.config.pingDelayMs;
            }
            if (currentTimeout < minimalTimeout)
                minimalTimeout = currentTimeout;
        }

        for(Map.Entry<Integer, ArrayList<ResendablePacket>> entry: resendableQueues.entrySet()){
            ArrayList<ResendablePacket> queue = entry.getValue();
            for(ResendablePacket packet: queue){
                currentTimeout = (int) (currentState.config.pingDelayMs - (System.currentTimeMillis() - packet.lastSent));
                if(currentTimeout <= 0){
                    System.out.println("RESEND: " + packet.seq);
                    socket.send(packet.packet);
                    packet.updateLastSend();
                    currentTimeout = currentState.config.pingDelayMs;
                }
                if (currentTimeout < minimalTimeout)
                    minimalTimeout = currentTimeout;
            }
        }

        ArrayList<Integer> toRemove = new ArrayList<>(currentState.players.size());

        for(Map.Entry<Integer, Long> entry: lastRecvs.entrySet()){
            currentTimeout = (int) (currentState.config.nodeTimeoutMs - (System.currentTimeMillis() - entry.getValue()));
            if(currentTimeout <= 0){
                int id = entry.getKey();
                PlayerInfo player = currentState.players.get(id);
                if(role == NodeRole.NORMAL){
                    lastPings.clear();
                    lastRecvs.clear();
                    resendableQueues.clear();
                    PlayerInfo deputy = null;
                    for(PlayerInfo gamer: currentState.players.values()){
                        if(gamer.role == NodeRole.DEPUTY) {
                            deputy = gamer;
                            break;
                        }
                    }

                    if(deputy == null || deputy.id == masterId ){
                        System.out.println("HERE");
                        System.exit(-1);
                    }

                    masterAddr = (Inet4Address) Inet4Address.getByName(deputy.ipAddress);
                    masterPort = deputy.port;
                    masterId = deputy.id;
                    lastPings.put(masterId, System.currentTimeMillis());
                    lastRecvs.put(masterId, System.currentTimeMillis());
                    int queueInitCapacity = (int)(2. / currentState.config.pingDelayMs + 2. / currentState.config.iterationDelayMs);
                    resendableQueues.put(masterId, new ArrayList<>(queueInitCapacity));
                }
                else if(role == NodeRole.DEPUTY){
                    deputyReplaceMaster();
                }
                else if(role == NodeRole.MASTER){
                    System.out.println("REMOVE: " + id);
                    currentState.players.remove(id);
                    currentState.setZombie(id);
                    lastPings.remove(id);
                    toRemove.add(id);
                    resendableQueues.remove(id);
                    if(player.role == NodeRole.DEPUTY){
                        if(currentState.players.size() == 1){
                            isThereDeputy = false;
                        }
                        else{
                            PlayerInfo newDeputy;
                            if(currentState.players.lastEntry().getValue().role == NodeRole.MASTER)
                                newDeputy = currentState.players.firstEntry().getValue();
                            else
                                newDeputy = currentState.players.lastEntry().getValue();
                            newDeputy.role = NodeRole.DEPUTY;
                            ChangeRoleMessage message = new ChangeRoleMessage(mySeq++, myId, newDeputy.id, null, NodeRole.DEPUTY);
                            sendPacket(message, InetAddress.getByName(newDeputy.ipAddress), newDeputy.port, true);
                        }
                    }
                }
            }
            else if (currentTimeout < minimalTimeout)
                minimalTimeout = currentTimeout;
        }

        for(Integer id: toRemove){
            lastRecvs.remove(id);
        }

        return minimalTimeout;
    }

    private void processReceivedPacket(DatagramPacket recvPacket) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteIn = new ByteArrayInputStream(recvPacket.getData());
        ObjectInputStream objIn = new ObjectInputStream(byteIn);
        Object recvObj = objIn.readObject();
        System.out.println(recvObj.getClass());

        if(!Message.class.isAssignableFrom(recvObj.getClass()))
            return;
        Message recvMessage = (Message) recvObj;
        System.out.println("SENDER: " + recvMessage.senderId);
        if((role == NodeRole.MASTER || recvMessage.senderId == masterId) && recvObj.getClass() != JoinMessage.class){
            if(!currentState.players.containsKey(recvMessage.senderId))
                return;
            lastRecvs.put(recvMessage.senderId, System.currentTimeMillis());
        }

        if(role == NodeRole.MASTER && recvObj.getClass() == SteerMessage.class){
            SteerMessage message = (SteerMessage)recvObj;
            System.out.println("RECEIVED STEER: " + message.seq);
            currentState.changeSnakeDirection(message.senderId, message.direction);
            AckMessage ack = new AckMessage(message.seq, myId, message.senderId);
            sendPacket(ack, recvPacket.getAddress(), recvPacket.getPort(), false);
        }
        else if(role == NodeRole.MASTER && recvObj.getClass() == JoinMessage.class){
            JoinMessage message = (JoinMessage)recvObj;
            boolean canJoin = currentState.findSuitableCoord() != null;
            if(canJoin) {
                int unusedId = findUnusedId();
                System.out.println("UNUSED: " + unusedId);
                lastPings.put(unusedId, System.currentTimeMillis());
                lastRecvs.put(unusedId, System.currentTimeMillis());
                int queueInitCapacity = (int)(2. / currentState.config.pingDelayMs + 2. / currentState.config.iterationDelayMs);
                resendableQueues.put(unusedId, new ArrayList<>(queueInitCapacity));

                AckMessage ack = new AckMessage(message.seq, myId, unusedId);
                sendPacket(ack, recvPacket.getAddress(), recvPacket.getPort(), false); //TODO ?

                NodeRole newPlayerRole = NodeRole.NORMAL;
                if(!isThereDeputy){
                    newPlayerRole = NodeRole.DEPUTY;
                    isThereDeputy = true;
                    ChangeRoleMessage role = new ChangeRoleMessage(mySeq++, myId, unusedId, null, NodeRole.DEPUTY);
                    sendPacket(role, recvPacket.getAddress(), recvPacket.getPort(), true);
                }

                PlayerInfo newPlayer = new PlayerInfo(
                        message.name, unusedId,
                        recvPacket.getAddress().getHostAddress(), recvPacket.getPort(),
                        newPlayerRole, message.playerType
                );
                currentState.players.put(unusedId, newPlayer);
                currentState.addNewSnake(unusedId);
            }
            else{
                ErrorMessage error = new ErrorMessage(mySeq++, myId, 0, "Game if full!");
                sendPacket(error, recvPacket.getAddress(), recvPacket.getPort(), true);
            }
        }
        else if(role != NodeRole.MASTER && recvObj.getClass() == StateMessage.class){
            StateMessage message = (StateMessage) recvObj;
            AckMessage ack = new AckMessage(message.seq, myId, message.senderId);
            sendPacket(ack, recvPacket.getAddress(), recvPacket.getPort(), false); //TODO ?
            if(currentState == null || currentState.getStateId() < message.state.getStateId()) {
                currentState = message.state;
                app.paintState(currentState);
            }
        }
        else if(recvObj.getClass() == PingMessage.class){
            PingMessage message = (PingMessage) recvObj;
            AckMessage ack = new AckMessage(message.seq, myId, message.senderId);
            sendPacket(ack, recvPacket.getAddress(), recvPacket.getPort(), false);
        }
        else if(recvObj.getClass() == ChangeRoleMessage.class){
            ChangeRoleMessage message = (ChangeRoleMessage) recvObj;
            if(role == NodeRole.DEPUTY && message.receiverRole == NodeRole.MASTER){ //TODO ?
                deputyReplaceMaster();
            }
            else if(role == NodeRole.NORMAL && message.receiverRole == NodeRole.DEPUTY){
                role = NodeRole.DEPUTY;
            }
            else if(message.senderRole == NodeRole.MASTER){
                if(role == NodeRole.MASTER) {
                    System.out.println("THERE");
                    System.exit(-1); //TODO change
                }
                if(masterId == message.senderId)
                    return;
                role = NodeRole.NORMAL;
                isThereDeputy = false;
                lastPings.clear();
                lastRecvs.clear();
                resendableQueues.clear();
                masterAddr = (Inet4Address) recvPacket.getAddress();
                masterPort = recvPacket.getPort();
                masterId = message.senderId;
                lastPings.put(masterId, System.currentTimeMillis());
                lastRecvs.put(masterId, System.currentTimeMillis());
                int queueInitCapacity = (int)(2. / currentState.config.pingDelayMs + 2. / currentState.config.iterationDelayMs);
                resendableQueues.put(masterId, new ArrayList<>(queueInitCapacity));
                //TODO maybe master not exit
            }
            AckMessage ack = new AckMessage(message.seq, myId, message.senderId);
            sendPacket(ack, recvPacket.getAddress(), recvPacket.getPort(), false);
        }
        else if(recvObj.getClass() == ErrorMessage.class){
            //TODO ?
        }
        else if(recvObj.getClass() == AckMessage.class){
            AckMessage message = (AckMessage) recvObj;
            System.out.println("RECEIVED ACK: " + message.seq);
            resendableQueues.get(message.senderId).removeIf(new Predicate<ResendablePacket>() {
                @Override
                public boolean test(ResendablePacket resendablePacket) {
                    return resendablePacket.seq == message.seq;
                }
            });
        }
    }

    private void sendPacket(Message message, InetAddress address, int port, boolean resend) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ObjectOutputStream objOut = new ObjectOutputStream(byteOut);
        objOut.writeObject(message);
        DatagramPacket packet = new DatagramPacket(byteOut.toByteArray(), byteOut.toByteArray().length, address, port);
        if(resend)
            resendableQueues.get(message.receiverId).add(new ResendablePacket(packet, message.seq));
        socket.send(packet);
    }

    private void deputyReplaceMaster() throws IOException {
        role = NodeRole.MASTER;
        currentState.players.get(myId).role = NodeRole.MASTER;

        currentState.players.remove(masterId); //TODO maybe not delete master
        currentState.setZombie(masterId);

        lastPings.clear();
        lastRecvs.clear();
        resendableQueues.clear();
        for(Map.Entry<Integer, PlayerInfo> entry: currentState.players.entrySet()){
            int id = entry.getKey();
            PlayerInfo player = entry.getValue();
            if(player.role == NodeRole.MASTER)
                continue;
            lastPings.put(id, System.currentTimeMillis());
            System.out.println("ID: " + id);
            lastRecvs.put(id, System.currentTimeMillis());
            int queueInitCapacity = (int)(2. / currentState.config.pingDelayMs + 2. / currentState.config.iterationDelayMs);
            resendableQueues.put(id, new ArrayList<>(queueInitCapacity));
            ChangeRoleMessage fromDeputy = new ChangeRoleMessage(mySeq++, myId, id, NodeRole.MASTER, null);
            sendPacket(fromDeputy, InetAddress.getByName(player.ipAddress), player.port, true);
        }
        System.out.println(currentState.players.size());
        if(currentState.players.size() == 1){
            isThereDeputy = false;
        }
        else{
            PlayerInfo newDeputy;
            if(currentState.players.lastEntry().getValue().role == NodeRole.MASTER)
                newDeputy = currentState.players.firstEntry().getValue();
            else
                newDeputy = currentState.players.lastEntry().getValue();
            newDeputy.role = NodeRole.DEPUTY;
            ChangeRoleMessage message = new ChangeRoleMessage(mySeq++, myId, newDeputy.id, null, NodeRole.DEPUTY);
            sendPacket(message, InetAddress.getByName(newDeputy.ipAddress), newDeputy.port, true);
        }
    }

    private int findUnusedId(){
        int iterations = currentState.snakes.size() + 1;
        for(int i = 0; i < iterations; i++){
            if(!currentState.snakes.containsKey(i))
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
                System.out.println("CHECK: " + currentState.players.containsKey(0));
                System.out.println("CHECK: " + currentState.players.size());
                if(interrupted()) {
                    //TODO change role
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
                sendPacket(message, masterAddr, masterPort, true); //TODO ?
            } catch(IOException e){
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

}
