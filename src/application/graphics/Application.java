package application.graphics;

import application.ApplicationControlThread;
import application.GameState;
import application.JoinableGameReceiverThread;
import application.JoinableTestThread;
import application.enums.Direction;
import application.enums.NodeRole;
import application.enums.PlayerType;
import application.gamedata.Coord;
import application.gamedata.GameConfig;
import application.gamedata.PlayerInfo;
import application.messages.AnnouncementMessage;
import jdk.jfr.Unsigned;

import javax.sql.rowset.Joinable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.*;
import java.util.EnumMap;
import java.util.TreeMap;

public class Application {

    private final JFrame window;
    private JPanel scorePanel;
    private final EnumMap<MenuIndex, JPanel> menuPanels = new EnumMap<MenuIndex, JPanel>(MenuIndex.class);
    private MenuIndex currentMenu = MenuIndex.MAIN;
    private SnakeCanvas snakeCanvas;

    private ApplicationControlThread gameController = null;
    private JoinableGameReceiverThread joinReceiverThread;


    private TreeMap<Integer, JLabel> playerLabels = new TreeMap<>();
    private TreeMap<Integer, JoinableGame> joinableGames = new TreeMap<>();

    private int firstPixX;
    private int firstPixY;
    private int stepPix;
    private int lastPixX;
    private int lastPixY;

    private GameConfig currentConfig = new GameConfig();

    private JTextField configWidthTextField, configHeightTextField,
                        configConstantFoodTextField, configFoodPerPlayerTextField,
                        configTurnDelayTextField, configFoodChanceTextField,
                        configPingFrequencyTextField, configTimeoutTextField;

    private JPanel joinMasterPanel, joinNumberOfPlayersPanel,
    joinFieldSizePanel, joinFoodPanel,
    joinTurnDurationPanel ,joinButtonPanel;

    private final int menuWidth = 800;
    private final int menuHeight = 600;
    private final int gameFieldWidth = 1000;
    private final int gameScoreWidth = 400;
    private final int gameHeight = 1000;

    private class JoinableGame{
        public final JLabel master = new JLabel();
        public final Component masterUpRigidBox = Box.createRigidArea(new Dimension(0, 5));
        public final Component masterBottomRigidBox = Box.createRigidArea(new Dimension(0, 5));
        public final JLabel numOfPlayers = new JLabel();
        public final Component numOfPlayersUpRigidBox = Box.createRigidArea(new Dimension(0, 5));
        public final Component numOfPlayersBottomRigidBox = Box.createRigidArea(new Dimension(0, 5));
        public final JLabel fieldSize = new JLabel();
        public final Component fieldSizeUpRigidBox = Box.createRigidArea(new Dimension(0, 5));
        public final Component fieldSizeBottomRigidBox = Box.createRigidArea(new Dimension(0, 5));
        public final JLabel foodOnField = new JLabel();
        public final Component foodOnFieldUpRigidBox = Box.createRigidArea(new Dimension(0, 5));
        public final Component foodOnFieldBottomRigidBox = Box.createRigidArea(new Dimension(0, 5));
        public final JLabel turnDuration = new JLabel();
        public final Component turnDurationUpRigidBox = Box.createRigidArea(new Dimension(0, 5));
        public final Component turnDurationBottomRigidBox = Box.createRigidArea(new Dimension(0, 5));
        public final JButton join = new JButton("Join");
        private long lastUpdate;
        private boolean canJoin;
    }

    private class SnakeCanvas extends Canvas{
        private GameState currentState;

