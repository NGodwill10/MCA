package model;

public class GameState {
    private char[][] board;
    private char currentPlayer;
    private boolean gameActive;

    public GameState() {
        board = new char[3][3];
        currentPlayer = 'X';
        gameActive = true;
    }

    public char[][] getBoard() {
        return board;
    }

    public char getCurrentPlayer() {
        return currentPlayer;
    }

    public boolean isGameActive() {
        return gameActive;
    }

    public void setGameActive(boolean gameActive) {
        this.gameActive = gameActive;
    }

    public void switchPlayer() {
        currentPlayer = (currentPlayer == 'X') ? 'O' : 'X';
    }

    public boolean makeMove(int row, int col) {
        if (board[row][col] == '\0' && gameActive) {
            board[row][col] = currentPlayer;
            return true;
        }
        return false;
    }

    public char checkWinner() {
        for (int i = 0; i < 3; i++) {
            if (board[i][0] == board[i][1] && board[i][1] == board[i][2] && board[i][0] != '\0')
                return board[i][0];
            if (board[0][i] == board[1][i] && board[1][i] == board[2][i] && board[0][i] != '\0')
                return board[0][i];
        }
        if (board[0][0] == board[1][1] && board[1][1] == board[2][2] && board[0][0] != '\0')
            return board[0][0];
        if (board[0][2] == board[1][1] && board[1][1] == board[2][0] && board[0][2] != '\0')
            return board[0][2];
        return '\0';
    }

    public boolean isBoardFull() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i][j] == '\0')
                    return false;
            }
        }
        return true;
    }

    public void resetBoard() {
        board = new char[3][3];
        currentPlayer = 'X';
        gameActive = true;
    }
}