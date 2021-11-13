package application.messages;

public class ErrorMessage extends Message{
    public final String reason;

    public ErrorMessage(int seq, int senderId, int receiverId, String reason){
        super(MessageType.ERROR, seq, senderId, receiverId);
        this.reason = reason;
    }
}