        public void paint(Graphics g){
            super.paint(g);
            g.setColor(Color.BLACK);
            g.drawRect(firstPixX, firstPixY, firstPixX + currentState.config.width * stepPix, firstPixY + currentState.config.height * stepPix);
            g.setColor(Color.BLUE);
            for(GameState.Snake snake: currentState.snakes.values()){
                int curX = snake.body.peekFirst().x;
                int curY = snake.body.peekFirst().y;
                synchronized (snake){
                    Coord first = snake.body.removeFirst();
                    g.fillRect(curX * stepPix + firstPixX, curY * stepPix + firstPixY, stepPix, stepPix);
                    for(Coord coord: snake.body){
                        int countToPaint = Math.abs(coord.x) + Math.abs(coord.y);
                        Direction nextDir = coord.dirOfRelative();
                        int nextStepX = nextDir.x;
                        int nextStepY = nextDir.y;
                        while(countToPaint-- != 0){
                            curX = Math.floorMod(curX + nextStepX, currentState.config.width);
                            curY = Math.floorMod(curY + nextStepY, currentState.config.height);
                            g.fillRect(curX * stepPix + firstPixX, curY * stepPix + firstPixY, stepPix, stepPix);
                        }
                    }
                    snake.body.addFirst(first);
                }
            }
            g.setColor(Color.RED);
            for(Coord food: currentState.foods){
                g.fillRect(food.x * stepPix + firstPixX, food.y * stepPix + firstPixY, stepPix, stepPix);
            }
        }
        public void paintState(GameState state){
            this.currentState = state;
            this.paint(getGraphics());
        }
    }

    /**
     * Initializes and shows oKtopusSnake graphic application
     */
    public Application() throws IOException {
        window = new JFrame("oKtopusSnake");
        window.setSize(menuWidth, menuHeight);
        window.setResizable(false);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.getContentPane().setLayout(new BoxLayout(window.getContentPane(), BoxLayout.Y_AXIS));
        createMainMenu();
        createConfigMenu();
        createJoinMenu();
        createGameMenu();

        joinReceiverThread = new JoinableGameReceiverThread(this);
        joinReceiverThread.start();

        window.setVisible(true);
    }

