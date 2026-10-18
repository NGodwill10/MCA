import javax.swing.*;
import java.awt.*;
import model.GameState;
import service.GameService;

public class Main {
    public static void main(String[] args) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int screenWidth = (int) screenSize.getWidth();
        int screenHeight = (int) screenSize.getHeight();

        JFrame frame = new JFrame("Tic-Tac-Toe");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(screenWidth, screenHeight);
        frame.setLayout(new BorderLayout());

        JLabel titleLabel = new JLabel("Tic-Tac-Toe", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 36));
        titleLabel.setPreferredSize(new Dimension(screenWidth, screenHeight / 10));
        titleLabel.setOpaque(true);
        titleLabel.setBackground(new Color(100, 149, 237));
        titleLabel.setForeground(Color.WHITE);
        frame.add(titleLabel, BorderLayout.NORTH);

        JPanel leftPanel = new JPanel();
        leftPanel.setPreferredSize(new Dimension(screenWidth / 4, screenHeight - screenHeight / 10));
        leftPanel.setLayout(new GridLayout(2, 1, 10, 10));
        leftPanel.setBackground(new Color(240, 248, 255));

        JButton playPauseButton = new JButton("Play/Pause");
        JButton resetBoardButton = new JButton("Reset Board");
        leftPanel.add(playPauseButton);
        leftPanel.add(resetBoardButton);
        frame.add(leftPanel, BorderLayout.WEST);

        JPanel boardPanel = new JPanel();
        boardPanel.setPreferredSize(new Dimension(screenWidth / 2, screenHeight - screenHeight / 10));
        boardPanel.setLayout(new GridLayout(3, 3, 5, 5));
        boardPanel.setBackground(new Color(245, 245, 245));

        JButton[][] buttons = new JButton[3][3];
        GameService gameService = new GameService();
        JLabel statusLabel = new JLabel("Player X's turn", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 20));
        frame.add(statusLabel, BorderLayout.SOUTH);

        JPanel overlayPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                char[][] board = gameService.getGameState().getBoard();
                Graphics2D g2 = (Graphics2D) g;
                g2.setStroke(new BasicStroke(5));
                g2.setColor(Color.RED);

                for (int i = 0; i < 3; i++) {
                    if (board[i][0] == board[i][1] && board[i][1] == board[i][2] && board[i][0] != '\0') {
                        g2.drawLine(50, (i * 100) + 50, 250, (i * 100) + 50);
                    }
                    if (board[0][i] == board[1][i] && board[1][i] == board[2][i] && board[0][i] != '\0') {
                        g2.drawLine((i * 100) + 50, 50, (i * 100) + 50, 250);
                    }
                }
                if (board[0][0] == board[1][1] && board[1][1] == board[2][2] && board[0][0] != '\0') {
                    g2.drawLine(50, 50, 250, 250);
                }
                if (board[0][2] == board[1][1] && board[1][1] == board[2][0] && board[0][2] != '\0') {
                    g2.drawLine(250, 50, 50, 250);
                }
            }
        };
        overlayPanel.setOpaque(false);
        overlayPanel.setPreferredSize(new Dimension(screenWidth / 2, screenHeight - screenHeight / 10));
        frame.add(overlayPanel, BorderLayout.CENTER);

        JTextArea scoreList = new JTextArea();
        scoreList.setEditable(false);
        scoreList.setFont(new Font("Arial", Font.PLAIN, 16));
        scoreList.setText("Score List:\n");

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                buttons[i][j] = new JButton();
                buttons[i][j].setFont(new Font("Arial", Font.BOLD, 24));
                buttons[i][j].setBackground(new Color(173, 216, 230));
                int row = i, col = j;
                buttons[i][j].addActionListener(e -> {
                    if (gameService.getGameState().isGameActive()) {
                        String result = gameService.handleMove(row, col);
                        GameState gameState = gameService.getGameState();
                        buttons[row][col].setText(String.valueOf(gameState.getBoard()[row][col]));
                        statusLabel.setText(result);
                        overlayPanel.repaint();
                        if (!gameState.isGameActive()) {
                            scoreList.append(result + "\n");
                        }
                    }
                });
                boardPanel.add(buttons[i][j]);
            }
        }
        frame.add(boardPanel, BorderLayout.CENTER);

        resetBoardButton.addActionListener(e -> {
            gameService.resetGame();
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    buttons[i][j].setText("");
                }
            }
            statusLabel.setText("Player X's turn");
            overlayPanel.repaint();
        });

        JPanel rightPanel = new JPanel();
        rightPanel.setPreferredSize(new Dimension(screenWidth / 4, screenHeight - screenHeight / 10));
        rightPanel.setLayout(new BorderLayout());
        rightPanel.setBackground(new Color(240, 248, 255));

        JScrollPane scrollPane = new JScrollPane(scoreList);
        rightPanel.add(scrollPane, BorderLayout.CENTER);

        JButton resetScoreButton = new JButton("Reset Score");
        resetScoreButton.addActionListener(e -> {
            scoreList.setText("Score List:\n");
        });
        rightPanel.add(resetScoreButton, BorderLayout.SOUTH);
        frame.add(rightPanel, BorderLayout.EAST);

        frame.setVisible(true);
    }
}