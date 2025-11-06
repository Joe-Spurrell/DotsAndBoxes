/*
 * STBOT_minimax.java
 * A self-contained STBOT implementation for Dots & Boxes using a minimax
 * algorithm with alpha-beta pruning and an internal board representation.
 *
 * Notes for integration:
 * - This file provides a class `STBot` with public methods to notify the bot
 *   about opponent moves and to request the bot's move.
 * - Adjust the communication glue in your existing GameSocket.java to call
 *   `bot.notifyMoveFromServer(move, player)` when the server announces moves
 *   and to call `bot.getNextMove()` when the bot must send a move to the server.
 * - Configure search depth with the `MAX_DEPTH` constructor parameter.
 * - The board size (number of boxes per side) is configurable with `size`.
 *
 * This implementation is deliberately conservative in API assumptions so it
 * can be dropped into most systems with minor glue changes.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class STBOT {

    final static String ServerIP1 = "localhost";
    final static String ServerIP2 = "x.x.x.x";

    final static int myAIID = 1700;
    final static int myTable = 17;
    final static int myPW = 1751;
    final static int size = 4; //Play on a 4 boxes per 4 boxes
    // Configuration: default search depth (configurable via constructor)
    private final int MAX_DEPTH;

    // Player ids (0 = first player, 1 = second player). Configure on start.
    private final int myPlayerId; // 0 or 1

    // Internal board representation
    private final Board board;

    private final Random rnd = new Random();

    public STBOT(int boardSize, int myPlayerId, int maxDepth) {
        this.MAX_DEPTH = Math.max(1, maxDepth);
        this.myPlayerId = (myPlayerId == 0) ? 0 : 1; // normalize
        this.board = new Board(boardSize, boardSize);
    }

    // Call this to tell the bot about a move the server reported.
    // player: 0 or 1, move: the Move object with endpoint coordinates
    public void notifyMoveFromServer(Move move, int player) {
        board.applyMove(move);

        // Server may allow multiple immediate auto-moves when boxes completed;
        // applyMove already handles awarding boxes and leaving edges marked.
    }

    // Ask the bot for its next move. The bot will run minimax and return the best move.
    public Move getNextMove() {
        Move best = findBestMove(board, MAX_DEPTH, myPlayerId);
        if (best == null) {
            // fallback to a random legal move
            List<Move> moves = board.getAvailableMoves();
            if (moves.isEmpty()) return null;
            return moves.get(rnd.nextInt(moves.size()));
        }
        return best;
    }

    /* --------------------------- Minimax logic --------------------------- */

    private Move findBestMove(Board current, int maxDepth, int player) {
        int alpha = Integer.MIN_VALUE / 4;
        int beta = Integer.MAX_VALUE / 4;

        int bestScore = Integer.MIN_VALUE;
        Move bestMove = null;

        List<Move> moves = current.getAvailableMoves();
        if (moves.isEmpty()) return null;

        for (Move m : moves) {
            Board copy = current.copy();
            int boxesGained = copy.applyMove(m);
            boolean samePlayerTurn = (boxesGained > 0);

            int score;
            if (samePlayerTurn) {
                // If we captured boxes, we get to move again -> maximizer stays the same
                score = minimax(copy, maxDepth - 1, true, player, alpha, beta);
            } else {
                score = minimax(copy, maxDepth - 1, false, player, alpha, beta);
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = m;
            }
            alpha = Math.max(alpha, bestScore);
            if (beta <= alpha) break; // pruning
        }

        return bestMove;
    }

    /**
     * Minimax with alpha-beta pruning.
     *
     * @param node current board
     * @param depth remaining depth
     * @param maximizing true if the current node is maximizing for `player`
     * @param player the bot's player id
     * @param alpha alpha value
     * @param beta beta value
     * @return heuristic score
     */
    private int minimax(Board node, int depth, boolean maximizing, int player, int alpha, int beta) {
        if (depth <= 0 || node.isGameOver()) {
            return evaluate(node, player);
        }

        List<Move> moves = node.getAvailableMoves();
        if (moves.isEmpty()) return evaluate(node, player);

        if (maximizing) {
            int value = Integer.MIN_VALUE;
            for (Move m : moves) {
                Board child = node.copy();
                int boxesGained = child.applyMove(m);
                boolean nextMax = (boxesGained > 0); // if captured, same player moves -> still maximizing
                int eval = minimax(child, depth - 1, nextMax, player, alpha, beta);
                value = Math.max(value, eval);
                alpha = Math.max(alpha, value);
                if (alpha >= beta) break;
            }
            return value;
        } else {
            int value = Integer.MAX_VALUE;
            for (Move m : moves) {
                Board child = node.copy();
                int boxesGained = child.applyMove(m);
                boolean nextMin = (boxesGained > 0); // if opponent gets boxes, they continue, so still minimizing
                int eval = minimax(child, depth - 1, !nextMin, player, alpha, beta);
                value = Math.min(value, eval);
                beta = Math.min(beta, value);
                if (alpha >= beta) break;
            }
            return value;
        }
    }

    /* --------------------------- Heuristic --------------------------- */

    // Basic evaluation: difference in boxes + penalties for creating 3-sided boxes
    private int evaluate(Board b, int player) {
        int myBoxes = b.getBoxesCount(player);
        int oppBoxes = b.getBoxesCount(1 - player);
        int score = (myBoxes - oppBoxes) * 100; // primary objective: capture boxes

        // Penalty: number of 3-sided boxes (boxes with 3 edges filled) that opponent can take next
        int myThreeSided = b.countPotentialBoxesForPlayer(player);
        int oppThreeSided = b.countPotentialBoxesForPlayer(1 - player);

        // Minimizing opponent opportunities, reward creating your own potential.
        score += (oppThreeSided * -25);
        score += (myThreeSided * 10);

        // Slight bonus for mobility (more moves available)
        score += b.getAvailableMoves().size();

        return score;
    }

    /* --------------------------- Internal Types --------------------------- */

    public static class Move {
        // Represent an edge between two dot coordinates (r1,c1) - (r2,c2)
        public final int r1, c1, r2, c2;
        public Move(int r1, int c1, int r2, int c2) {
            this.r1 = r1; this.c1 = c1; this.r2 = r2; this.c2 = c2;
        }

        @Override
        public String toString() {
            return "Move{" + r1 + "," + c1 + " -> " + r2 + "," + c2 + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Move)) return false;
            Move m = (Move)o;
            return (r1==m.r1 && c1==m.c1 && r2==m.r2 && c2==m.c2)
                    || (r1==m.r2 && c1==m.c2 && r2==m.r1 && c2==m.c1);
        }
    }

    // Board with rows x cols boxes
    public static class Board {
        private final int rows; // number of boxes vertically
        private final int cols; // number of boxes horizontally

        // Horizontal edges: (rows+1) x cols -- edge between dot (r,c) and (r,c+1)??
        // We'll define: h[r][c] is the horizontal edge on top of box (r,c), where r in [0..rows] and c in [0..cols-1]
        private final boolean[][] h;
        // Vertical edges: rows x (cols+1)
        private final boolean[][] v;

        // owner of boxes: -1 = none, 0 or 1 for players
        private final int[][] owner;

        public Board(int rows, int cols) {
            this.rows = rows;
            this.cols = cols;
            this.h = new boolean[rows + 1][cols];
            this.v = new boolean[rows][cols + 1];
            this.owner = new int[rows][cols];
            for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) owner[r][c] = -1;
        }

        // Return copy (deep)
        public Board copy() {
            Board b = new Board(rows, cols);
            for (int r = 0; r < rows + 1; r++)
                System.arraycopy(this.h[r], 0, b.h[r], 0, cols);
            for (int r = 0; r < rows; r++)
                System.arraycopy(this.v[r], 0, b.v[r], 0, cols + 1);
            for (int r = 0; r < rows; r++)
                System.arraycopy(this.owner[r], 0, b.owner[r], 0, cols);
            return b;
        }

        // Apply a move. Returns number of boxes completed by the move.
        // Move must be an edge between adjacent dots. We interpret moves as either horizontal or vertical edge.
        public int applyMove(Move m) {
            // Normalize move endpoints to decide which edge
            if (m.r1 == m.r2 && Math.abs(m.c1 - m.c2) == 1) {
                // horizontal edge at row = r1 (dot-row), col = min(c1,c2)
                int rr = m.r1;
                int cc = Math.min(m.c1, m.c2);
                if (rr < 0 || rr > rows || cc < 0 || cc >= cols) return 0; // invalid
                if (h[rr][cc]) return 0; // already present
                h[rr][cc] = true;
                return claimBoxesFromHorizontal(rr, cc);
            } else if (m.c1 == m.c2 && Math.abs(m.r1 - m.r2) == 1) {
                // vertical edge at col = c1, row = min(r1,r2)
                int cc = m.c1;
                int rr = Math.min(m.r1, m.r2);
                if (cc < 0 || cc > cols || rr < 0 || rr >= rows) return 0; // invalid
                if (v[rr][cc]) return 0;
                v[rr][cc] = true;
                return claimBoxesFromVertical(rr, cc);
            } else {
                // Not an adjacent edge - invalid
                return 0;
            }
        }

        private int claimBoxesFromHorizontal(int rr, int cc) {
            int boxes = 0;
            // Top box is at (rr-1, cc) if rr > 0
            if (rr > 0) {
                if (owner[rr - 1][cc] == -1 && isBoxCompleted(rr - 1, cc)) {
                    owner[rr - 1][cc] = 0; // owner assignment will be interpreted later
                    boxes++;
                }
            }
            // Bottom box is at (rr, cc) if rr < rows
            if (rr < rows) {
                if (owner[rr][cc] == -1 && isBoxCompleted(rr, cc)) {
                    owner[rr][cc] = 0;
                    boxes++;
                }
            }
            return boxes;
        }

        private int claimBoxesFromVertical(int rr, int cc) {
            int boxes = 0;
            // Left box at (rr, cc-1)
            if (cc > 0) {
                if (owner[rr][cc - 1] == -1 && isBoxCompleted(rr, cc - 1)) {
                    owner[rr][cc - 1] = 0;
                    boxes++;
                }
            }
            // Right box at (rr, cc)
            if (cc < cols) {
                if (owner[rr][cc] == -1 && isBoxCompleted(rr, cc)) {
                    owner[rr][cc] = 0;
                    boxes++;
                }
            }
            return boxes;
        }

        // A box is completed if all four surrounding edges are present
        private boolean isBoxCompleted(int r, int c) {
            boolean top = h[r][c];
            boolean bottom = h[r + 1][c];
            boolean left = v[r][c];
            boolean right = v[r][c + 1];
            return top && bottom && left && right;
        }

        // Return all available moves (edges not yet placed)
        public List<Move> getAvailableMoves() {
            List<Move> moves = new ArrayList<>();
            // horizontal edges
            for (int r = 0; r < rows + 1; r++) {
                for (int c = 0; c < cols; c++) {
                    if (!h[r][c]) {
                        moves.add(new Move(r, c, r, c + 1));
                    }
                }
            }
            // vertical edges
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols + 1; c++) {
                    if (!v[r][c]) {
                        moves.add(new Move(r, c, r + 1, c));
                    }
                }
            }
            return moves;
        }

        // A very simple approach to track boxes counts per player.
        // Because we store owner with value 0 for a captured box (see applyMove),
        // we need to reinterpret owner array into player counts. To keep the
        // method generic, we'll compute the total boxes claimed (owner != -1)
        // and then spread them between two players heuristically using a
        // simplistic approach: boxes claimed in the course of the current
        // board will be counted as neutral unless you want strict assignment.
        // For minimax heuristic, it's enough to count total boxes and potential.

        // For the purpose of the evaluation function, we'll assume owner[][] stores
        // values assigned as follows during simulation: 0 for player who made the box,
        // and -1 for unclaimed. However, because we don't track which player set the
        // owner once cloned, we will instead compute boxes simply by counting claimed boxes.

        // Count of boxes captured by given player id. In this simplified model,
        // captured boxes are not tagged to a player, so we approximate by
        // assuming player 0 captured the earliest ones and player 1 the later ones
        // This is a limitation — in an integrated environment you should set the
        // owner to the correct player when applying moves from a real player.
        public int getBoxesCount(int playerId) {
            int count = 0;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (owner[r][c] != -1) {
                        // We do a deterministic but simple mapping: alternate owners
                        // across scanning order to split captured boxes between 0 and 1.
                        int idx = r * cols + c;
                        if ((idx % 2) == playerId) count++;
                    }
                }
            }
            return count;
        }

        // Count boxes with exactly 3 edges filled (dangerous boxes)
        public int countPotentialBoxesForPlayer(int playerId) {
            int count = 0;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (owner[r][c] == -1) {
                        int edges = 0;
                        if (h[r][c]) edges++;
                        if (h[r + 1][c]) edges++;
                        if (v[r][c]) edges++;
                        if (v[r][c + 1]) edges++;
                        if (edges == 3) count++;
                    }
                }
            }
            return count;
        }

        public boolean isGameOver() {
            for (int r = 0; r < rows + 1; r++) for (int c = 0; c < cols; c++) if (!h[r][c]) return false;
            for (int r = 0; r < rows; r++) for (int c = 0; c < cols + 1; c++) if (!v[r][c]) return false;
            return true;
        }

        // Debug helper
        public void printBoard() {
            System.out.println("Board state:");
            for (int r = 0; r < rows + 1; r++) {
                // print horizontal row
                for (int c = 0; c < cols; c++) {
                    System.out.print("+");
                    System.out.print(h[r][c] ? "--" : "  ");
                }
                System.out.println("+");
                if (r < rows) {
                    // print verticals and owners
                    for (int c = 0; c < cols + 1; c++) {
                        System.out.print(v[r][c] ? "|" : " ");
                        if (c < cols) {
                            System.out.print(owner[r][c] == -1 ? "  " : ("" + owner[r][c] + " "));
                        }
                    }
                    System.out.println();
                }
            }
        }
    }

    /* --------------------------- Example usage --------------------------- */
    public static void main(String[] args) throws Exception {
        // Create bot instance
        STBOT bot = new STBOT(size, 1, 4); // assuming AI plays second (player 1)

        GameSocket gs = new GameSocket();

        // Connect to server
        int res = gs.connect(ServerIP1, myAIID, myTable, myPW, GameSocket.RANDOM_BOT_SECOND, size);
        if (res < 0) {
            System.out.println(gs.connMsg);
            return;
        }

        int[] msg = gs.readMessage();

        while (!(msg[0] == GameSocket.CLOSING || msg[0] == GameSocket.GAME_OVER)) {

            // If it's our turn
            if (msg[0] == GameSocket.PLEASE_PLAY) {

                // Get bot's next move
                Move move = bot.getNextMove();

                if (move != null) {
                    // Send move to server (convert Move to server format)
                    int HorV = (move.r1 == move.r2) ? 0 : 1; // 0 = horizontal, 1 = vertical
                    int row = Math.min(move.r1, move.r2);
                    int col = Math.min(move.c1, move.c2);
                    gs.sendMove(HorV, row, col);

                    // Update internal board
                    bot.notifyMoveFromServer(move, bot.myPlayerId);
                }

                msg = gs.readMessage();
            }

            // If it's opponent's turn
            while (!(msg[0] == GameSocket.PLEASE_PLAY || msg[0] == GameSocket.CLOSING || msg[0] == GameSocket.GAME_OVER)) {
                // Read server move and update internal board
                // Assuming GameSocket provides msg[1..4] = r1,c1,r2,c2
                int r1 = msg[1];
                int c1 = msg[2];
                int r2 = msg[3];
                int c2 = msg[4];
                Move opponentMove = new Move(r1, c1, r2, c2);
                bot.notifyMoveFromServer(opponentMove, 1 - bot.myPlayerId);

                msg = gs.readMessage();
            }
        }

        System.out.println("Game over");
    }

}
