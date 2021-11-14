package application.messages;

public class PingMessage extends Message{

    public PingMessage(int seq, int senderId, int receiverId) {
        super(MessageType.PING, seq, senderId, receiverId);
    }
}
