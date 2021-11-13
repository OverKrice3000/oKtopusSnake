package application;

import application.enums.Direction;
import application.gamedata.Coord;
import application.gamedata.GameConfig;
import application.gamedata.PlayerInfo;

import java.io.Serializable;
import java.util.*;
import java.util.function.Predicate;

public class GameState implements Serializable {

    enum SnakeState {
        ALIVE,
        ZOMBIE
    }

    public class Snake implements Serializable {

        final int playerId;
        public final Deque<Coord> body;
        private SnakeState state = SnakeState.ALIVE;
        private Direction currentDirection;

        private Snake(int playerId, Coord head, Direction currentDirection) {
            this.playerId = playerId;
            this.body = new ArrayDeque<>();
            this.body.add(head);
            Coord tailRel = new Coord(-currentDirection.x, -currentDirection.y);
            this.body.addLast(tailRel);
            this.currentDirection = currentDirection;
        }

        private void changeDirection(Direction direction){
            synchronized (this) {
                Coord head = this.body.pollFirst();
                if (this.body.peekFirst().dirOfRelative().id != direction.id) {
                    currentDirection = direction;
                }
                this.body.addFirst(head);
            }
        }

        private void moveHead(){
            synchronized (this) {
                Coord head = body.removeFirst();
                Coord keyPoint = body.peekFirst();
                Direction lastDirection = keyPoint.dirOfRelative();
                if (currentDirection.x == -lastDirection.x && currentDirection.y == -lastDirection.y) {
                    body.removeFirst();
                    body.addFirst(new Coord(keyPoint.x + lastDirection.x, keyPoint.y + lastDirection.y));
                } else {
                    body.addFirst(new Coord(-currentDirection.x, -currentDirection.y));
                }
                body.addFirst(new Coord(Math.floorMod(head.x + currentDirection.x, config.width), Math.floorMod(head.y + currentDirection.y, config.height)));
            }
        }

        private void growTail(){
            synchronized (this) {
                Coord tail = body.removeLast();
                Direction relTail = tail.dirOfRelative();
                body.addLast(new Coord(tail.x + relTail.x, tail.y + relTail.y));
            }
        }

        private void cutTail(){
            synchronized (this) {
                Coord tail = body.removeLast();
                Direction relTail = tail.dirOfRelative();
                if (tail.x == relTail.x && tail.y == relTail.y)
                    return;
                body.addLast(new Coord(tail.x - relTail.x, tail.y - relTail.y));
            }
        }

        private boolean stumblesInto(Snake snake){
            Coord head = body.peekFirst();
            if(head.equals(snake.body.peekFirst()) && !this.equals(snake))
                return true;
            synchronized (this) {
                Coord first = snake.body.removeFirst();
                int curX = first.x;
                int curY = first.y;
                for (Coord coord : snake.body) {
                    int countToPaint = Math.abs(coord.x) + Math.abs(coord.y);
                    Direction nextDir = coord.dirOfRelative();
                    int nextStepX = nextDir.x;
                    int nextStepY = nextDir.y;
                    while (countToPaint-- != 0) {
                        curX = Math.floorMod(curX + nextStepX, config.width);
                        curY = Math.floorMod(curY + nextStepY, config.height);
                        if (head.x == curX && head.y == curY) {
                            snake.body.addFirst(first);
                            return true;
                        }
                    }
                }
                snake.body.addFirst(first);
            }
            return false;
        }

    }

    private int stateId;
    public final TreeMap<Integer, Snake> snakes = new TreeMap<>();
    public final ArrayList<Coord> foods = new ArrayList<>();
    public final TreeMap<Integer, PlayerInfo> players = new TreeMap<>();
    public final GameConfig config;

    private int snakesAlive;

    /**
     * This constructor invoked when starting state of the game is created.
     */
    public GameState(GameConfig config, PlayerInfo master){
        stateId = 0;

        this.config = config;

        players.put(master.id, master);

        snakesAlive = 0;
        addNewSnake(master.id);

        addNeededFood();
    }

