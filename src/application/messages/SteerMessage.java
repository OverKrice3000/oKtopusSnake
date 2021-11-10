package application.messages;

import application.enums.Direction;

public class SteerMessage extends Message {
    final Direction direction;

    public SteerMessage(int seq, int senderId, int receiverId, Direction direction){
        super(MessageType.STEER, seq, senderId, receiverId);
        this.direction = direction;
    }
}
