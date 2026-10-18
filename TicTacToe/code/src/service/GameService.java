package service;

import model.GameState;

public class GameService {
    private GameState gameState;

    public GameService() {
        gameState = new GameState();
    }

    public GameState getGameState() {
        return gameState;
    }

    public String handleMove(int row, int col) {
        if (gameState.makeMove(row, col)) {
            char winner = gameState.checkWinner();
            if (winner != '\0') {
                gameState.setGameActive(false);
                return "Player " + winner + " wins!";
            } else if (gameState.isBoardFull()) {
                gameState.setGameActive(false);
                return "It's a draw!";
            } else {
                gameState.switchPlayer();
                return "Player " + gameState.getCurrentPlayer() + "'s turn";
            }
        }
        return "Invalid move!";
    }

    public void resetGame() {
        gameState.resetBoard();
    }
}