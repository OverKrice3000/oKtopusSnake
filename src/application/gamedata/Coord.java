package application.gamedata;

import application.enums.Direction;

import java.io.Serializable;

public class Coord  implements Serializable {
    public final int x;
    public final int y;
    public Coord(int x, int y) {
        this.x = x;
        this.y = y;
    }
    public Direction dirOfRelative(){
        if(x < 0)
            return Direction.LEFT;
        else if(x > 0)
            return Direction.RIGHT;
        if(y < 0)
            return Direction.UP;
        else if(y > 0)
            return Direction.DOWN;
        else
            throw new IllegalStateException("Calculation of direction of non-relative coordinate");
    }
    public boolean equals(Object obj){
        if(obj == null)
            return false;
        if(this.getClass() != obj.getClass())
            return false;
        Coord coord = (Coord)obj;
        return (this.x == coord.x) && (this.y == coord.y);
    }
}
