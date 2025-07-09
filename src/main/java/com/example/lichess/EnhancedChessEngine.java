package com.example.lichess;

import java.util.*;

public class EnhancedChessEngine {
    private static final int MAX_DEPTH = 4; // Search depth
    private static final int INFINITY = 999999;
    
    // Piece values in centipawns
    private static final Map<Character, Integer> PIECE_VALUES;
    static {
        Map<Character, Integer> values = new HashMap<>();
        values.put('P', 100);   values.put('p', -100);  // Pawn
        values.put('N', 320);   values.put('n', -320);  // Knight
        values.put('B', 330);   values.put('b', -330);  // Bishop
        values.put('R', 500);   values.put('r', -500);  // Rook
        values.put('Q', 900);   values.put('q', -900);  // Queen
        values.put('K', 20000); values.put('k', -20000); // King
        PIECE_VALUES = Collections.unmodifiableMap(values);
    }
    
    // Positional bonus tables (from white's perspective)
    private static final int[][] PAWN_TABLE = {
        {0,  0,  0,  0,  0,  0,  0,  0},
        {50, 50, 50, 50, 50, 50, 50, 50},
        {10, 10, 20, 30, 30, 20, 10, 10},
        {5,  5, 10, 25, 25, 10,  5,  5},
        {0,  0,  0, 20, 20,  0,  0,  0},
        {5, -5,-10,  0,  0,-10, -5,  5},
        {5, 10, 10,-20,-20, 10, 10,  5},
        {0,  0,  0,  0,  0,  0,  0,  0}
    };
    
    private static final int[][] KNIGHT_TABLE = {
        {-50,-40,-30,-30,-30,-30,-40,-50},
        {-40,-20,  0,  0,  0,  0,-20,-40},
        {-30,  0, 10, 15, 15, 10,  0,-30},
        {-30,  5, 15, 20, 20, 15,  5,-30},
        {-30,  0, 15, 20, 20, 15,  0,-30},
        {-30,  5, 10, 15, 15, 10,  5,-30},
        {-40,-20,  0,  5,  5,  0,-20,-40},
        {-50,-40,-30,-30,-30,-30,-40,-50}
    };
    
    private static final int[][] BISHOP_TABLE = {
        {-20,-10,-10,-10,-10,-10,-10,-20},
        {-10,  0,  0,  0,  0,  0,  0,-10},
        {-10,  0,  5, 10, 10,  5,  0,-10},
        {-10,  5,  5, 10, 10,  5,  5,-10},
        {-10,  0, 10, 10, 10, 10,  0,-10},
        {-10, 10, 10, 10, 10, 10, 10,-10},
        {-10,  5,  0,  0,  0,  0,  5,-10},
        {-20,-10,-10,-10,-10,-10,-10,-20}
    };
    
    private ChessEngine baseEngine;
    
    public EnhancedChessEngine() {
        this.baseEngine = new ChessEngine();
    }
    
    public EnhancedChessEngine(ChessEngine baseEngine) {
        this.baseEngine = baseEngine;
    }
    
    /**
     * Find the best move using simplified evaluation
     */
    public Move findBestMove(String fen) {
        baseEngine.setFen(fen);
        
        List<Move> legalMoves = generateLegalMoves();
        if (legalMoves.isEmpty()) {
            return null;
        }
        
        Move bestMove = null;
        int bestValue = Integer.MIN_VALUE;
        boolean isWhite = baseEngine.getActiveColor().equals("w");
        
        // Simple evaluation of each move
        for (Move move : legalMoves) {
            int value = evaluateMove(move, isWhite);
            move.evaluation = value;
            
            if (value > bestValue) {
                bestValue = value;
                bestMove = move;
            }
        }
        
        if (bestMove != null) {
            bestMove.evaluation = bestValue;
        }
        
        return bestMove;
    }
    
