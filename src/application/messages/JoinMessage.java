package application.messages;

import application.enums.PlayerType;

public class JoinMessage extends Message {
    public final PlayerType playerType;
    public final boolean onlyView;
    public final String name;

    public JoinMessage(int seq, int senderId, int receiverId, PlayerType playerType, boolean onlyView, String name){
        super(MessageType.JOIN, seq, senderId, receiverId);
        this.playerType = playerType;
        this.onlyView = onlyView;
        this.name = name;
    }
}
