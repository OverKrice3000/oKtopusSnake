package application.gamedata;

import java.io.Serializable;

/**
 * This class represents config of the game, which can't be changed
 * once game has started.
 * <p>
 * Objects of this class are immutable.
 */
public class GameConfig implements Serializable {
    public final int width;           // Ширина поля в клетках (от 10 до 100)
    public final int height;          // Высота поля в клетках (от 10 до 100)
    public final int foodStatic;      // Количество клеток с едой, независимо от числа игроков (от 0 до 100)
    public final double foodPerPlayer;  // Количество клеток с едой, на каждого игрока (вещественный коэффициент от 0 до 100)
    public final int iterationDelayMs; // Задержка между ходами (сменой состояний) в игре, в миллисекундах (от 1 до 10000)
    public final double deadFoodProb; // Вероятность превращения мёртвой клетки в еду (от 0 до 1).
    public final int pingDelayMs;   // Задержка между отправкой ping-сообщений, в миллисекундах (от 1 до 10000)
    public final int nodeTimeoutMs; // Таймаут, после которого считаем что узел-сосед отпал, в миллисекундах (от 1 до 10000)

    public final static GameConfig minimals = new GameConfig(
            5, 5, 0, 0,
            1, 0, 1, 1
    );
    public final static GameConfig maximums = new GameConfig(
            300, 300, 300, 300,
            10000, 1, 10000, 10000
    );

    public GameConfig(){
        width = 100;
        height = 100;
        foodStatic = 1;
        foodPerPlayer = 1;
        iterationDelayMs = 1;
        deadFoodProb = 0.1;
        pingDelayMs = 100;
        nodeTimeoutMs = 800;
    }

    public GameConfig(int width, int height, int foodStatic, double foodPerPlayer, int iterationDelayMs, double deadFoodProb, int pingDelayMs, int nodeTimeoutMs){
        this.width = width;
        this.height = height;
        this.foodStatic = foodStatic;
        this.foodPerPlayer = foodPerPlayer;
        this.iterationDelayMs = iterationDelayMs;
        this.deadFoodProb = deadFoodProb;
        this.pingDelayMs = pingDelayMs;
        this.nodeTimeoutMs = nodeTimeoutMs;
    }
}