    /**
     * Enhanced move evaluation with piece safety
     */
    private int evaluateMove(Move move, boolean isWhite) {
        String uci = move.uci;
        int score = 0;
        
        // CRITICAL: Check if this move hangs a piece (massive penalty)
        if (wouldHangPiece(uci)) {
            String[] board = getBoardArray();
            int fromSquare = algebraicToSquare(uci.substring(0, 2));
            if (fromSquare >= 0) {
                String piece = board[fromSquare];
                if (!piece.isEmpty()) {
                    char pieceChar = Character.toLowerCase(piece.charAt(0));
                    int pieceValue = PIECE_VALUES.getOrDefault(Character.isUpperCase(piece.charAt(0)) ? pieceChar : Character.toUpperCase(pieceChar), 0);
                    score -= Math.abs(pieceValue) * 10; // 10x penalty for hanging pieces
                }
            }
        }
        
        // Capture analysis
        score += evaluateCapture(uci);
        
        // Center control bonus
        if (uci.contains("e4") || uci.contains("e5") || uci.contains("d4") || uci.contains("d5")) {
            score += 50;
        }
        
        // Development bonus for knights and bishops
        if (uci.startsWith("g1") || uci.startsWith("b1") || uci.startsWith("g8") || uci.startsWith("b8")) {
            score += 30;
        }
        if (uci.startsWith("f1") || uci.startsWith("c1") || uci.startsWith("f8") || uci.startsWith("c8")) {
            score += 25;
        }
        
        // Piece safety bonus (moving to safe squares)
        int toSquare = algebraicToSquare(uci.substring(2, 4));
        if (toSquare >= 0) {
            boolean isAttackedByOpponent = isSquareAttacked(toSquare, !isWhite);
            boolean isDefendedByUs = isSquareAttacked(toSquare, isWhite);
            
            if (isAttackedByOpponent && !isDefendedByUs) {
                score -= 100; // Penalty for moving to undefended attacked square
            } else if (!isAttackedByOpponent) {
                score += 10; // Small bonus for safe squares
            }
        }
        
        // Basic positional evaluation
        score += evaluatePosition() / 10; // Reduce weight of positional factors
        
        return isWhite ? score : -score;
    }
    
    /**
     * Evaluate capture moves
     */
    private int evaluateCapture(String uci) {
        int toSquare = algebraicToSquare(uci.substring(2, 4));
        if (toSquare < 0) return 0;
        
        String[] board = getBoardArray();
        String capturedPiece = board[toSquare];
        
        if (capturedPiece.isEmpty()) return 0; // Not a capture
        
        // Get value of captured piece
        char capturedChar = capturedPiece.charAt(0);
        int captureValue = Math.abs(PIECE_VALUES.getOrDefault(capturedChar, 0));
        
        // Get value of capturing piece
        int fromSquare = algebraicToSquare(uci.substring(0, 2));
        if (fromSquare < 0) return captureValue;
        
        String capturingPiece = board[fromSquare];
        if (capturingPiece.isEmpty()) return captureValue;
        
        char capturingChar = capturingPiece.charAt(0);
        int capturingValue = Math.abs(PIECE_VALUES.getOrDefault(capturingChar, 0));
        
        // Evaluate the trade
        int tradeValue = captureValue - capturingValue;
        
        // Bonus for good trades (capturing more valuable pieces)
        if (tradeValue > 0) {
            return captureValue + tradeValue; // Double bonus for profitable captures
        } else if (tradeValue == 0) {
            return captureValue / 2; // Small bonus for equal trades
        } else {
            // Check if the capturing piece would be recaptured
            boolean wouldBeRecaptured = wouldHangPiece(uci);
            if (wouldBeRecaptured) {
                return tradeValue * 2; // Double penalty for bad trades
            } else {
                return captureValue; // Just the capture value if safe
            }
        }
    }
    
