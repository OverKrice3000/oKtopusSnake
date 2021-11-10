package application.messages;

import application.enums.NodeRole;

public class ChangeRoleMessage extends Message {
    final NodeRole senderRole;
    final NodeRole receiverRole;

    public ChangeRoleMessage(int seq, int senderId, int receiverId, NodeRole senderRole, NodeRole receiverRole){
        super(MessageType.CHANGEROLE, seq, senderId, receiverId);
        this.senderRole = senderRole;
        this.receiverRole = receiverRole;
    }
}
