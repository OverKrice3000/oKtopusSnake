package application.enums;

/**
 * This enum class represents direction of snake's next turn.
 * An object of this class is stored in Snake class.
 * It is guaranteed, that whatever Direction object is stored inside a Snake object,
 * snake can make a turn in this direction.
 */
public enum Direction {
    /**
     * Snake will move to upper tile next turn
     */
    UP(0, -1, 1),
    /**
     * Snake will move to bottom tile next turn
     */
    DOWN(0, 1, -1),
    /**
     * Snake will move to left tile next turn
     */
    LEFT(-1, 0, 2),
    /**
     * Snake will move to right tile next turn
     */
    RIGHT(1, 0, -2);

    public final int x, y, id;
    Direction(int x, int y, int id){
        this.x = x;
        this.y = y;
        this.id = id;
    }

}