    /**
     * Minimax algorithm with alpha-beta pruning
     */
    private int minimax(int depth, int alpha, int beta, boolean isMaximizing) {
        if (depth == 0) {
            return evaluatePosition();
        }
        
        List<Move> moves = generateLegalMoves();
        
        if (moves.isEmpty()) {
            // Checkmate or stalemate
            if (baseEngine.isKingInCheck()) {
                return isMaximizing ? -INFINITY : INFINITY; // Checkmate
            } else {
                return 0; // Stalemate
            }
        }
        
        if (isMaximizing) {
            int maxEval = -INFINITY;
            for (Move move : moves) {
                makeMove(move);
                int eval = minimax(depth - 1, alpha, beta, false);
                undoMove(move);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break; // Alpha-beta pruning
                }
            }
            return maxEval;
        } else {
            int minEval = INFINITY;
            for (Move move : moves) {
                makeMove(move);
                int eval = minimax(depth - 1, alpha, beta, true);
                undoMove(move);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break; // Alpha-beta pruning
                }
            }
            return minEval;
        }
    }
    
    /**
     * Evaluate the current position
     */
    private int evaluatePosition() {
        int score = 0;
        
        // Material evaluation
        score += getMaterialValue();
        
        // Positional evaluation
        score += getPositionalValue();
        
        // Simple mobility bonus
        score += getMobilityBonus();
        
        return score;
    }
    
    private int getMaterialValue() {
        int score = 0;
        String[] board = getBoardArray();
        
        for (String piece : board) {
            if (!piece.isEmpty()) {
                char pieceChar = piece.charAt(0);
                score += PIECE_VALUES.getOrDefault(pieceChar, 0);
            }
        }
        
        return score;
    }
    
    private int getPositionalValue() {
        int score = 0;
        String[] board = getBoardArray();
        
        for (int square = 0; square < 64; square++) {
            String piece = board[square];
            if (piece.isEmpty()) continue;
            
            char pieceChar = piece.charAt(0);
            int rank = square / 8;
            int file = square % 8;
            
            boolean isWhite = Character.isUpperCase(pieceChar);
            int tableRank = isWhite ? rank : 7 - rank;
            
            int positionalBonus = 0;
            switch (Character.toLowerCase(pieceChar)) {
                case 'p':
                    positionalBonus = PAWN_TABLE[tableRank][file];
                    break;
                case 'n':
                    positionalBonus = KNIGHT_TABLE[tableRank][file];
                    break;
                case 'b':
                    positionalBonus = BISHOP_TABLE[tableRank][file];
                    break;
                default:
                    positionalBonus = 0;
            }
            
            score += isWhite ? positionalBonus : -positionalBonus;
        }
        
        return score;
    }
    
    private int getMobilityBonus() {
        boolean isWhite = baseEngine.getActiveColor().equals("w");
        int mobilityCount = generateLegalMoves().size();
        
        // Simple mobility bonus: 2 centipawns per legal move
        return isWhite ? mobilityCount * 2 : -mobilityCount * 2;
    }
    
    /**
     * Generate legal moves (simplified implementation)
     */
    private List<Move> generateLegalMoves() {
        List<Move> moves = new ArrayList<>();
        List<String> uciMoves = baseEngine.getLegalMoves();
        
        for (String uci : uciMoves) {
            moves.add(new Move(uci));
        }
        
        return moves;
    }
    
    /**
     * Apply a move (simplified)
     */
    private void makeMove(Move move) {
        // Store the current state for undo
        move.previousFen = baseEngine.getCurrentFen();
        // Apply the move using the base engine's applyMove functionality
        // This is simplified - in a real engine we'd need proper move application
    }
    
    /**
     * Undo a move (simplified)
     */
    private void undoMove(Move move) {
        // Restore the previous position
        if (move.previousFen != null) {
            baseEngine.setFen(move.previousFen);
        }
    }
    
    private String[] getBoardArray() {
        // Get board representation from base engine
        // Parse the current FEN to get board state
        String fen = baseEngine.getCurrentFen();
        String[] fenParts = fen.split(" ");
        String boardFen = fenParts[0];
        
        String[] board = new String[64];
        java.util.Arrays.fill(board, "");
        
        String[] ranks = boardFen.split("/");
        int square = 0;
        
        for (String rank : ranks) {
            for (char c : rank.toCharArray()) {
                if (Character.isDigit(c)) {
                    int emptySquares = Character.getNumericValue(c);
                    for (int i = 0; i < emptySquares; i++) {
                        board[square++] = "";
                    }
                } else {
                    board[square++] = String.valueOf(c);
                }
            }
        }
        
        return board;
    }
    
    /**
     * Get engine analysis of a position
     */
    public EngineAnalysis analyzePosition(String fen, int depth) {
        baseEngine.setFen(fen);
        
        Move bestMove = findBestMove(fen);
        List<Move> topMoves = getTopMoves(fen, Math.min(5, generateLegalMoves().size()));
        
        EngineAnalysis analysis = new EngineAnalysis();
        analysis.bestMove = bestMove;
        analysis.topMoves = topMoves;
        analysis.evaluation = bestMove != null ? bestMove.evaluation : 0;
        analysis.depth = depth;
        analysis.position = fen;
        
        return analysis;
    }
    
    /**
     * Get top N moves with evaluations
     */
    public List<Move> getTopMoves(String fen, int count) {
        baseEngine.setFen(fen);
        
        List<Move> moves = generateLegalMoves();
        boolean isWhite = baseEngine.getActiveColor().equals("w");
        
        // Evaluate each move
        for (Move move : moves) {
            makeMove(move);
            move.evaluation = evaluatePosition();
            undoMove(move);
        }
        
        // Sort moves by evaluation
        moves.sort((a, b) -> isWhite ? 
            Integer.compare(b.evaluation, a.evaluation) : 
            Integer.compare(a.evaluation, b.evaluation));
        
        return moves.subList(0, Math.min(count, moves.size()));
    }
    
    /**
     * Convert engine evaluation to human-readable format
     */
    public String formatEvaluation(int centipawns) {
        if (Math.abs(centipawns) > 10000) {
            return centipawns > 0 ? "Mate for White" : "Mate for Black";
        }
        
        double pawns = centipawns / 100.0;
        return String.format("%.2f", pawns);
    }
    
    /**
     * Check if a square is attacked by the opponent
     */
    public boolean isSquareAttacked(int square, boolean byWhite) {
        String[] board = getBoardArray();
        
        // Check pawn attacks
        if (isPawnAttacking(square, byWhite, board)) return true;
        
        // Check knight attacks
        if (isKnightAttacking(square, byWhite, board)) return true;
        
        // Check bishop/queen diagonal attacks
        if (isBishopAttacking(square, byWhite, board)) return true;
        
        // Check rook/queen straight attacks
        if (isRookAttacking(square, byWhite, board)) return true;
        
        // Check king attacks
        if (isKingAttacking(square, byWhite, board)) return true;
        
        return false;
    }
    
    private boolean isPawnAttacking(int square, boolean byWhite, String[] board) {
        int rank = square / 8;
        int file = square % 8;
        
        // In my coordinate system: rank 0 = 8th rank, rank 7 = 1st rank
        // White pawns attack from rank+1 (from lower rank numbers to higher)
        // Black pawns attack from rank-1 (from higher rank numbers to lower)
        int pawnRank = byWhite ? rank + 1 : rank - 1;
        
        if (pawnRank < 0 || pawnRank > 7) return false;
        
        // Check diagonal attacks from pawns (pawns attack diagonally)
        for (int df = -1; df <= 1; df += 2) {
            int pawnFile = file + df;
            if (pawnFile >= 0 && pawnFile < 8) {
                int pawnSquare = pawnRank * 8 + pawnFile;
                String piece = board[pawnSquare];
                if (!piece.isEmpty()) {
                    char pieceChar = piece.charAt(0);
                    boolean isPawnWhite = Character.isUpperCase(pieceChar);
                    if (isPawnWhite == byWhite && Character.toLowerCase(pieceChar) == 'p') {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    private boolean isKnightAttacking(int square, boolean byWhite, String[] board) {
        int rank = square / 8;
        int file = square % 8;
        
        int[][] knightMoves = {{-2,-1}, {-2,1}, {-1,-2}, {-1,2}, {1,-2}, {1,2}, {2,-1}, {2,1}};
        
        for (int[] move : knightMoves) {
            int newRank = rank + move[0];
            int newFile = file + move[1];
            
            if (newRank >= 0 && newRank < 8 && newFile >= 0 && newFile < 8) {
                int knightSquare = newRank * 8 + newFile;
                String piece = board[knightSquare];
                if (!piece.isEmpty()) {
                    char pieceChar = piece.charAt(0);
                    boolean isKnightWhite = Character.isUpperCase(pieceChar);
                    if (isKnightWhite == byWhite && Character.toLowerCase(pieceChar) == 'n') {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    private boolean isBishopAttacking(int square, boolean byWhite, String[] board) {
        int[][] directions = {{-1,-1}, {-1,1}, {1,-1}, {1,1}};
        return isSlidingPieceAttacking(square, byWhite, board, directions, "bq");
    }
    
    private boolean isRookAttacking(int square, boolean byWhite, String[] board) {
        int[][] directions = {{-1,0}, {1,0}, {0,-1}, {0,1}};
        return isSlidingPieceAttacking(square, byWhite, board, directions, "rq");
    }
    
    private boolean isSlidingPieceAttacking(int square, boolean byWhite, String[] board, int[][] directions, String pieces) {
        int rank = square / 8;
        int file = square % 8;
        
        for (int[] dir : directions) {
            int newRank = rank + dir[0];
            int newFile = file + dir[1];
            
            while (newRank >= 0 && newRank < 8 && newFile >= 0 && newFile < 8) {
                int newSquare = newRank * 8 + newFile;
                String piece = board[newSquare];
                
                if (!piece.isEmpty()) {
                    char pieceChar = piece.charAt(0);
                    boolean isPieceWhite = Character.isUpperCase(pieceChar);
                    if (isPieceWhite == byWhite && pieces.contains(String.valueOf(Character.toLowerCase(pieceChar)))) {
                        return true;
                    }
                    break; // Piece blocks further attacks in this direction
                }
                
                newRank += dir[0];
                newFile += dir[1];
            }
        }
        
        return false;
    }
    
    private boolean isKingAttacking(int square, boolean byWhite, String[] board) {
        int rank = square / 8;
        int file = square % 8;
        
        for (int dr = -1; dr <= 1; dr++) {
            for (int df = -1; df <= 1; df++) {
                if (dr == 0 && df == 0) continue;
                
                int newRank = rank + dr;
                int newFile = file + df;
                
                if (newRank >= 0 && newRank < 8 && newFile >= 0 && newFile < 8) {
                    int kingSquare = newRank * 8 + newFile;
                    String piece = board[kingSquare];
                    if (!piece.isEmpty()) {
                        char pieceChar = piece.charAt(0);
                        boolean isKingWhite = Character.isUpperCase(pieceChar);
                        if (isKingWhite == byWhite && Character.toLowerCase(pieceChar) == 'k') {
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if a piece would be hanging after a move
     */
    public boolean wouldHangPiece(String moveUci) {
        if (moveUci.length() != 4) return false;
        
        // Parse move
        int fromSquare = algebraicToSquare(moveUci.substring(0, 2));
        int toSquare = algebraicToSquare(moveUci.substring(2, 4));
        
        if (fromSquare < 0 || toSquare < 0) return false;
        
        String[] board = getBoardArray();
        String movingPiece = board[fromSquare];
        
        if (movingPiece.isEmpty()) return false;
        
        boolean isWhite = Character.isUpperCase(movingPiece.charAt(0));
        
        // Temporarily make the move
        String capturedPiece = board[toSquare];
        board[toSquare] = movingPiece;
        board[fromSquare] = "";
        
        // Check if the piece would be attacked and not defended
        boolean isAttacked = isSquareAttackedByBoard(toSquare, !isWhite, board);
        boolean isDefended = isSquareAttackedByBoard(toSquare, isWhite, board);
        
        // Restore the board
        board[fromSquare] = movingPiece;
        board[toSquare] = capturedPiece;
        
        return isAttacked && !isDefended;
    }
    
    private boolean isSquareAttackedByBoard(int square, boolean byWhite, String[] board) {
        // Similar to isSquareAttacked but uses provided board state
        if (isPawnAttacking(square, byWhite, board)) return true;
        if (isKnightAttacking(square, byWhite, board)) return true;
        if (isBishopAttacking(square, byWhite, board)) return true;
        if (isRookAttacking(square, byWhite, board)) return true;
        if (isKingAttacking(square, byWhite, board)) return true;
        return false;
    }
    
    private int algebraicToSquare(String algebraic) {
        if (algebraic.length() != 2) return -1;
        
        char file = algebraic.charAt(0);
        char rank = algebraic.charAt(1);
        
        if (file < 'a' || file > 'h' || rank < '1' || rank > '8') return -1;
        
        int fileNum = file - 'a';
        int rankNum = rank - '1';
        
        return (7 - rankNum) * 8 + fileNum;
    }
    
    // Inner classes
    public static class Move {
        public String uci;
        public int evaluation;
        public String algebraic;
        public String previousFen; // For undo functionality
        
        public Move(String uci) {
            this.uci = uci;
            this.evaluation = 0;
            this.algebraic = uci; // Simplified - should convert to algebraic notation
            this.previousFen = null;
        }
        
        @Override
        public String toString() {
            return String.format("%s (eval: %d)", uci, evaluation);
        }
    }
    
    public static class EngineAnalysis {
        public Move bestMove;
        public List<Move> topMoves;
        public int evaluation;
        public int depth;
        public String position;
        
        public String toFormattedString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Engine Analysis:\n");
            sb.append("Position: ").append(position).append("\n");
            sb.append("Depth: ").append(depth).append("\n");
            sb.append("Evaluation: ").append(evaluation).append(" centipawns\n");
            
            if (bestMove != null) {
                sb.append("Best Move: ").append(bestMove.uci).append(" (").append(bestMove.evaluation).append(")\n");
            }
            
            sb.append("Top Moves:\n");
            for (int i = 0; i < topMoves.size(); i++) {
                Move move = topMoves.get(i);
                sb.append(String.format("%d. %s (eval: %d)\n", 
                    i + 1, move.uci, move.evaluation));
            }
            
            return sb.toString();
        }
    }
}