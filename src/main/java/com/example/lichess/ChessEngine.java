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
        
        // Simple move parsing for common moves
        if (cleanMove.length() >= 2) {
            // Try to parse basic moves like e4, Nf3, etc.
            char piece = cleanMove.charAt(0);
            String destination = cleanMove.substring(cleanMove.length() - 2);
            
            // Apply basic move logic
            applyBasicMove(piece, destination);
        }
        
        // Toggle active color
        activeColor = activeColor.equals("w") ? "b" : "w";
        
        if (activeColor.equals("w")) {
            fullMoveNumber++;
        }
        
        // Update FEN
        updateFen();
    }
    
    private void applyBasicMove(char piece, String destination) {
        // Basic move application to update board array
        int destSquare = algebraicToSquare(destination);
        if (destSquare >= 0 && destSquare < 64) {
            // Clear the destination square and place the piece
            if (Character.isLowerCase(piece)) {
                // Pawn move
                board[destSquare] = activeColor.equals("w") ? "P" : "p";
            } else {
                // Piece move
                board[destSquare] = activeColor.equals("w") ? String.valueOf(piece) : String.valueOf(Character.toLowerCase(piece));
            }
        }
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
        // Generate legal moves based on current position
        List<String> moves = new ArrayList<>();
        
        // Generate moves for each piece on the board
        for (int square = 0; square < 64; square++) {
            String piece = board[square];
            if (piece.isEmpty()) continue;
            
            boolean isPieceWhite = Character.isUpperCase(piece.charAt(0));
            boolean isCurrentPlayerTurn = (activeColor.equals("w") && isPieceWhite) || 
                                        (activeColor.equals("b") && !isPieceWhite);
            
            if (!isCurrentPlayerTurn) continue;
            
            // Generate moves for this piece
            List<String> pieceMoves = generateMovesForPiece(square, piece.charAt(0));
            moves.addAll(pieceMoves);
        }
        
        return moves;
    }
    
    private List<String> generateMovesForPiece(int square, char piece) {
        List<String> moves = new ArrayList<>();
        String fromSquare = squareToAlgebraic(square);
        
        switch (Character.toLowerCase(piece)) {
            case 'p':
                moves.addAll(generatePawnMoves(square, Character.isUpperCase(piece)));
                break;
            case 'n':
                moves.addAll(generateKnightMoves(square));
                break;
            case 'b':
                moves.addAll(generateBishopMoves(square));
                break;
            case 'r':
                moves.addAll(generateRookMoves(square));
                break;
            case 'q':
                moves.addAll(generateQueenMoves(square));
                break;
            case 'k':
                moves.addAll(generateKingMoves(square));
                break;
        }
        
        return moves;
    }
    
    private List<String> generatePawnMoves(int square, boolean isWhite) {
        List<String> moves = new ArrayList<>();
        int rank = square / 8;
        int file = square % 8;
        
        int direction = isWhite ? -1 : 1;
        int startRank = isWhite ? 6 : 1;
        
        // Forward move
        int newSquare = square + direction * 8;
        if (newSquare >= 0 && newSquare < 64 && board[newSquare].isEmpty()) {
            moves.add(squareToAlgebraic(square) + squareToAlgebraic(newSquare));
            
            // Double move from starting position
            if (rank == startRank) {
                newSquare = square + direction * 16;
                if (newSquare >= 0 && newSquare < 64 && board[newSquare].isEmpty()) {
                    moves.add(squareToAlgebraic(square) + squareToAlgebraic(newSquare));
                }
            }
        }
        
        // Captures
        for (int df = -1; df <= 1; df += 2) {
            if (file + df >= 0 && file + df < 8) {
                newSquare = square + direction * 8 + df;
                if (newSquare >= 0 && newSquare < 64 && !board[newSquare].isEmpty()) {
                    boolean targetIsWhite = Character.isUpperCase(board[newSquare].charAt(0));
                    if (targetIsWhite != isWhite) {
                        moves.add(squareToAlgebraic(square) + squareToAlgebraic(newSquare));
                    }
                }
            }
        }
        
        return moves;
    }
    
    private List<String> generateKnightMoves(int square) {
        List<String> moves = new ArrayList<>();
        int rank = square / 8;
        int file = square % 8;
        
        int[][] knightMoves = {{-2,-1}, {-2,1}, {-1,-2}, {-1,2}, {1,-2}, {1,2}, {2,-1}, {2,1}};
        
        for (int[] move : knightMoves) {
            int newRank = rank + move[0];
            int newFile = file + move[1];
            
            if (newRank >= 0 && newRank < 8 && newFile >= 0 && newFile < 8) {
                int newSquare = newRank * 8 + newFile;
                if (canMoveTo(square, newSquare)) {
                    moves.add(squareToAlgebraic(square) + squareToAlgebraic(newSquare));
                }
            }
        }
        
        return moves;
    }
    
    private List<String> generateBishopMoves(int square) {
        List<String> moves = new ArrayList<>();
        int[][] directions = {{-1,-1}, {-1,1}, {1,-1}, {1,1}};
        
        for (int[] dir : directions) {
            moves.addAll(generateSlidingMoves(square, dir[0], dir[1]));
        }
        
        return moves;
    }
    
    private List<String> generateRookMoves(int square) {
        List<String> moves = new ArrayList<>();
        int[][] directions = {{-1,0}, {1,0}, {0,-1}, {0,1}};
        
        for (int[] dir : directions) {
            moves.addAll(generateSlidingMoves(square, dir[0], dir[1]));
        }
        
        return moves;
    }
    
    private List<String> generateQueenMoves(int square) {
        List<String> moves = new ArrayList<>();
        int[][] directions = {{-1,-1}, {-1,0}, {-1,1}, {0,-1}, {0,1}, {1,-1}, {1,0}, {1,1}};
        
        for (int[] dir : directions) {
            moves.addAll(generateSlidingMoves(square, dir[0], dir[1]));
        }
        
        return moves;
    }
    
    private List<String> generateKingMoves(int square) {
        List<String> moves = new ArrayList<>();
        int rank = square / 8;
        int file = square % 8;
        
        for (int dr = -1; dr <= 1; dr++) {
            for (int df = -1; df <= 1; df++) {
                if (dr == 0 && df == 0) continue;
                
                int newRank = rank + dr;
                int newFile = file + df;
                
                if (newRank >= 0 && newRank < 8 && newFile >= 0 && newFile < 8) {
                    int newSquare = newRank * 8 + newFile;
                    if (canMoveTo(square, newSquare)) {
                        moves.add(squareToAlgebraic(square) + squareToAlgebraic(newSquare));
                    }
                }
            }
        }
        
        return moves;
    }
    
    private List<String> generateSlidingMoves(int square, int rankDir, int fileDir) {
        List<String> moves = new ArrayList<>();
        int rank = square / 8;
        int file = square % 8;
        
        int newRank = rank + rankDir;
        int newFile = file + fileDir;
        
        while (newRank >= 0 && newRank < 8 && newFile >= 0 && newFile < 8) {
            int newSquare = newRank * 8 + newFile;
            
            if (board[newSquare].isEmpty()) {
                moves.add(squareToAlgebraic(square) + squareToAlgebraic(newSquare));
            } else {
                // Can capture if it's opponent's piece
                if (canMoveTo(square, newSquare)) {
                    moves.add(squareToAlgebraic(square) + squareToAlgebraic(newSquare));
                }
                break; // Can't move further
            }
            
            newRank += rankDir;
            newFile += fileDir;
        }
        
        return moves;
    }
    
    private boolean canMoveTo(int fromSquare, int toSquare) {
        if (board[toSquare].isEmpty()) {
            return true;
        }
        
        boolean fromIsWhite = Character.isUpperCase(board[fromSquare].charAt(0));
        boolean toIsWhite = Character.isUpperCase(board[toSquare].charAt(0));
        
        return fromIsWhite != toIsWhite; // Can capture opponent's piece
    }
    
    private String squareToAlgebraic(int square) {
        int rank = square / 8;
        int file = square % 8;
        return String.valueOf((char)('a' + file)) + String.valueOf((char)('1' + (7 - rank)));
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