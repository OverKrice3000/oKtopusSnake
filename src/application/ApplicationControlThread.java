package application;

import application.enums.Direction;
import application.enums.NodeRole;
import application.enums.PlayerType;
import application.gamedata.GameConfig;
import application.gamedata.PlayerInfo;
import application.graphics.Application;

import java.io.IOException;
import java.net.MulticastSocket;

/**
 * This class represents thread, which controls game process
 */
public class ApplicationControlThread extends Thread {
    private final int myId;
    /**
     * Latest state of the game
     */
    private final GameState currentState;
    /**
     * Graphic application
     */
    private final Application app;

    private NodeRole role;
    private final MulticastSocket socket;

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
        PlayerInfo master = new PlayerInfo("Master", myId, "", (short)25565, NodeRole.MASTER, PlayerType.HUMAN); //TODO port & name from application
        currentState = new GameState(gameConfig, master);
    }

    public void run(){
        while(true){
            long millis = System.currentTimeMillis();;
            try {
                app.paintState(currentState);
                System.out.println(System.currentTimeMillis() - millis);
                sleep(currentState.config.iterationDelayMs);
                millis = System.currentTimeMillis();
                currentState.changeState();
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    public void changeSnakeDirection(Direction direction){
        currentState.changeSnakeDirection(myId, direction);
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
