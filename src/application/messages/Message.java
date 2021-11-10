package application.messages;

public abstract class Message{
    final MessageType messageType;
    final int seq;
    final int senderId;
    final int receiverId;

    public Message(MessageType messageType, int seq, int senderId, int receiverId) {
        this.messageType = messageType;
        this.seq = seq;
        this.senderId = senderId;
        this.receiverId = receiverId;
    }
}
