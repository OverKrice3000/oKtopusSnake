package application.messages;

import application.gamedata.GameConfig;
import application.gamedata.PlayerInfo;

public class AnnouncementMessage extends Message {
    public final PlayerInfo[] players;
    public final GameConfig config;
    public final boolean canJoin;

    public AnnouncementMessage(int seq, int senderId, int receiverId, PlayerInfo[] players, GameConfig config, boolean canJoin){
        super(MessageType.ANNOUNCEGAME, seq, senderId, receiverId);
        this.players = players;
        this.config = config;
        this.canJoin = canJoin;
    }
}