    /**
     * This method changes {@code this} object as if one turn has passed
     */
    public void changeState() throws InterruptedException {
        stateId++;
        for(Snake snake: snakes.values()){
            snake.moveHead();
        }
        foods.removeIf(new Predicate<Coord>() {
            @Override
            public boolean test(Coord food) {
                boolean toDelete = false;
                for(Snake snake: snakes.values()){
                    if(food.equals(snake.body.peekFirst())){
                        snake.growTail();
                        if(snake.state == SnakeState.ALIVE)
                            players.get(snake.playerId).incrementScore();
                        toDelete = true;
                    }
                }
                return toDelete;
            }
        });
        for(Snake snake: snakes.values()){
            snake.cutTail();
        }
        ArrayList<Integer> toDelete = new ArrayList<>(snakes.size());
        for(Snake snake1: snakes.values()){
            for(Snake snake2: snakes.values()){
                if(snake1.stumblesInto(snake2) && !toDelete.contains(snake1.playerId)){
                    toDelete.add(snake1.playerId);
                    if(snake2.state == SnakeState.ALIVE)
                        players.get(snake2.playerId).incrementScore();
                }
            }
        }
        for(Integer id: toDelete){
            Snake snake = snakes.get(id);
            turnIntoFood(snake);
            if(snake.state == SnakeState.ALIVE)
                players.get(snake.playerId).nullifyScore();
            snakes.remove(id);
            snakesAlive--;
        }
        if(snakes.isEmpty()) {
            startNewGame();
            return;
        }

        addNeededFood();

    }

    private void startNewGame() throws InterruptedException {
        foods.clear();
        Thread.sleep(1000);
        stateId = 0;
        for(PlayerInfo player: players.values()){
            addNewSnake(player.id);
        }
        addNeededFood();
    }

    private void turnIntoFood(Snake snake){
        Random rand = new Random();
        double currentLuck;
        double chance = config.deadFoodProb;
        synchronized (this) {
            Coord head = snake.body.removeFirst();
            int curX = head.x;
            int curY = head.y;
            currentLuck = rand.nextDouble();
            if (currentLuck < chance)
                foods.add(new Coord(curX, curY));
            for (Coord coord : snake.body) {
                int countToIterate = Math.abs(coord.x + coord.y);
                Direction nextDir = coord.dirOfRelative();
                int nextStepX = nextDir.x;
                int nextStepY = nextDir.y;
                while (countToIterate-- != 0) {
                    curX += nextStepX;
                    curY += nextStepY;
                    currentLuck = rand.nextDouble();
                    if (currentLuck < chance)
                        foods.add(new Coord(curX, curY));
                }
            }
            snake.body.addFirst(head);
        }
    }

    public void changeSnakeDirection(int ownerId, Direction direction){
        if(!snakes.containsKey(ownerId))
            return;
        snakes.get(ownerId).changeDirection(direction);
    }

    public boolean addNewSnake(int playerId){
        ArrayList<Coord> emptyList = getEmptyTilesList();
        Random rand = new Random();
        while(!emptyList.isEmpty()){
            Coord nextCoord = emptyList.get(rand.nextInt(emptyList.size()));
            boolean isSuitable = true;
            for(int i = -2; i <= 2; i++){
                for(int j = -2; j <= 2; j++){
                    if(!emptyList.contains(new Coord(nextCoord.x + i, nextCoord.y + j)))
                        isSuitable = false;
                }
            }
            if(isSuitable) {
                snakes.put(playerId, new Snake(playerId, nextCoord, Direction.values()[rand.nextInt(Direction.values().length)]));
                snakesAlive++;
                return true;
            }
        }
        return false;
    }

    /**
     * Attempts to turn some number of empty tiles to food tiles, so that
     * total number of food equalled to
     */
    private void addNeededFood(){
        int foodToCreate = config.foodStatic + (int)((double)snakesAlive * config.foodPerPlayer);
        int foodOnField = foods.size();
        if(foodToCreate <= foodOnField)
            return;
        ArrayList<Coord> emptyList = getEmptyTilesList();
        Random rand = new Random();
        for(int i = 0; i < foodToCreate - foodOnField; i++){
            Coord nextFood = emptyList.get(rand.nextInt(emptyList.size()));
            foods.add(nextFood);
            emptyList.remove(nextFood);
        }
    }

    private ArrayList<Coord> getEmptyTilesList(){
        ArrayList<Coord> emptyList = new ArrayList<>(config.width * config.height);
        for(int i = 0; i < config.width; i++){
            for(int j = 0; j < config.height; j++){
                emptyList.add(new Coord(i, j));
            }
        }
        for(Coord food: foods){
            emptyList.remove(food);
        }

        for(Snake snake: snakes.values()){
            synchronized (this) {
                Coord head = snake.body.removeFirst();
                emptyList.remove(head);
                int curX = head.x;
                int curY = head.y;
                for (Coord coord : snake.body) {
                    int countToIterate = Math.abs(coord.x) + Math.abs(coord.y);
                    Direction nextDir = coord.dirOfRelative();
                    int nextStepX = nextDir.x;
                    int nextStepY = nextDir.y;
                    while (countToIterate-- != 0) {
                        curX = Math.floorMod(curX + nextStepX, config.width);
                        curY = Math.floorMod(curY + nextStepY, config.height);
                        emptyList.remove(new Coord(curX, curY));
                    }
                }
                snake.body.addFirst(head);
            }
        }
        return emptyList;
    }
}
