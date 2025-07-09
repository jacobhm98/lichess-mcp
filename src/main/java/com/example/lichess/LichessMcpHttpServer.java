package com.example.lichess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.IO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class LichessMcpHttpServer {
    private static final Logger logger = LoggerFactory.getLogger(LichessMcpHttpServer.class);
    private final ObjectMapper objectMapper;
    private final ChessEngine chessEngine;
    private final EnhancedChessEngine enhancedEngine;
    private final int port;

    public LichessMcpHttpServer(int port) {
        this.objectMapper = new ObjectMapper();
        this.chessEngine = new ChessEngine();
        this.enhancedEngine = new EnhancedChessEngine(chessEngine);
        this.port = port;
    }

    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid port number, using default 8080");
            }
        }
        
        LichessMcpHttpServer server = new LichessMcpHttpServer(port);
        server.start();
    }

    public void start() {
        Server server = new Server(port);
        server.setHandler(new McpHandler());

        try {
            server.start();
            logger.info("Lichess MCP HTTP Server started on port {}", port);
            logger.info("MCP endpoint: http://localhost:{}/mcp", port);
            logger.info("Health check: http://localhost:{}/health", port);
            server.join();
        } catch (Exception e) {
            logger.error("Failed to start HTTP server", e);
            System.exit(1);
        }
    }

    private class McpHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, 
                          HttpServletResponse response) throws IOException, ServletException {
            
            baseRequest.setHandled(true);
            
            // CORS headers for web clients
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
            
            if ("OPTIONS".equals(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_OK);
                return;
            }
            
            try {
                switch (target) {
                    case "/mcp":
                        if ("POST".equals(request.getMethod())) {
                            handleMcpRequest(request, response);
                        } else if ("DELETE".equals(request.getMethod())) {
                            handleSessionTermination(request, response);
                        } else {
                            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                        }
                        break;
                    case "/health":
                        handleHealthCheck(request, response);
                        break;
                    default:
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        response.getWriter().write("Not found");
                }
            } catch (Exception e) {
                logger.error("Error handling request", e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("Internal server error");
            }
        }

        private void handleMcpRequest(HttpServletRequest request, HttpServletResponse response) 
                throws IOException {
            
            // Extract Lichess API token from Authorization header
            String authHeader = request.getHeader("Authorization");
            String lichessToken = null;
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                lichessToken = authHeader.substring(7);
            }
            
            if (lichessToken == null || lichessToken.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("jsonrpc", "2.0");
                errorResponse.putNull("id");
                
                ObjectNode error = objectMapper.createObjectNode();
                error.put("code", -32001);
                error.put("message", "Missing or invalid Authorization header. Please provide: Authorization: Bearer <lichess_token>");
                errorResponse.set("error", error);
                
                response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                return;
            }
            
            // Read JSON-RPC request body
            String requestBody = IO.toString(request.getReader());
            
            try {
                JsonNode jsonRequest = objectMapper.readTree(requestBody);
                String mcpResponse = processRequest(jsonRequest, lichessToken);
                
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("application/json");
                response.getWriter().write(mcpResponse);
                
            } catch (Exception e) {
                logger.error("Error processing MCP request", e);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Invalid JSON-RPC request");
            }
        }
        
        private void handleSessionTermination(HttpServletRequest request, HttpServletResponse response) 
                throws IOException {
            // Handle session termination (DELETE /mcp)
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"session_terminated\"}");
        }
        
        private void handleHealthCheck(HttpServletRequest request, HttpServletResponse response) 
                throws IOException {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"healthy\",\"service\":\"lichess-mcp-server\"}");
        }
    }

    private String processRequest(JsonNode request, String lichessToken) throws Exception {
        String method = request.get("method").asText();
        JsonNode params = request.get("params");
        JsonNode id = request.get("id");

        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");

        if (id != null && !id.isNull()) {
            if (id.isNumber()) {
                response.put("id", id.asInt());
            } else {
                response.put("id", id.asText());
            }
        }

        // Create LichessApiClient with the provided token
        LichessApiClient lichessClient = new LichessApiClient(lichessToken);

        switch (method) {
            case "initialize":
                handleInitialize(response, params);
                break;
            case "tools/list":
                handleToolsList(response);
                break;
            case "tools/call":
                handleToolCall(response, params, lichessClient);
                break;
            default:
                handleError(response, -32601, "Method not found", null);
        }

        return objectMapper.writeValueAsString(response);
    }

    private void handleInitialize(ObjectNode response, JsonNode params) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");

        ObjectNode capabilities = objectMapper.createObjectNode();
        capabilities.put("tools", true);
        result.set("capabilities", capabilities);

        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", "lichess-mcp-server");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);

        response.set("result", result);
    }

    private void handleToolsList(ObjectNode response) {
        ArrayNode tools = objectMapper.createArrayNode();

        // Add all the same tools as the original server
        addGetUserProfileTool(tools);
        addGetGameTool(tools);
        addCreateGameTool(tools);
        addPlayMoveTool(tools);
        addGetGameStateTool(tools);
        addAcceptChallengeTool(tools);
        addAnalyzeGameTool(tools);
        addMakeLlmMoveTool(tools);
        addGetMoveHistoryTool(tools);
        addGetBoardPositionTool(tools);
        addGetLegalMovesTool(tools);
        addWatchGameTool(tools);
        addPollForMyTurnTool(tools);
        
        // Engine analysis tools
        addGetBestMoveTool(tools);
        addAnalyzePositionTool(tools);
        addGetTopMovesTool(tools);

        ObjectNode result = objectMapper.createObjectNode();
        result.set("tools", tools);
        response.set("result", result);
    }

    private void handleToolCall(ObjectNode response, JsonNode params, LichessApiClient lichessClient) {
        String toolName = params.get("name").asText();
        JsonNode arguments = params.get("arguments");

        try {
            switch (toolName) {
                case "get_user_profile":
                    handleGetUserProfile(response, arguments, lichessClient);
                    break;
                case "get_game":
                    handleGetGame(response, arguments, lichessClient);
                    break;
                case "create_game":
                    handleCreateGame(response, arguments, lichessClient);
                    break;
                case "play_move":
                    handlePlayMove(response, arguments, lichessClient);
                    break;
                case "get_game_state":
                    handleGetGameState(response, arguments, lichessClient);
                    break;
                case "accept_challenge":
                    handleAcceptChallenge(response, arguments, lichessClient);
                    break;
                case "analyze_game":
                    handleAnalyzeGame(response, arguments, lichessClient);
                    break;
                case "make_llm_move":
                    handleMakeLlmMove(response, arguments, lichessClient);
                    break;
                case "get_move_history":
                    handleGetMoveHistory(response, arguments, lichessClient);
                    break;
                case "get_board_position":
                    handleGetBoardPosition(response, arguments, lichessClient);
                    break;
                case "get_legal_moves":
                    handleGetLegalMoves(response, arguments, lichessClient);
                    break;
                case "watch_game":
                    handleWatchGame(response, arguments, lichessClient);
                    break;
                case "poll_for_my_turn":
                    handlePollForMyTurn(response, arguments, lichessClient);
                    break;
                case "get_best_move":
                    handleGetBestMove(response, arguments, lichessClient);
                    break;
                case "analyze_position":
                    handleAnalyzePosition(response, arguments, lichessClient);
                    break;
                case "get_top_moves":
                    handleGetTopMoves(response, arguments, lichessClient);
                    break;
                default:
                    handleError(response, -32602, "Unknown tool", null);
            }
        } catch (Exception e) {
            logger.error("Error calling tool: " + toolName, e);
            handleError(response, -32603, "Tool execution error", e.getMessage());
        }
    }

    // Tool handler methods (same logic as original server)
    private void handleGetUserProfile(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String username = arguments.get("username").asText();
        String profileData = lichessClient.getUserProfile(username);

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", "User profile for " + username + ":\n" + profileData);
        content.add(textContent);

        result.set("content", content);
        response.set("result", result);
    }

    private void handleGetGame(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String gameId = arguments.get("gameId").asText();
        String gameData = lichessClient.getGame(gameId);

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", "Game information for " + gameId + ":\n" + gameData);
        content.add(textContent);

        result.set("content", content);
        response.set("result", result);
    }

    private void handleCreateGame(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String player1 = arguments.get("player1").asText();
        String player2 = arguments.get("player2").asText();
        String timeControl = arguments.has("timeControl") ? arguments.get("timeControl").asText() : "10+0";
        String variant = arguments.has("variant") ? arguments.get("variant").asText() : "standard";
        
        String gameResponse = lichessClient.createGame(player1, player2, timeControl, variant);
        
        JsonNode gameJson = objectMapper.readTree(gameResponse);
        String gameUrl = gameJson.has("url") ? gameJson.get("url").asText() : 
                        "https://lichess.org/" + gameJson.get("id").asText();
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", String.format("Game created successfully!\n\n" +
            "Players: %s vs %s\n" +
            "Time Control: %s\n" +
            "Variant: %s\n" +
            "Game URL: %s\n\n" +
            "Share this URL with both players to start the game.", 
            player1, player2, timeControl, variant, gameUrl));
        content.add(textContent);

        result.set("content", content);
        response.set("result", result);
    }

    private void handlePlayMove(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String gameId = arguments.get("gameId").asText();
        String move = arguments.get("move").asText();
        
        String moveResponse = lichessClient.makeMove(gameId, move);
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", String.format("Move %s played successfully in game %s!\n\nResponse: %s", 
            move, gameId, moveResponse));
        content.add(textContent);

        result.set("content", content);
        response.set("result", result);
    }

    private void handleGetGameState(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String gameId = arguments.get("gameId").asText();
        
        String gameState = lichessClient.getBotGameState(gameId);
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", String.format("Current game state for %s:\n\n%s", gameId, gameState));
        content.add(textContent);

        result.set("content", content);
        response.set("result", result);
    }

    private void handleAcceptChallenge(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String challengeId = arguments.get("challengeId").asText();
        
        String acceptResponse = lichessClient.acceptChallenge(challengeId);
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", String.format("Challenge %s accepted successfully!\n\n" +
            "You can now play moves using the play_move tool.\n" +
            "Use get_game_state to see the current position.\n\n" +
            "Response: %s", challengeId, acceptResponse));
        content.add(textContent);

        result.set("content", content);
        response.set("result", result);
    }

    private void handleAnalyzeGame(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String gameId = arguments.get("gameId").asText();
        
        String pgn = lichessClient.getGamePgn(gameId);
        String finalFen = chessEngine.applyMoves(pgn);
        String boardDescription = chessEngine.getBoardDescription();
        boolean isCheck = chessEngine.isKingInCheck();
        String activeColor = chessEngine.getActiveColor();
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", String.format("Game Analysis for %s:\n\n" +
            "PGN:\n%s\n\n" +
            "Current Position:\n%s\n\n" +
            "Active Player: %s\n" +
            "King in Check: %s\n\n" +
            "Board Description:\n%s", 
            gameId, pgn, finalFen, 
            activeColor.equals("w") ? "White" : "Black",
            isCheck ? "Yes" : "No",
            boardDescription));
        content.add(textContent);

        result.set("content", content);
        response.set("result", result);
    }

    private void handleMakeLlmMove(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String gameId = arguments.get("gameId").asText();
        String move = arguments.get("move").asText();
        
        String moveResponse = lichessClient.makeMove(gameId, move);
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", String.format("LLM move %s played successfully in game %s!\n\nResponse: %s", 
            move, gameId, moveResponse));
        content.add(textContent);

        result.set("content", content);
        response.set("result", result);
    }

    private void handleGetMoveHistory(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String gameId = arguments.get("gameId").asText();
        
        String pgn = lichessClient.getGamePgn(gameId);
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", String.format("Move History for game %s:\n\n%s", gameId, pgn));
        content.add(textContent);

        result.set("content", content);
        response.set("result", result);
    }

    private void handleGetBoardPosition(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String gameId = arguments.get("gameId").asText();
        
        String pgn = lichessClient.getGamePgn(gameId);
        String finalFen = chessEngine.applyMoves(pgn);
        String boardDescription = chessEngine.getBoardDescription();
        String activeColor = chessEngine.getActiveColor();
        boolean isCheck = chessEngine.isKingInCheck();
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", String.format("Board Position for game %s:\n\n" +
            "FEN: %s\n" +
            "Turn: %s to move\n" +
            "King in Check: %s\n\n" +
            "%s", 
            gameId, finalFen, 
            activeColor.equals("w") ? "White" : "Black",
            isCheck ? "Yes" : "No",
            boardDescription));
        content.add(textContent);

        result.set("content", content);
        response.set("result", result);
    }

    private void handleGetLegalMoves(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String gameId = arguments.get("gameId").asText();
        
        String pgn = lichessClient.getGamePgn(gameId);
        chessEngine.applyMoves(pgn);
        
        List<String> legalMoves = chessEngine.getLegalMoves();
        String activeColor = chessEngine.getActiveColor();
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", String.format("Legal Moves for game %s:\n\n" +
            "Turn: %s to move\n" +
            "Available moves: %s\n\n" +
            "Use make_llm_move to play one of these moves.", 
            gameId, 
            activeColor.equals("w") ? "White" : "Black",
            String.join(", ", legalMoves)));
        content.add(textContent);

        result.set("content", content);
        response.set("result", result);
    }

    private void handleWatchGame(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String gameId = arguments.get("gameId").asText();
        
        String gameUpdates = lichessClient.watchGameStream(gameId, 10);
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", String.format("Game Watch Updates for %s:\n\n%s\n\n" +
            "Use this tool periodically to monitor when it's your turn to move.", 
            gameId, gameUpdates));
        content.add(textContent);

        result.set("content", content);
        response.set("result", result);
    }

    private void handlePollForMyTurn(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String gameId = arguments.get("gameId").asText();
        int maxPolls = arguments.has("maxPolls") ? arguments.get("maxPolls").asInt() : 30;
        int intervalSeconds = arguments.has("intervalSeconds") ? arguments.get("intervalSeconds").asInt() : 3;
        
        String pollResult = lichessClient.pollForMyTurn(gameId, maxPolls, intervalSeconds);
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", String.format("Polling Results for game %s:\n\n%s", gameId, pollResult));
        content.add(textContent);

        result.set("content", content);
        response.set("result", result);
    }

    private void handleError(ObjectNode response, int code, String message, String data) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.put("data", data);
        }
        response.set("error", error);
    }

    // Tool schema definition methods (same as original server)
    private void addGetUserProfileTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "get_user_profile");
        tool.put("description", "Get a user's profile information from Lichess");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        ObjectNode usernameParam = objectMapper.createObjectNode();
        usernameParam.put("type", "string");
        usernameParam.put("description", "The username to get profile for");
        props.set("username", usernameParam);
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("username");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }

    private void addGetGameTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "get_game");
        tool.put("description", "Get information about a specific game");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        ObjectNode gameIdParam = objectMapper.createObjectNode();
        gameIdParam.put("type", "string");
        gameIdParam.put("description", "The game ID to retrieve");
        props.set("gameId", gameIdParam);
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("gameId");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }

    private void addCreateGameTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "create_game");
        tool.put("description", "Create a new Lichess game with two players and return the game URL");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        
        ObjectNode player1Param = objectMapper.createObjectNode();
        player1Param.put("type", "string");
        player1Param.put("description", "First player name");
        props.set("player1", player1Param);
        
        ObjectNode player2Param = objectMapper.createObjectNode();
        player2Param.put("type", "string");
        player2Param.put("description", "Second player name");
        props.set("player2", player2Param);
        
        ObjectNode timeControlParam = objectMapper.createObjectNode();
        timeControlParam.put("type", "string");
        timeControlParam.put("description", "Time control in format 'minutes+increment' (e.g., '10+0', '5+3'). Default is '10+0'");
        props.set("timeControl", timeControlParam);
        
        ObjectNode variantParam = objectMapper.createObjectNode();
        variantParam.put("type", "string");
        variantParam.put("description", "Chess variant (standard, chess960, crazyhouse, etc.). Default is 'standard'");
        props.set("variant", variantParam);
        
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("player1");
        required.add("player2");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }

    private void addPlayMoveTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "play_move");
        tool.put("description", "Make a move in a Lichess game as a bot player");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        
        ObjectNode gameIdParam = objectMapper.createObjectNode();
        gameIdParam.put("type", "string");
        gameIdParam.put("description", "The game ID to make a move in");
        props.set("gameId", gameIdParam);
        
        ObjectNode moveParam = objectMapper.createObjectNode();
        moveParam.put("type", "string");
        moveParam.put("description", "The move in UCI format (e.g., 'e2e4', 'e7e5')");
        props.set("move", moveParam);
        
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("gameId");
        required.add("move");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }

    private void addGetGameStateTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "get_game_state");
        tool.put("description", "Get the current state of a Lichess game including position and moves");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        
        ObjectNode gameIdParam = objectMapper.createObjectNode();
        gameIdParam.put("type", "string");
        gameIdParam.put("description", "The game ID to get state for");
        props.set("gameId", gameIdParam);
        
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("gameId");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }

    private void addAcceptChallengeTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "accept_challenge");
        tool.put("description", "Accept a Lichess challenge to start playing as a bot");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        
        ObjectNode challengeIdParam = objectMapper.createObjectNode();
        challengeIdParam.put("type", "string");
        challengeIdParam.put("description", "The challenge ID to accept");
        props.set("challengeId", challengeIdParam);
        
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("challengeId");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }

    private void addAnalyzeGameTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "analyze_game");
        tool.put("description", "Analyze a Lichess game and get the current board position, legal moves, and suggestions");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        
        ObjectNode gameIdParam = objectMapper.createObjectNode();
        gameIdParam.put("type", "string");
        gameIdParam.put("description", "The game ID to analyze");
        props.set("gameId", gameIdParam);
        
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("gameId");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }

    private void addMakeLlmMoveTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "make_llm_move");
        tool.put("description", "Make a move suggested by the LLM in a Lichess game");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        
        ObjectNode gameIdParam = objectMapper.createObjectNode();
        gameIdParam.put("type", "string");
        gameIdParam.put("description", "The game ID to make a move in");
        props.set("gameId", gameIdParam);
        
        ObjectNode moveParam = objectMapper.createObjectNode();
        moveParam.put("type", "string");
        moveParam.put("description", "The move in UCI format (e.g., 'e2e4', 'e7e5') suggested by the LLM");
        props.set("move", moveParam);
        
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("gameId");
        required.add("move");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }

    private void addGetMoveHistoryTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "get_move_history");
        tool.put("description", "Get the complete move history of a game in PGN format");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        
        ObjectNode gameIdParam = objectMapper.createObjectNode();
        gameIdParam.put("type", "string");
        gameIdParam.put("description", "The game ID to get move history for");
        props.set("gameId", gameIdParam);
        
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("gameId");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }

    private void addGetBoardPositionTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "get_board_position");
        tool.put("description", "Get the current board position in FEN format with visual representation");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        
        ObjectNode gameIdParam = objectMapper.createObjectNode();
        gameIdParam.put("type", "string");
        gameIdParam.put("description", "The game ID to get board position for");
        props.set("gameId", gameIdParam);
        
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("gameId");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }

    private void addGetLegalMovesTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "get_legal_moves");
        tool.put("description", "Get all legal moves available in the current position");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        
        ObjectNode gameIdParam = objectMapper.createObjectNode();
        gameIdParam.put("type", "string");
        gameIdParam.put("description", "The game ID to get legal moves for");
        props.set("gameId", gameIdParam);
        
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("gameId");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }

    private void addWatchGameTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "watch_game");
        tool.put("description", "Monitor a game and get notified when it's your turn to move");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        
        ObjectNode gameIdParam = objectMapper.createObjectNode();
        gameIdParam.put("type", "string");
        gameIdParam.put("description", "The game ID to monitor");
        props.set("gameId", gameIdParam);
        
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("gameId");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }

    private void addPollForMyTurnTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "poll_for_my_turn");
        tool.put("description", "Poll continuously until it's your turn to move or the game ends (background monitoring)");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        
        ObjectNode gameIdParam = objectMapper.createObjectNode();
        gameIdParam.put("type", "string");
        gameIdParam.put("description", "The game ID to monitor");
        props.set("gameId", gameIdParam);
        
        ObjectNode maxPollsParam = objectMapper.createObjectNode();
        maxPollsParam.put("type", "number");
        maxPollsParam.put("description", "Maximum number of polls (default 30)");
        props.set("maxPolls", maxPollsParam);
        
        ObjectNode intervalParam = objectMapper.createObjectNode();
        intervalParam.put("type", "number");
        intervalParam.put("description", "Interval between polls in seconds (default 3)");
        props.set("intervalSeconds", intervalParam);
        
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("gameId");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }
    
    // Engine analysis tool handlers
    private void handleGetBestMove(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String gameId = arguments.get("gameId").asText();
        
        // Get the current position FEN from game state
        String gameState = lichessClient.getBotGameState(gameId);
        String finalFen = extractFenFromGameState(gameState);
        
        EnhancedChessEngine.Move bestMove = enhancedEngine.findBestMove(finalFen);
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        
        if (bestMove != null) {
            String evalStr = enhancedEngine.formatEvaluation(bestMove.evaluation);
            textContent.put("text", String.format("Best Move for game %s:\n\n" +
                "Move: %s\n" +
                "Evaluation: %s (%d centipawns)\n" +
                "Current Position: %s\n\n" +
                "Engine recommends playing %s", 
                gameId, bestMove.uci, evalStr, bestMove.evaluation, finalFen, bestMove.uci));
        } else {
            textContent.put("text", String.format("No best move found for game %s. The game may be over.", gameId));
        }
        
        content.add(textContent);
        result.set("content", content);
        response.set("result", result);
    }

    private void handleAnalyzePosition(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String gameId = arguments.get("gameId").asText();
        int depth = arguments.has("depth") ? arguments.get("depth").asInt() : 4;
        
        // Get the current position FEN from game state
        String gameState = lichessClient.getBotGameState(gameId);
        String finalFen = extractFenFromGameState(gameState);
        
        EnhancedChessEngine.EngineAnalysis analysis = enhancedEngine.analyzePosition(finalFen, depth);
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", String.format("Chess Engine Analysis for game %s:\n\n%s", 
            gameId, analysis.toFormattedString()));
        content.add(textContent);

        result.set("content", content);
        response.set("result", result);
    }

    private void handleGetTopMoves(ObjectNode response, JsonNode arguments, LichessApiClient lichessClient) throws Exception {
        String gameId = arguments.get("gameId").asText();
        int count = arguments.has("count") ? arguments.get("count").asInt() : 5;
        
        // Get the current position FEN from game state
        String gameState = lichessClient.getBotGameState(gameId);
        String finalFen = extractFenFromGameState(gameState);
        
        List<EnhancedChessEngine.Move> topMoves = enhancedEngine.getTopMoves(finalFen, count);
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();

        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Top %d Moves for game %s:\n\n", count, gameId));
        sb.append(String.format("Position: %s\n\n", finalFen));
        
        for (int i = 0; i < topMoves.size(); i++) {
            EnhancedChessEngine.Move move = topMoves.get(i);
            String evalStr = enhancedEngine.formatEvaluation(move.evaluation);
            sb.append(String.format("%d. %s - %s (%d centipawns)\n", 
                i + 1, move.uci, evalStr, move.evaluation));
        }
        
        textContent.put("text", sb.toString());
        content.add(textContent);

        result.set("content", content);
        response.set("result", result);
    }
    
    // Engine analysis tool schema methods
    private void addGetBestMoveTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "get_best_move");
        tool.put("description", "Get the chess engine's best move suggestion for a position");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        
        ObjectNode gameIdParam = objectMapper.createObjectNode();
        gameIdParam.put("type", "string");
        gameIdParam.put("description", "The game ID to analyze");
        props.set("gameId", gameIdParam);
        
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("gameId");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }

    private void addAnalyzePositionTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "analyze_position");
        tool.put("description", "Get detailed chess engine analysis of a position including evaluation and top moves");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        
        ObjectNode gameIdParam = objectMapper.createObjectNode();
        gameIdParam.put("type", "string");
        gameIdParam.put("description", "The game ID to analyze");
        props.set("gameId", gameIdParam);
        
        ObjectNode depthParam = objectMapper.createObjectNode();
        depthParam.put("type", "number");
        depthParam.put("description", "Analysis depth (default 4)");
        props.set("depth", depthParam);
        
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("gameId");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }

    private void addGetTopMovesTool(ArrayNode tools) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "get_top_moves");
        tool.put("description", "Get multiple top move candidates with evaluations from the chess engine");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        
        ObjectNode gameIdParam = objectMapper.createObjectNode();
        gameIdParam.put("type", "string");
        gameIdParam.put("description", "The game ID to analyze");
        props.set("gameId", gameIdParam);
        
        ObjectNode countParam = objectMapper.createObjectNode();
        countParam.put("type", "number");
        countParam.put("description", "Number of top moves to return (default 5)");
        props.set("count", countParam);
        
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("gameId");
        schema.set("required", required);
        tool.set("inputSchema", schema);
        
        tools.add(tool);
    }
    
    /**
     * Extract FEN from game state JSON
     */
    private String extractFenFromGameState(String gameState) throws Exception {
        JsonNode gameStateJson = objectMapper.readTree(gameState);
        JsonNode nowPlaying = gameStateJson.get("nowPlaying");
        
        if (nowPlaying != null && nowPlaying.isArray() && nowPlaying.size() > 0) {
            JsonNode game = nowPlaying.get(0);
            JsonNode fen = game.get("fen");
            if (fen != null) {
                return fen.asText();
            }
        }
        
        // Fallback to starting position
        return "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    }
}