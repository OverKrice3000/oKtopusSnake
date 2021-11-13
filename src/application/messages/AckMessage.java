package application.messages;

public class AckMessage extends Message{

    public AckMessage(int seq, int senderId, int receiverId) {
        super(MessageType.ACK, seq, senderId, receiverId);
    }
}
