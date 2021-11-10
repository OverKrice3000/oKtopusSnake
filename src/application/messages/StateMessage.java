package application.messages;

import application.GameState;

public class StateMessage extends Message{
    final GameState state;

    public StateMessage(int seq, int senderId, int receiverId, GameState state) {
        super(MessageType.SENDSTATE, seq, senderId, receiverId);
        this.state = state;
    }
}
