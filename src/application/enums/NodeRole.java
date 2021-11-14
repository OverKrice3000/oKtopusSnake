package application.enums;

/**
 * This enum class represents a role of a node in current application protocol.
 * Nodes here are all hosts, which are in the same game with each other.
 */
public enum NodeRole {
    /**
     * Node with role "MASTER" controls the game.
     * <p>
     * It receives messages from other nodes, telling what direction players wants to turn their snakes.
     * <p>
     * It makes turns for all the players and sends a new {@link application.GameState GameState} to other nodes.
     * <p>
     * It chooses a "DEPUTY" node.
     */
    MASTER,
    /**
     * Node with role "NORMAL" sends to the "MASTER" node messages, telling how player wants their snake controlled.
     * <p>
     * It receives {@link application.GameState GameState} from "MASTER" node
     * and tells {@link application.graphics.Application Application} object to show it.
     */
    NORMAL,
    /**
     * In case if "MASTER" node can't work properly due to connection troubles,
     * node with role "DEPUTY" will attempt to replace it.
     * Other than this, "DEPUTY" node is equivalent to "NORMAL" node.
     */
    DEPUTY,
    /**
     * Node with role "VIEWER" can't participate in game, but it does receive messages
     * with {@link application.GameState GameState} from "MASTER" node.
     */
    VIEWER,
}
