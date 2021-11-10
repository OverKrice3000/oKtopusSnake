package application.messages;

public class AckMessage extends Message{

    public AckMessage(MessageType messageType, int seq, int senderId, int receiverId) {
        super(MessageType.ACK, seq, senderId, receiverId);
    }
}
