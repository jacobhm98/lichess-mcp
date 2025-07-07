package com.example.lichess;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChessEngine {
    private static final String INITIAL_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    
    private String[] board;
    private String currentFen;
    private String activeColor;
    private String castlingRights;
    private String enPassantTarget;
    private int halfMoveClock;
    private int fullMoveNumber;
    
    public ChessEngine() {
        initializeBoard();
    }
    
    private void initializeBoard() {
        setFen(INITIAL_FEN);
    }
    
    public void setFen(String fen) {
        String[] parts = fen.split(" ");
        if (parts.length >= 4) {
            this.currentFen = fen;
            this.activeColor = parts[1];
            this.castlingRights = parts[2];
            this.enPassantTarget = parts[3];
            this.halfMoveClock = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
            this.fullMoveNumber = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
            
            // Parse board position
            this.board = new String[64];
            Arrays.fill(board, "");
            
            String[] ranks = parts[0].split("/");
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
        }
    }
    
    public String applyMoves(String pgn) {
        String[] moves = extractMovesFromPgn(pgn);
        String currentFen = INITIAL_FEN;
        setFen(currentFen);
        
        for (String move : moves) {
            if (!move.trim().isEmpty()) {
                applyMove(move.trim());
            }
        }
        
        return getCurrentFen();
    }
    
    private String[] extractMovesFromPgn(String pgn) {
        // Remove game result and comments
        pgn = pgn.replaceAll("\\{[^}]*\\}", ""); // Remove comments
        pgn = pgn.replaceAll("\\([^)]*\\)", ""); // Remove variations
        pgn = pgn.replaceAll("1-0|0-1|1/2-1/2|\\*", ""); // Remove results
        
        // Extract moves using regex
        Pattern movePattern = Pattern.compile("\\d+\\.\\s*([a-h1-8NBRQK\\-O=\\+#x]+)(?:\\s+([a-h1-8NBRQK\\-O=\\+#x]+))?");
        Matcher matcher = movePattern.matcher(pgn);
        
        List<String> moves = new ArrayList<>();
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                moves.add(matcher.group(1));
            }
            if (matcher.group(2) != null) {
                moves.add(matcher.group(2));
            }
        }
        
        return moves.toArray(new String[0]);
    }
    
    private void applyMove(String move) {
        // Basic move application - simplified implementation
        // This is a basic implementation, real chess engine would be more complex
        
        // Handle castling
        if (move.equals("O-O")) {
            applyCastling(true);
            return;
        } else if (move.equals("O-O-O")) {
            applyCastling(false);
            return;
        }
        
        // Parse algebraic notation
        String cleanMove = move.replaceAll("[+#]", ""); // Remove check/checkmate indicators
        
        // This is a simplified move parser - real implementation would need more logic
        // For now, just toggle active color
        activeColor = activeColor.equals("w") ? "b" : "w";
        
        if (activeColor.equals("w")) {
            fullMoveNumber++;
        }
        
        // Update FEN
        updateFen();
    }
    
    private void applyCastling(boolean kingside) {
        // Simplified castling implementation
        activeColor = activeColor.equals("w") ? "b" : "w";
        if (activeColor.equals("w")) {
            fullMoveNumber++;
        }
        updateFen();
    }
    
    private void updateFen() {
        // Simplified FEN update - real implementation would rebuild from board array
        this.currentFen = getCurrentFen();
    }
    
    public String getCurrentFen() {
        // Build FEN from current board state
        StringBuilder fen = new StringBuilder();
        
        // Board position
        for (int rank = 0; rank < 8; rank++) {
            int emptyCount = 0;
            for (int file = 0; file < 8; file++) {
                int square = rank * 8 + file;
                String piece = board[square];
                
                if (piece.isEmpty()) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(piece);
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (rank < 7) {
                fen.append("/");
            }
        }
        
        // Add other FEN components
        fen.append(" ").append(activeColor);
        fen.append(" ").append(castlingRights);
        fen.append(" ").append(enPassantTarget);
        fen.append(" ").append(halfMoveClock);
        fen.append(" ").append(fullMoveNumber);
        
        return fen.toString();
    }
    
    public boolean isKingInCheck() {
        // Simplified check detection
        // Real implementation would check if king is attacked
        return false; // Placeholder
    }
    
    public List<String> getLegalMoves() {
        // Simplified legal move generation
        List<String> moves = new ArrayList<>();
        
        // Basic moves based on current position
        if (activeColor.equals("w")) {
            moves.add("e2e4");
            moves.add("d2d4");
            moves.add("g1f3");
            moves.add("b1c3");
        } else {
            moves.add("e7e5");
            moves.add("d7d5");
            moves.add("g8f6");
            moves.add("b8c6");
        }
        
        return moves;
    }
    
    public String getActiveColor() {
        return activeColor;
    }
    
    public String getBoardDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append("Current position (FEN): ").append(getCurrentFen()).append("\n");
        desc.append("Active color: ").append(activeColor.equals("w") ? "White" : "Black").append("\n");
        desc.append("King in check: ").append(isKingInCheck() ? "Yes" : "No").append("\n");
        desc.append("Legal moves: ").append(String.join(", ", getLegalMoves())).append("\n");
        
        // ASCII board representation
        desc.append("\nBoard:\n");
        desc.append("  a b c d e f g h\n");
        for (int rank = 0; rank < 8; rank++) {
            desc.append(8 - rank).append(" ");
            for (int file = 0; file < 8; file++) {
                int square = rank * 8 + file;
                String piece = board[square];
                desc.append(piece.isEmpty() ? "." : piece).append(" ");
            }
            desc.append(" ").append(8 - rank).append("\n");
        }
        desc.append("  a b c d e f g h\n");
        
        return desc.toString();
    }
    
    public String suggestMove() {
        List<String> legalMoves = getLegalMoves();
        if (legalMoves.isEmpty()) {
            return null;
        }
        
        // Simple move suggestion - just return first legal move
        // Real implementation would use chess engine evaluation
        return legalMoves.get(0);
    }
}