    /**
     * Initializes main menu.
     */
    private void createMainMenu(){
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setSize(window.getSize());
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.setAlignmentY(Component.CENTER_ALIGNMENT);
        Application appLink = this;

        panel.add(Box.createVerticalGlue());
        JButton newGame = new JButton("Start new game");
        newGame.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showMenu(MenuIndex.GAME);
                GameConfig gameConfig = new GameConfig();
                stepPix = Math.min(940 / gameConfig.width, 920 / gameConfig.height);
                firstPixX = (940 - stepPix * gameConfig.width) / 2;
                firstPixY = (920 - stepPix * gameConfig.height) / 2;
                lastPixX = firstPixX + stepPix * gameConfig.width;
                lastPixY = firstPixY + stepPix * gameConfig.height;
                int shift = Math.min((970 - lastPixX) / 2, (930 - lastPixY) / 2);
                firstPixX += shift;
                firstPixY += shift;
                lastPixX += shift;
                lastPixY += shift;
                joinReceiverThread.interrupt();
                gameController = new ApplicationControlThread(gameConfig, appLink);
            }
        });
        newGame.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(newGame);

        JButton joinGame = new JButton("Join a game");
        joinGame.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showMenu(MenuIndex.JOIN);
            }
        });
        joinGame.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(joinGame);

        JButton config = new JButton("Game config");
        config.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showMenu(MenuIndex.CONFIG);
            }
        });
        config.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(config);

        JButton exit = new JButton("Exit");
        exit.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                System.exit(0);
            }
        });
        exit.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(exit);
        panel.add(Box.createVerticalGlue());

        panel.setVisible(true);
        window.add(panel);
        menuPanels.put(MenuIndex.MAIN, panel);
    }

    /**
     * Initializes menu where one can change game config
     */
    private void createConfigMenu(){
        JPanel panel = new JPanel();
        panel.setAlignmentY(Component.CENTER_ALIGNMENT);
        panel.setSize(window.getSize());
        panel.setVisible(false);

        panel.add(Box.createRigidArea(new Dimension(800, 150)));

        JPanel firstBlock = new JPanel();
        configWidthTextField = new JTextField(10);
        configHeightTextField = new JTextField(10);
        firstBlock.add(Box.createRigidArea(new Dimension(20, 0)));
        firstBlock.add(new JLabel("Field width: "));
        firstBlock.add(configWidthTextField);
        firstBlock.add(Box.createRigidArea(new Dimension(140, 0)));
        firstBlock.add(configHeightTextField);
        firstBlock.add(new JLabel(": Field height"));

        firstBlock.add(Box.createRigidArea(new Dimension(20, 0)));
        firstBlock.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(firstBlock);

        JPanel secondBlock = new JPanel();
        configConstantFoodTextField = new JTextField(10);
        configFoodPerPlayerTextField = new JTextField(10);
        secondBlock.add(Box.createRigidArea(new Dimension(20, 0)));
        secondBlock.add(new JLabel("Constant food: "));
        secondBlock.add(configConstantFoodTextField);
        secondBlock.add(Box.createRigidArea(new Dimension(140, 0)));
        secondBlock.add(configFoodPerPlayerTextField);
        secondBlock.add(new JLabel(": Food per player"));

        secondBlock.add(Box.createRigidArea(new Dimension(17, 0)));
        secondBlock.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(secondBlock);

        JPanel thirdBlock = new JPanel();
        configTurnDelayTextField = new JTextField(10);
        configFoodChanceTextField = new JTextField(10);
        thirdBlock.add(Box.createRigidArea(new Dimension(70, 0)));
        thirdBlock.add(new JLabel("Turn delay: "));
        thirdBlock.add(configTurnDelayTextField);
        thirdBlock.add(Box.createRigidArea(new Dimension(140, 0)));
        thirdBlock.add(configFoodChanceTextField);
        thirdBlock.add(new JLabel(": Food chance on death"));
        thirdBlock.add(Box.createRigidArea(new Dimension(10, 0)));
        thirdBlock.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(thirdBlock);

        JPanel forthBlock = new JPanel();
        configPingFrequencyTextField = new JTextField(10);
        configTimeoutTextField = new JTextField(10);
        forthBlock.add(Box.createRigidArea(new Dimension(70, 0)));
        forthBlock.add(new JLabel("Ping delay: "));
        forthBlock.add(configPingFrequencyTextField);
        forthBlock.add(Box.createRigidArea(new Dimension(140, 0)));
        forthBlock.add(configTimeoutTextField);
        forthBlock.add(new JLabel(": Node timeout delay"));
        forthBlock.add(Box.createRigidArea(new Dimension(25, 0)));
        forthBlock.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(forthBlock);

        panel.add(Box.createRigidArea(new Dimension(800, 50)));

        JButton save = new JButton("Save config");
        JButton defaultConfig = new JButton("Set defaults");
        JButton back = new JButton("Return to main menu");

        save.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(areSettingsSuitable())
                    currentConfig = new GameConfig(
                        Integer.parseInt(configWidthTextField.getText()),
                        Integer.parseInt(configHeightTextField.getText()),
                        Integer.parseInt(configConstantFoodTextField.getText()),
                        Double.parseDouble(configFoodPerPlayerTextField.getText()),
                        Integer.parseInt(configTurnDelayTextField.getText()),
                        Double.parseDouble(configFoodChanceTextField.getText()),
                        Integer.parseInt(configPingFrequencyTextField.getText()),
                        Integer.parseInt(configTimeoutTextField.getText())
                    );
                else
                    restoreConfigTextFields();
            }
        });
        defaultConfig.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                currentConfig = new GameConfig();
                restoreConfigTextFields();
            }
        });
        back.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                restoreConfigTextFields();
                showMenu(MenuIndex.MAIN);
            }
        });

        panel.add(save);
        panel.add(back);
        panel.add(defaultConfig);

        restoreConfigTextFields();

        window.add(panel);
        menuPanels.put(MenuIndex.CONFIG, panel);
    }

    /**
     * Initializes menu where one would enter address of
     * existing game's host.
     */
    private void createJoinMenu(){
        JPanel panel = new JPanel();
        panel.setSize(window.getSize());
        panel.setVisible(false);

        joinMasterPanel = new JPanel();
        joinNumberOfPlayersPanel = new JPanel();
        joinFieldSizePanel = new JPanel();
        joinFoodPanel = new JPanel();
        joinTurnDurationPanel = new JPanel();
        joinButtonPanel = new JPanel();
        joinMasterPanel.setLayout(new BoxLayout(joinMasterPanel, BoxLayout.Y_AXIS));
        joinNumberOfPlayersPanel.setLayout(new BoxLayout(joinNumberOfPlayersPanel, BoxLayout.Y_AXIS));
        joinFieldSizePanel.setLayout(new BoxLayout(joinFieldSizePanel, BoxLayout.Y_AXIS));
        joinFoodPanel.setLayout(new BoxLayout(joinFoodPanel, BoxLayout.Y_AXIS));
        joinTurnDurationPanel.setLayout(new BoxLayout(joinTurnDurationPanel, BoxLayout.Y_AXIS));
        joinButtonPanel.setLayout(new BoxLayout(joinButtonPanel, BoxLayout.Y_AXIS));

        JScrollPane joinMasterScrollPanel = new JScrollPane(joinMasterPanel);
        JScrollPane joinNumberOfPlayersScrollPanel = new JScrollPane(joinNumberOfPlayersPanel);
        JScrollPane joinFieldSizeScrollPanel = new JScrollPane(joinFieldSizePanel);
        JScrollPane joinFoodScrollPanel = new JScrollPane(joinFoodPanel);
        JScrollPane joinTurnDurationScrollPanel = new JScrollPane(joinTurnDurationPanel);
        JScrollPane joinButtonScrollPanel = new JScrollPane(joinButtonPanel);

        joinMasterScrollPanel.setPreferredSize(new Dimension(200, 520));
        joinNumberOfPlayersScrollPanel.setPreferredSize(new Dimension(40,520));
        joinFieldSizeScrollPanel.setPreferredSize(new Dimension(100, 520));
        joinFoodScrollPanel.setPreferredSize(new Dimension(100, 520));
        joinTurnDurationScrollPanel.setPreferredSize(new Dimension(100, 520));
        joinButtonScrollPanel.setPreferredSize(new Dimension(100, 520));

        joinMasterScrollPanel.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        joinNumberOfPlayersScrollPanel.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        joinFieldSizeScrollPanel.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        joinFoodScrollPanel.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        joinTurnDurationScrollPanel.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        joinButtonScrollPanel.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        joinMasterScrollPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        joinNumberOfPlayersScrollPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        joinFieldSizeScrollPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        joinFoodScrollPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        joinTurnDurationScrollPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        joinButtonScrollPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        BoundedRangeModel barOfAll = joinButtonScrollPanel.getVerticalScrollBar().getModel();
        joinMasterScrollPanel.getVerticalScrollBar().setModel(barOfAll);
        joinNumberOfPlayersScrollPanel.getVerticalScrollBar().setModel(barOfAll);
        joinFieldSizeScrollPanel.getVerticalScrollBar().setModel(barOfAll);
        joinFoodScrollPanel.getVerticalScrollBar().setModel(barOfAll);
        joinTurnDurationScrollPanel.getVerticalScrollBar().setModel(barOfAll);

        //joinButtonScrollPanel.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        panel.add(joinMasterScrollPanel);
        panel.add(joinNumberOfPlayersScrollPanel);
        panel.add(joinFieldSizeScrollPanel);
        panel.add(joinFoodScrollPanel);
        panel.add(joinTurnDurationScrollPanel);
        panel.add(joinButtonScrollPanel);

        JButton back = new JButton("Return to main menu");
        back.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showMenu(MenuIndex.MAIN);
            }
        });
        back.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(back);

        window.add(panel);
        menuPanels.put(MenuIndex.JOIN, panel);
    }

    /**
     * Initializes game field
     */
    private void createGameMenu(){
        JPanel panel = new JPanel();
        panel.setSize(gameFieldWidth + gameScoreWidth, gameHeight);
        panel.setVisible(false);

        snakeCanvas = new SnakeCanvas();
        snakeCanvas.setSize(gameFieldWidth, gameHeight);
        panel.add(snakeCanvas);

        snakeCanvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_W || e.getKeyCode() == KeyEvent.VK_UP)
                    gameController.changeSnakeDirection(Direction.UP);
                else if(e.getKeyCode() == KeyEvent.VK_S || e.getKeyCode() == KeyEvent.VK_DOWN)
                    gameController.changeSnakeDirection(Direction.DOWN);
                else if(e.getKeyCode() == KeyEvent.VK_D || e.getKeyCode() == KeyEvent.VK_RIGHT)
                    gameController.changeSnakeDirection(Direction.RIGHT);
                else if(e.getKeyCode() == KeyEvent.VK_A || e.getKeyCode() == KeyEvent.VK_LEFT)
                    gameController.changeSnakeDirection(Direction.LEFT);
                e.consume();
            }
        });

        JPanel rightGamePanel = new JPanel();
        rightGamePanel.setSize(gameScoreWidth, gameHeight);
        rightGamePanel.setLayout(new BoxLayout(rightGamePanel, BoxLayout.Y_AXIS));

        scorePanel = new JPanel();
        scorePanel.setLayout(new BoxLayout(scorePanel, BoxLayout.Y_AXIS));

        JButton backToMenu = new JButton("Back to main menu");
        backToMenu.setAlignmentX(Component.CENTER_ALIGNMENT);
        backToMenu.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                gameController.interrupt();
                gameController = null;
                scorePanel.removeAll();
                playerLabels.clear();
                joinReceiverThread.start();
                showMenu(MenuIndex.MAIN);
            }
        });
        rightGamePanel.add(scorePanel);
        rightGamePanel.add(Box.createRigidArea(new Dimension(0, 200)));
        rightGamePanel.add(backToMenu);

        panel.add(rightGamePanel);

        window.add(panel);
        menuPanels.put(MenuIndex.GAME, panel);
    }

    /**
     * Shows menu indicated by MenuIndex
     * @param menuIndex index of menu to show
     */
    public void showMenu(MenuIndex menuIndex){
        menuPanels.get(currentMenu).setVisible(false);
        if(menuIndex == MenuIndex.GAME)
            window.setSize(gameFieldWidth + gameScoreWidth, gameHeight);
        else
            window.setSize(menuWidth, menuHeight);
        currentMenu = menuIndex;
        menuPanels.get(currentMenu).setVisible(true);
    }

    public void paintState(GameState state){
        SwingUtilities.invokeLater(new Runnable(){
           @Override
           public void run() {
               updatePlayerLabels(state);
               if(state.players.size() != playerLabels.size()) {
                   addPlayerLabels(state);
                   window.repaint();
               }
               snakeCanvas.paintState(state);
           }
       });
    }

    private void updatePlayerLabels(GameState state){
        for(Integer id: playerLabels.keySet()){
            playerLabels.get(id).setText(state.players.get(id).name + ": " + state.players.get(id).getScore());
        }
    }

    private void addPlayerLabels(GameState state){
        for(PlayerInfo player: state.players.values()){
            if(!playerLabels.containsKey(player.id)){
                JLabel label = new JLabel(player.name + ": " + player.getScore());
                label.setAlignmentX(Component.CENTER_ALIGNMENT);
                scorePanel.add(label);
                scorePanel.updateUI();
                playerLabels.put(player.id, label);
            }
        }
    }

    private boolean areSettingsSuitable(){
        try{
            int width = Integer.parseInt(configWidthTextField.getText());
            int height = Integer.parseInt(configHeightTextField.getText());
            int foodStatic = Integer.parseInt(configConstantFoodTextField.getText());
            double foodPerPlayer = Double.parseDouble(configFoodPerPlayerTextField.getText());
            int iterationDelayMs = Integer.parseInt(configConstantFoodTextField.getText());
            double deadFoodProb = Double.parseDouble(configFoodChanceTextField.getText());
            int pingDelayMs = Integer.parseInt(configPingFrequencyTextField.getText());
            int nodeTimeoutMs = Integer.parseInt(configTimeoutTextField.getText());
            boolean isSuitable;
            isSuitable = GameConfig.minimals.width <= width;
            isSuitable &= GameConfig.minimals.height <= height;
            isSuitable &= GameConfig.minimals.foodStatic <= foodStatic;
            isSuitable &= GameConfig.minimals.foodPerPlayer <= foodPerPlayer;
            isSuitable &= GameConfig.minimals.iterationDelayMs <= iterationDelayMs;
            isSuitable &= GameConfig.minimals.deadFoodProb <= deadFoodProb;
            isSuitable &= GameConfig.minimals.pingDelayMs <= pingDelayMs;
            isSuitable &= GameConfig.minimals.nodeTimeoutMs <= nodeTimeoutMs;
            isSuitable &= width <= GameConfig.maximums.width;
            isSuitable &= height <= GameConfig.maximums.height;
            isSuitable &= foodStatic <= GameConfig.maximums.foodStatic;
            isSuitable &= foodPerPlayer <= GameConfig.maximums.foodPerPlayer;
            isSuitable &= iterationDelayMs <= GameConfig.maximums.iterationDelayMs;
            isSuitable &= deadFoodProb <= GameConfig.maximums.deadFoodProb;
            isSuitable &= pingDelayMs <= GameConfig.maximums.pingDelayMs;
            isSuitable &= nodeTimeoutMs <= GameConfig.maximums.nodeTimeoutMs;
            if(!isSuitable)
                JOptionPane.showMessageDialog(window, "Incorrect config!");
            return isSuitable;
        }catch(NumberFormatException e){
            JOptionPane.showMessageDialog(window, "Incorrect config!");
            return false;
        }
    }

    private void restoreConfigTextFields(){
        configWidthTextField.setText(String.valueOf(currentConfig.width));
        configHeightTextField.setText(String.valueOf(currentConfig.height));
        configConstantFoodTextField.setText(String.valueOf(currentConfig.foodStatic));
        configFoodPerPlayerTextField.setText(String.valueOf(currentConfig.foodPerPlayer));
        configTurnDelayTextField.setText(String.valueOf(currentConfig.iterationDelayMs));
        configFoodChanceTextField.setText(String.valueOf(currentConfig.deadFoodProb));
        configPingFrequencyTextField.setText(String.valueOf(currentConfig.pingDelayMs));
        configTimeoutTextField.setText(String.valueOf(currentConfig.nodeTimeoutMs));
    }

    public void processAnnouncementMessage(AnnouncementMessage message, Inet4Address address){
        int ipaddr = ipaddrToInt(address);
        if(joinableGames.containsKey(ipaddr))
            updateJoinableGame(message, address);
        else
            addJoinableGame(message, address);
    }

    private void updateJoinableGame(AnnouncementMessage message, Inet4Address address){
        JoinableGame game = joinableGames.get(ipaddrToInt(address));
        int masterIndex;
        for(masterIndex = 0; masterIndex < message.players.length; masterIndex++){
            if(message.players[masterIndex].role == NodeRole.MASTER)
                break;
        }
        game.master.setText(message.players[masterIndex].name + "[" + address.getHostAddress() + "]");
        game.numOfPlayers.setText(String.valueOf(message.players.length));
        game.fieldSize.setText(message.config.width + "x" + message.config.height);
        game.foodOnField.setText(message.config.foodStatic + " + " + message.config.foodPerPlayer + "x");
        game.turnDuration.setText(message.config.iterationDelayMs + "ms");
        game.lastUpdate = System.currentTimeMillis();
        menuPanels.get(MenuIndex.JOIN).updateUI();
    }

    private void addJoinableGame(AnnouncementMessage message, Inet4Address address){
        JoinableGame game = new JoinableGame();
        int masterIndex;
        for(masterIndex = 0; masterIndex < message.players.length; masterIndex++){
            if(message.players[masterIndex].role == NodeRole.MASTER)
                break;
        }
        game.master.setText(message.players[masterIndex].name + "[" + address.getHostAddress() + "]");
        game.numOfPlayers.setText(String.valueOf(message.players.length));
        game.fieldSize.setText(message.config.width + "x" + message.config.height);
        game.foodOnField.setText(message.config.foodStatic + " + " + message.config.foodPerPlayer + "x");
        game.turnDuration.setText(message.config.iterationDelayMs + "ms");
        game.lastUpdate = System.currentTimeMillis();
        game.canJoin = message.canJoin;

        game.master.setAlignmentX(Component.CENTER_ALIGNMENT);
        game.numOfPlayers.setAlignmentX(Component.CENTER_ALIGNMENT);
        game.fieldSize.setAlignmentX(Component.CENTER_ALIGNMENT);
        game.foodOnField.setAlignmentX(Component.CENTER_ALIGNMENT);
        game.turnDuration.setAlignmentX(Component.CENTER_ALIGNMENT);
        game.join.setAlignmentX(Component.CENTER_ALIGNMENT);

        joinMasterPanel.add(game.masterUpRigidBox);
        joinMasterPanel.add(game.master);
        joinMasterPanel.add(game.masterBottomRigidBox);
        joinNumberOfPlayersPanel.add(game.numOfPlayersUpRigidBox);
        joinNumberOfPlayersPanel.add(game.numOfPlayers);
        joinNumberOfPlayersPanel.add(game.numOfPlayersBottomRigidBox);
        joinFieldSizePanel.add(game.fieldSizeUpRigidBox);
        joinFieldSizePanel.add(game.fieldSize);
        joinFieldSizePanel.add(game.fieldSizeBottomRigidBox);
        joinFoodPanel.add(game.foodOnFieldUpRigidBox);
        joinFoodPanel.add(game.foodOnField);
        joinFoodPanel.add(game.foodOnFieldBottomRigidBox);
        joinTurnDurationPanel.add(game.turnDurationUpRigidBox);
        joinTurnDurationPanel.add(game.turnDuration);
        joinTurnDurationPanel.add(game.turnDurationBottomRigidBox);
        joinButtonPanel.add(game.join);
        joinableGames.put(ipaddrToInt(address), game);
        menuPanels.get(MenuIndex.JOIN).updateUI();
    }

    private int ipaddrToInt(Inet4Address addr){
        String[] addrBytes = addr.getHostAddress().split("\\.");
        int ipaddrInt = 0;
        for(int i = 0; i < 4; i++){
            ipaddrInt += (Integer.parseInt(addrBytes[3 - i]) & 0b11111111)  << (i * 8);
        }
        return ipaddrInt;
    }

    public void removeOutdatedGames(){
        for(Integer gameIndex: joinableGames.keySet()){
            if(System.currentTimeMillis() - joinableGames.get(gameIndex).lastUpdate > 3000){
                removeJoinableGame(gameIndex);
            }
        }
        menuPanels.get(MenuIndex.JOIN).updateUI();
    }

    public void removeJoinableGame(int gameIndex){
        JoinableGame game = joinableGames.get(gameIndex);
        joinMasterPanel.remove(game.masterUpRigidBox);
        joinMasterPanel.remove(game.master);
        joinMasterPanel.remove(game.masterBottomRigidBox);
        joinNumberOfPlayersPanel.remove(game.numOfPlayersUpRigidBox);
        joinNumberOfPlayersPanel.remove(game.numOfPlayers);
        joinNumberOfPlayersPanel.remove(game.numOfPlayersBottomRigidBox);
        joinFieldSizePanel.remove(game.fieldSizeUpRigidBox);
        joinFieldSizePanel.remove(game.fieldSize);
        joinFieldSizePanel.remove(game.fieldSizeBottomRigidBox);
        joinFoodPanel.remove(game.foodOnFieldUpRigidBox);
        joinFoodPanel.remove(game.foodOnField);
        joinFoodPanel.remove(game.foodOnFieldBottomRigidBox);
        joinTurnDurationPanel.remove(game.turnDurationUpRigidBox);
        joinTurnDurationPanel.remove(game.turnDuration);
        joinTurnDurationPanel.remove(game.turnDurationBottomRigidBox);
        joinButtonPanel.remove(game.join);
        joinableGames.remove(gameIndex);
    }

    public void clearJoinableGames(){
        joinableGames.clear();
        menuPanels.get(MenuIndex.JOIN).updateUI();
    }

}
