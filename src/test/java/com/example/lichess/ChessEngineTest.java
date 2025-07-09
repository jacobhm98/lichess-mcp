package com.example.lichess;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class ChessEngineTest {
    
    private ChessEngine chessEngine;
    private EnhancedChessEngine enhancedEngine;
    
    @Before
    public void setUp() {
        chessEngine = new ChessEngine();
        enhancedEngine = new EnhancedChessEngine(chessEngine);
    }
    
    @Test
    public void testStartingPositionFEN() {
        String startingFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        chessEngine.setFen(startingFen);
        
        assertEquals("White should be active in starting position", "w", chessEngine.getActiveColor());
        
        List<String> legalMoves = chessEngine.getLegalMoves();
        assertFalse("Starting position should have legal moves", legalMoves.isEmpty());
        assertTrue("Should have pawn moves", legalMoves.contains("e2e4"));
        assertTrue("Should have knight moves", legalMoves.contains("g1f3"));
    }
    
    @Test
    public void testQueenHangingDetection() {
        // Test the position where queen hangs on c5
        String testFen = "rnbqkb1r/pp1p1ppp/2pp1n2/8/3Q4/8/PPP2PPP/RNB1KBNR w KQkq - 2 6";
        chessEngine.setFen(testFen);
        enhancedEngine = new EnhancedChessEngine(chessEngine);
        
        // The move Qc5 should be detected as hanging the queen
        boolean queenHangs = enhancedEngine.wouldHangPiece("d4c5");
        assertTrue("Queen moving to c5 should be detected as hanging", queenHangs);
    }
    
    @Test
    public void testPieceAttackDetection() {
        // Set up a position where d6 pawn can capture on c5
        String testFen = "rnbqkb1r/pp1p1ppp/2pp1n2/2Q5/8/8/PPP2PPP/RNB1KBNR b KQkq - 3 6";
        chessEngine.setFen(testFen);
        enhancedEngine = new EnhancedChessEngine(chessEngine);
        
        // Calculate c5 square correctly
        // c5 = (7-4)*8 + 2 = 3*8 + 2 = 26, not 34
        int c5Square = (7-4)*8 + 2; // c5 = rank 3, file 2 
        boolean isAttackedByBlack = enhancedEngine.isSquareAttacked(c5Square, false);
        assertTrue("c5 should be attacked by black pawn on d6", isAttackedByBlack);
        
        // Debug: also check if d6 contains a black pawn
        int d6Square = (7-5)*8 + 3; // d6 = rank 2, file 3
        // This is just for verification in the test
    }
    
    @Test
    public void testBestMoveAvoidHangingPieces() {
        // Position where queen can hang
        String testFen = "rnbqkb1r/pp1p1ppp/2pp1n2/8/3Q4/8/PPP2PPP/RNB1KBNR w KQkq - 2 6";
        
        EnhancedChessEngine.Move bestMove = enhancedEngine.findBestMove(testFen);
        assertNotNull("Engine should find a best move", bestMove);
        
        // The best move should NOT be Qc5 (hanging the queen)
        assertNotEquals("Engine should not suggest hanging the queen", "d4c5", bestMove.uci);
    }
    
    @Test
    public void testFENParsingConsistency() {
        String[] testPositions = {
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", // Starting position
            "rnbqkb1r/pppppppp/5n2/8/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 1 2", // After 1.d4 Nf6
            "rnbqkb1r/pp1ppppp/2p2n2/3P4/8/8/PPP1PPPP/RNBQKBNR w KQkq - 0 3", // After 1.d4 Nf6 2.d5 c6
        };
        
        for (String fen : testPositions) {
            chessEngine.setFen(fen);
            String reconstructedFen = chessEngine.getCurrentFen();
            
            // Parse the main components (ignore halfmove/fullmove clocks which might differ)
            String[] originalParts = fen.split(" ");
            String[] reconstructedParts = reconstructedFen.split(" ");
            
            assertEquals("Board position should match", originalParts[0], reconstructedParts[0]);
            assertEquals("Active color should match", originalParts[1], reconstructedParts[1]);
            assertEquals("Castling rights should match", originalParts[2], reconstructedParts[2]);
            assertEquals("En passant target should match", originalParts[3], reconstructedParts[3]);
        }
    }
    
    @Test
    public void testLegalMoveGeneration() {
        // Test a specific position with known legal moves
        String testFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        chessEngine.setFen(testFen);
        
        List<String> legalMoves = chessEngine.getLegalMoves();
        
        assertFalse("Should have legal moves", legalMoves.isEmpty());
        assertTrue("Should contain e7e5", legalMoves.contains("e7e5"));
        assertTrue("Should contain g8f6", legalMoves.contains("g8f6"));
        assertTrue("Should contain d7d5", legalMoves.contains("d7d5"));
        
        // Should not contain moves from squares with no pieces
        assertFalse("Should not contain moves from empty squares", 
                   legalMoves.stream().anyMatch(move -> move.startsWith("e4"))); // e4 is empty
    }
    
    @Test
    public void testCaptureEvaluation() {
        // Position where capturing is beneficial
        String testFen = "rnbqkb1r/pp1ppppp/5n2/2p5/3P4/8/PPP1PPPP/RNBQKBNR w KQkq c6 0 3";
        chessEngine.setFen(testFen);
        enhancedEngine = new EnhancedChessEngine(chessEngine);
        
        // Test that capturing the pawn is evaluated positively
        List<String> legalMoves = chessEngine.getLegalMoves();
        assertTrue("Should be able to capture on c5", legalMoves.contains("d4c5"));
        
        EnhancedChessEngine.Move bestMove = enhancedEngine.findBestMove(testFen);
        
        // The best move might be capturing the pawn (or at least it should be considered good)
        assertTrue("Engine should consider capturing favorably", bestMove.evaluation > -200);
    }
    
    @Test
    public void testEngineDoesNotHangMajorPieces() {
        // Test multiple positions to ensure engine doesn't hang queens, rooks, etc.
        String[] testPositions = {
            "rnbqkb1r/pp1p1ppp/2pp1n2/8/3Q4/8/PPP2PPP/RNB1KBNR w KQkq - 2 6", // Queen can hang
            "rnbqkbnr/pp1ppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", // Starting position
        };
        
        for (String fen : testPositions) {
            EnhancedChessEngine.Move bestMove = enhancedEngine.findBestMove(fen);
            assertNotNull("Engine should find a move", bestMove);
            
            // Check that the suggested move doesn't hang a major piece
            boolean wouldHang = enhancedEngine.wouldHangPiece(bestMove.uci);
            assertFalse("Engine should not suggest moves that hang pieces in position: " + fen, wouldHang);
        }
    }
    
    @Test
    public void testSquareAttackDetectionAccuracy() {
        // Test specific attack patterns
        String testFen = "8/8/8/3p4/2Q5/8/8/8 w - - 0 1"; // Queen vs pawn
        chessEngine.setFen(testFen);
        enhancedEngine = new EnhancedChessEngine(chessEngine);
        
        // Queen on c4 should attack many squares including d5
        // d5 = (7-4)*8 + 3 = 3*8 + 3 = 27 ✓
        int d5Square = (7-4)*8 + 3; // d5
        boolean isAttackedByWhite = enhancedEngine.isSquareAttacked(d5Square, true);
        assertTrue("d5 should be attacked by white queen on c4", isAttackedByWhite);
        
        // c4 = (7-3)*8 + 2 = 4*8 + 2 = 34
        int c4Square = (7-3)*8 + 2; // c4 
        boolean isAttackedByBlack = enhancedEngine.isSquareAttacked(c4Square, false);
        assertTrue("c4 should be attacked by black pawn on d5", isAttackedByBlack);
    }
}