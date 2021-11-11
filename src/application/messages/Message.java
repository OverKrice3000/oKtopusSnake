package application.messages;

public abstract class Message{
    public final MessageType messageType;
    public final int seq;
    public final int senderId;
    public final int receiverId;

    public Message(MessageType messageType, int seq, int senderId, int receiverId) {
        this.messageType = messageType;
        this.seq = seq;
        this.senderId = senderId;
        this.receiverId = receiverId;
    }
}
