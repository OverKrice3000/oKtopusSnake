package application.gamedata;

import application.enums.NodeRole;
import application.enums.PlayerType;


public class PlayerInfo {
    public final String name;
    public final int id;
    public final String ipAddress;
    public final short port;
    private NodeRole role;
    public final PlayerType type;
    private int score = 0;

    public PlayerInfo(String name, int id, String ipAddress, short port, NodeRole role, PlayerType type) {
        this.name = name;
        this.id = id;
        this.ipAddress = ipAddress;
        this.port = port;
        this.role = role;
        this.type = type;
    }

    public int getScore(){
        return score;
    }

    public void incrementScore(){
        score++;
    }

    public void nullifyScore(){
        score = 0;
    }

}
