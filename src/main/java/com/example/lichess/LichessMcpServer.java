package com.example.lichess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LichessMcpServer {
  private static final Logger logger = LoggerFactory.getLogger(LichessMcpServer.class);
  private final ObjectMapper objectMapper;
  private final LichessApiClient lichessClient;
  private final ChessEngine chessEngine;
  private final EnhancedChessEngine enhancedEngine;
  private final PrintWriter out;
  private final BufferedReader in;

  public LichessMcpServer() {
    this.objectMapper = new ObjectMapper();
    this.lichessClient = new LichessApiClient();
    this.chessEngine = new ChessEngine();
    this.enhancedEngine = new EnhancedChessEngine(chessEngine);
    this.out = new PrintWriter(System.out, true);
    this.in = new BufferedReader(new InputStreamReader(System.in));
  }

  public static void main(String[] args) {
    LichessMcpServer server = new LichessMcpServer();
    server.run();
  }

  public void run() {
    logger.info("Starting Lichess MCP Server...");

    try {
      String line;
      while ((line = in.readLine()) != null) {
        processRequest(line);
      }
    } catch (IOException e) {
      logger.error("Error reading input", e);
    }
  }

  private void processRequest(String jsonLine) {
    try {
      JsonNode request = objectMapper.readTree(jsonLine);
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

      switch (method) {
        case "initialize":
          handleInitialize(response, params);
          break;
        case "tools/list":
          handleToolsList(response);
          break;
        case "tools/call":
          handleToolCall(response, params);
          break;
        default:
          handleError(response, -32601, "Method not found", null);
      }

      out.println(objectMapper.writeValueAsString(response));

    } catch (Exception e) {
      logger.error("Error processing request", e);
      sendError(-32603, "Internal error", null);
    }
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

    ObjectNode getUserProfileTool = objectMapper.createObjectNode();
    getUserProfileTool.put("name", "get_user_profile");
    getUserProfileTool.put("description", "Get a user's profile information from Lichess");

    ObjectNode userProfileSchema = objectMapper.createObjectNode();
    userProfileSchema.put("type", "object");
    ObjectNode userProfileProps = objectMapper.createObjectNode();
    ObjectNode usernameParam = objectMapper.createObjectNode();
    usernameParam.put("type", "string");
    usernameParam.put("description", "The username to get profile for");
    userProfileProps.set("username", usernameParam);
    userProfileSchema.set("properties", userProfileProps);
    ArrayNode required = objectMapper.createArrayNode();
    required.add("username");
    userProfileSchema.set("required", required);

    ObjectNode userProfileInputSchema = objectMapper.createObjectNode();
    userProfileInputSchema.put("type", "object");
    userProfileInputSchema.set("properties", userProfileProps);
    userProfileInputSchema.set("required", required);
    getUserProfileTool.set("inputSchema", userProfileInputSchema);

    tools.add(getUserProfileTool);

    ObjectNode getGameTool = objectMapper.createObjectNode();
    getGameTool.put("name", "get_game");
    getGameTool.put("description", "Get information about a specific game");

    ObjectNode gameSchema = objectMapper.createObjectNode();
    gameSchema.put("type", "object");
    ObjectNode gameProps = objectMapper.createObjectNode();
    ObjectNode gameIdParam = objectMapper.createObjectNode();
    gameIdParam.put("type", "string");
    gameIdParam.put("description", "The game ID to retrieve");
    gameProps.set("gameId", gameIdParam);
    gameSchema.set("properties", gameProps);
    ArrayNode gameRequired = objectMapper.createArrayNode();
    gameRequired.add("gameId");
    gameSchema.set("required", gameRequired);

    ObjectNode gameInputSchema = objectMapper.createObjectNode();
    gameInputSchema.put("type", "object");
    gameInputSchema.set("properties", gameProps);
    gameInputSchema.set("required", gameRequired);
    getGameTool.set("inputSchema", gameInputSchema);

    tools.add(getGameTool);
    
    ObjectNode createGameTool = objectMapper.createObjectNode();
    createGameTool.put("name", "create_game");
    createGameTool.put("description", "Create a new Lichess game with two players and return the game URL");
    
    ObjectNode createGameSchema = objectMapper.createObjectNode();
    createGameSchema.put("type", "object");
    ObjectNode createGameProps = objectMapper.createObjectNode();
    
    ObjectNode player1Param = objectMapper.createObjectNode();
    player1Param.put("type", "string");
    player1Param.put("description", "First player name");
    createGameProps.set("player1", player1Param);
    
    ObjectNode player2Param = objectMapper.createObjectNode();
    player2Param.put("type", "string");
    player2Param.put("description", "Second player name");
    createGameProps.set("player2", player2Param);
    
    ObjectNode timeControlParam = objectMapper.createObjectNode();
    timeControlParam.put("type", "string");
    timeControlParam.put("description", "Time control in format 'minutes+increment' (e.g., '10+0', '5+3'). Default is '10+0'");
    createGameProps.set("timeControl", timeControlParam);
    
    ObjectNode variantParam = objectMapper.createObjectNode();
    variantParam.put("type", "string");
    variantParam.put("description", "Chess variant (standard, chess960, crazyhouse, etc.). Default is 'standard'");
    createGameProps.set("variant", variantParam);
    
    createGameSchema.set("properties", createGameProps);
    ArrayNode createGameRequired = objectMapper.createArrayNode();
    createGameRequired.add("player1");
    createGameRequired.add("player2");
    createGameSchema.set("required", createGameRequired);
    
    ObjectNode createGameInputSchema = objectMapper.createObjectNode();
    createGameInputSchema.put("type", "object");
    createGameInputSchema.set("properties", createGameProps);
    createGameInputSchema.set("required", createGameRequired);
    createGameTool.set("inputSchema", createGameInputSchema);
    
    tools.add(createGameTool);
    
    // Play Move Tool
    ObjectNode playMoveTool = objectMapper.createObjectNode();
    playMoveTool.put("name", "play_move");
    playMoveTool.put("description", "Make a move in a Lichess game as a bot player");
    
    ObjectNode playMoveSchema = objectMapper.createObjectNode();
    playMoveSchema.put("type", "object");
    ObjectNode playMoveProps = objectMapper.createObjectNode();
    
    ObjectNode gameIdMoveParam = objectMapper.createObjectNode();
    gameIdMoveParam.put("type", "string");
    gameIdMoveParam.put("description", "The game ID to make a move in");
    playMoveProps.set("gameId", gameIdMoveParam);
    
    ObjectNode moveParam = objectMapper.createObjectNode();
    moveParam.put("type", "string");
    moveParam.put("description", "The move in UCI format (e.g., 'e2e4', 'e7e5')");
    playMoveProps.set("move", moveParam);
    
    playMoveSchema.set("properties", playMoveProps);
    ArrayNode playMoveRequired = objectMapper.createArrayNode();
    playMoveRequired.add("gameId");
    playMoveRequired.add("move");
    playMoveSchema.set("required", playMoveRequired);
    
    ObjectNode playMoveInputSchema = objectMapper.createObjectNode();
    playMoveInputSchema.put("type", "object");
    playMoveInputSchema.set("properties", playMoveProps);
    playMoveInputSchema.set("required", playMoveRequired);
    playMoveTool.set("inputSchema", playMoveInputSchema);
    
    tools.add(playMoveTool);
    
    // Get Game State Tool
    ObjectNode getGameStateTool = objectMapper.createObjectNode();
    getGameStateTool.put("name", "get_game_state");
    getGameStateTool.put("description", "Get the current state of a Lichess game including position and moves");
    
    ObjectNode gameStateSchema = objectMapper.createObjectNode();
    gameStateSchema.put("type", "object");
    ObjectNode gameStateProps = objectMapper.createObjectNode();
    
    ObjectNode gameIdStateParam = objectMapper.createObjectNode();
    gameIdStateParam.put("type", "string");
    gameIdStateParam.put("description", "The game ID to get state for");
    gameStateProps.set("gameId", gameIdStateParam);
    
    gameStateSchema.set("properties", gameStateProps);
    ArrayNode gameStateRequired = objectMapper.createArrayNode();
    gameStateRequired.add("gameId");
    gameStateSchema.set("required", gameStateRequired);
    
    ObjectNode gameStateInputSchema = objectMapper.createObjectNode();
    gameStateInputSchema.put("type", "object");
    gameStateInputSchema.set("properties", gameStateProps);
    gameStateInputSchema.set("required", gameStateRequired);
    getGameStateTool.set("inputSchema", gameStateInputSchema);
    
    tools.add(getGameStateTool);
    
    // Accept Challenge Tool
    ObjectNode acceptChallengeTool = objectMapper.createObjectNode();
    acceptChallengeTool.put("name", "accept_challenge");
    acceptChallengeTool.put("description", "Accept a Lichess challenge to start playing as a bot");
    
    ObjectNode acceptChallengeSchema = objectMapper.createObjectNode();
    acceptChallengeSchema.put("type", "object");
    ObjectNode acceptChallengeProps = objectMapper.createObjectNode();
    
    ObjectNode challengeIdParam = objectMapper.createObjectNode();
    challengeIdParam.put("type", "string");
    challengeIdParam.put("description", "The challenge ID to accept");
    acceptChallengeProps.set("challengeId", challengeIdParam);
    
    acceptChallengeSchema.set("properties", acceptChallengeProps);
    ArrayNode acceptChallengeRequired = objectMapper.createArrayNode();
    acceptChallengeRequired.add("challengeId");
    acceptChallengeSchema.set("required", acceptChallengeRequired);
    
    ObjectNode acceptChallengeInputSchema = objectMapper.createObjectNode();
    acceptChallengeInputSchema.put("type", "object");
    acceptChallengeInputSchema.set("properties", acceptChallengeProps);
    acceptChallengeInputSchema.set("required", acceptChallengeRequired);
    acceptChallengeTool.set("inputSchema", acceptChallengeInputSchema);
    
    tools.add(acceptChallengeTool);
    
    // Analyze Game Tool
    ObjectNode analyzeGameTool = objectMapper.createObjectNode();
    analyzeGameTool.put("name", "analyze_game");
    analyzeGameTool.put("description", "Analyze a Lichess game and get the current board position, legal moves, and suggestions");
    
    ObjectNode analyzeSchema = objectMapper.createObjectNode();
    analyzeSchema.put("type", "object");
    ObjectNode analyzeProps = objectMapper.createObjectNode();
    
    ObjectNode gameIdAnalyzeParam = objectMapper.createObjectNode();
    gameIdAnalyzeParam.put("type", "string");
    gameIdAnalyzeParam.put("description", "The game ID to analyze");
    analyzeProps.set("gameId", gameIdAnalyzeParam);
    
    analyzeSchema.set("properties", analyzeProps);
    ArrayNode analyzeRequired = objectMapper.createArrayNode();
    analyzeRequired.add("gameId");
    analyzeSchema.set("required", analyzeRequired);
    
    ObjectNode analyzeInputSchema = objectMapper.createObjectNode();
    analyzeInputSchema.put("type", "object");
    analyzeInputSchema.set("properties", analyzeProps);
    analyzeInputSchema.set("required", analyzeRequired);
    analyzeGameTool.set("inputSchema", analyzeInputSchema);
    
    tools.add(analyzeGameTool);
    
    // Make LLM Move Tool - LLM provides the move to make
    ObjectNode makeLlmMoveTool = objectMapper.createObjectNode();
    makeLlmMoveTool.put("name", "make_llm_move");
    makeLlmMoveTool.put("description", "Make a move suggested by the LLM in a Lichess game");
    
    ObjectNode llmMoveSchema = objectMapper.createObjectNode();
    llmMoveSchema.put("type", "object");
    ObjectNode llmMoveProps = objectMapper.createObjectNode();
    
    ObjectNode gameIdLlmParam = objectMapper.createObjectNode();
    gameIdLlmParam.put("type", "string");
    gameIdLlmParam.put("description", "The game ID to make a move in");
    llmMoveProps.set("gameId", gameIdLlmParam);
    
    ObjectNode llmMoveParam = objectMapper.createObjectNode();
    llmMoveParam.put("type", "string");
    llmMoveParam.put("description", "The move in UCI format (e.g., 'e2e4', 'e7e5') suggested by the LLM");
    llmMoveProps.set("move", llmMoveParam);
    
    llmMoveSchema.set("properties", llmMoveProps);
    ArrayNode llmMoveRequired = objectMapper.createArrayNode();
    llmMoveRequired.add("gameId");
    llmMoveRequired.add("move");
    llmMoveSchema.set("required", llmMoveRequired);
    
    ObjectNode llmMoveInputSchema = objectMapper.createObjectNode();
    llmMoveInputSchema.put("type", "object");
    llmMoveInputSchema.set("properties", llmMoveProps);
    llmMoveInputSchema.set("required", llmMoveRequired);
    makeLlmMoveTool.set("inputSchema", llmMoveInputSchema);
    
    tools.add(makeLlmMoveTool);
    
    // Get Move History Tool
    ObjectNode getMoveHistoryTool = objectMapper.createObjectNode();
    getMoveHistoryTool.put("name", "get_move_history");
    getMoveHistoryTool.put("description", "Get the complete move history of a game in PGN format");
    
    ObjectNode historySchema = objectMapper.createObjectNode();
    historySchema.put("type", "object");
    ObjectNode historyProps = objectMapper.createObjectNode();
    
    ObjectNode gameIdHistoryParam = objectMapper.createObjectNode();
    gameIdHistoryParam.put("type", "string");
    gameIdHistoryParam.put("description", "The game ID to get move history for");
    historyProps.set("gameId", gameIdHistoryParam);
    
    historySchema.set("properties", historyProps);
    ArrayNode historyRequired = objectMapper.createArrayNode();
    historyRequired.add("gameId");
    historySchema.set("required", historyRequired);
    
    ObjectNode historyInputSchema = objectMapper.createObjectNode();
    historyInputSchema.put("type", "object");
    historyInputSchema.set("properties", historyProps);
    historyInputSchema.set("required", historyRequired);
    getMoveHistoryTool.set("inputSchema", historyInputSchema);
    
    tools.add(getMoveHistoryTool);
    
    // Get Board Position Tool
    ObjectNode getBoardTool = objectMapper.createObjectNode();
    getBoardTool.put("name", "get_board_position");
    getBoardTool.put("description", "Get the current board position in FEN format with visual representation");
    
    ObjectNode boardSchema = objectMapper.createObjectNode();
    boardSchema.put("type", "object");
    ObjectNode boardProps = objectMapper.createObjectNode();
    
    ObjectNode gameIdBoardParam = objectMapper.createObjectNode();
    gameIdBoardParam.put("type", "string");
    gameIdBoardParam.put("description", "The game ID to get board position for");
    boardProps.set("gameId", gameIdBoardParam);
    
    boardSchema.set("properties", boardProps);
    ArrayNode boardRequired = objectMapper.createArrayNode();
    boardRequired.add("gameId");
    boardSchema.set("required", boardRequired);
    
    ObjectNode boardInputSchema = objectMapper.createObjectNode();
    boardInputSchema.put("type", "object");
    boardInputSchema.set("properties", boardProps);
    boardInputSchema.set("required", boardRequired);
    getBoardTool.set("inputSchema", boardInputSchema);
    
    tools.add(getBoardTool);
    
    // Get Legal Moves Tool
    ObjectNode getLegalMovesTool = objectMapper.createObjectNode();
    getLegalMovesTool.put("name", "get_legal_moves");
    getLegalMovesTool.put("description", "Get all legal moves available in the current position");
    
    ObjectNode legalMovesSchema = objectMapper.createObjectNode();
    legalMovesSchema.put("type", "object");
    ObjectNode legalMovesProps = objectMapper.createObjectNode();
    
    ObjectNode gameIdLegalParam = objectMapper.createObjectNode();
    gameIdLegalParam.put("type", "string");
    gameIdLegalParam.put("description", "The game ID to get legal moves for");
    legalMovesProps.set("gameId", gameIdLegalParam);
    
    legalMovesSchema.set("properties", legalMovesProps);
    ArrayNode legalMovesRequired = objectMapper.createArrayNode();
    legalMovesRequired.add("gameId");
    legalMovesSchema.set("required", legalMovesRequired);
    
    ObjectNode legalMovesInputSchema = objectMapper.createObjectNode();
    legalMovesInputSchema.put("type", "object");
    legalMovesInputSchema.set("properties", legalMovesProps);
    legalMovesInputSchema.set("required", legalMovesRequired);
    getLegalMovesTool.set("inputSchema", legalMovesInputSchema);
    
    tools.add(getLegalMovesTool);
    
    // Watch Game Tool - Monitor game for updates
    ObjectNode watchGameTool = objectMapper.createObjectNode();
    watchGameTool.put("name", "watch_game");
    watchGameTool.put("description", "Monitor a game and get notified when it's your turn to move");
    
    ObjectNode watchSchema = objectMapper.createObjectNode();
    watchSchema.put("type", "object");
    ObjectNode watchProps = objectMapper.createObjectNode();
    
    ObjectNode gameIdWatchParam = objectMapper.createObjectNode();
    gameIdWatchParam.put("type", "string");
    gameIdWatchParam.put("description", "The game ID to monitor");
    watchProps.set("gameId", gameIdWatchParam);
    
    watchSchema.set("properties", watchProps);
    ArrayNode watchRequired = objectMapper.createArrayNode();
    watchRequired.add("gameId");
    watchSchema.set("required", watchRequired);
    
    ObjectNode watchInputSchema = objectMapper.createObjectNode();
    watchInputSchema.put("type", "object");
    watchInputSchema.set("properties", watchProps);
    watchInputSchema.set("required", watchRequired);
    watchGameTool.set("inputSchema", watchInputSchema);
    
    tools.add(watchGameTool);
    
    // Poll for Turn Tool - Continuously check until it's your turn
    ObjectNode pollTurnTool = objectMapper.createObjectNode();
    pollTurnTool.put("name", "poll_for_my_turn");
    pollTurnTool.put("description", "Poll continuously until it's your turn to move or the game ends (background monitoring)");
    
    ObjectNode pollSchema = objectMapper.createObjectNode();
    pollSchema.put("type", "object");
    ObjectNode pollProps = objectMapper.createObjectNode();
    
    ObjectNode gameIdPollParam = objectMapper.createObjectNode();
    gameIdPollParam.put("type", "string");
    gameIdPollParam.put("description", "The game ID to monitor");
    pollProps.set("gameId", gameIdPollParam);
    
    ObjectNode maxPollsParam = objectMapper.createObjectNode();
    maxPollsParam.put("type", "number");
    maxPollsParam.put("description", "Maximum number of polls (default 30)");
    pollProps.set("maxPolls", maxPollsParam);
    
    ObjectNode intervalParam = objectMapper.createObjectNode();
    intervalParam.put("type", "number");
    intervalParam.put("description", "Interval between polls in seconds (default 3)");
    pollProps.set("intervalSeconds", intervalParam);
    
    pollSchema.set("properties", pollProps);
    ArrayNode pollRequired = objectMapper.createArrayNode();
    pollRequired.add("gameId");
    pollSchema.set("required", pollRequired);
    
    ObjectNode pollInputSchema = objectMapper.createObjectNode();
    pollInputSchema.put("type", "object");
    pollInputSchema.set("properties", pollProps);
    pollInputSchema.set("required", pollRequired);
    pollTurnTool.set("inputSchema", pollInputSchema);
    
    tools.add(pollTurnTool);
    
    // Engine Analysis Tools
    ObjectNode getBestMoveTool = objectMapper.createObjectNode();
    getBestMoveTool.put("name", "get_best_move");
    getBestMoveTool.put("description", "Get the chess engine's best move suggestion for a position");
    
    ObjectNode bestMoveSchema = objectMapper.createObjectNode();
    bestMoveSchema.put("type", "object");
    ObjectNode bestMoveProps = objectMapper.createObjectNode();
    
    ObjectNode gameIdBestParam = objectMapper.createObjectNode();
    gameIdBestParam.put("type", "string");
    gameIdBestParam.put("description", "The game ID to analyze");
    bestMoveProps.set("gameId", gameIdBestParam);
    
    bestMoveSchema.set("properties", bestMoveProps);
    ArrayNode bestMoveRequired = objectMapper.createArrayNode();
    bestMoveRequired.add("gameId");
    bestMoveSchema.set("required", bestMoveRequired);
    
    ObjectNode bestMoveInputSchema = objectMapper.createObjectNode();
    bestMoveInputSchema.put("type", "object");
    bestMoveInputSchema.set("properties", bestMoveProps);
    bestMoveInputSchema.set("required", bestMoveRequired);
    getBestMoveTool.set("inputSchema", bestMoveInputSchema);
    
    tools.add(getBestMoveTool);
    
    // Analyze Position Tool
    ObjectNode analyzePositionTool = objectMapper.createObjectNode();
    analyzePositionTool.put("name", "analyze_position");
    analyzePositionTool.put("description", "Get detailed chess engine analysis of a position including evaluation and top moves");
    
    ObjectNode analyzePositionSchema = objectMapper.createObjectNode();
    analyzePositionSchema.put("type", "object");
    ObjectNode analyzePositionProps = objectMapper.createObjectNode();
    
    ObjectNode gameIdAnalyzePositionParam = objectMapper.createObjectNode();
    gameIdAnalyzePositionParam.put("type", "string");
    gameIdAnalyzePositionParam.put("description", "The game ID to analyze");
    analyzePositionProps.set("gameId", gameIdAnalyzePositionParam);
    
    ObjectNode depthParam = objectMapper.createObjectNode();
    depthParam.put("type", "number");
    depthParam.put("description", "Analysis depth (default 4)");
    analyzePositionProps.set("depth", depthParam);
    
    analyzePositionSchema.set("properties", analyzePositionProps);
    ArrayNode analyzePositionRequired = objectMapper.createArrayNode();
    analyzePositionRequired.add("gameId");
    analyzePositionSchema.set("required", analyzePositionRequired);
    
    ObjectNode analyzePositionInputSchema = objectMapper.createObjectNode();
    analyzePositionInputSchema.put("type", "object");
    analyzePositionInputSchema.set("properties", analyzePositionProps);
    analyzePositionInputSchema.set("required", analyzePositionRequired);
    analyzePositionTool.set("inputSchema", analyzePositionInputSchema);
    
    tools.add(analyzePositionTool);
    
    // Get Top Moves Tool
    ObjectNode getTopMovesTool = objectMapper.createObjectNode();
    getTopMovesTool.put("name", "get_top_moves");
    getTopMovesTool.put("description", "Get multiple top move candidates with evaluations from the chess engine");
    
    ObjectNode topMovesSchema = objectMapper.createObjectNode();
    topMovesSchema.put("type", "object");
    ObjectNode topMovesProps = objectMapper.createObjectNode();
    
    ObjectNode gameIdTopMovesParam = objectMapper.createObjectNode();
    gameIdTopMovesParam.put("type", "string");
    gameIdTopMovesParam.put("description", "The game ID to analyze");
    topMovesProps.set("gameId", gameIdTopMovesParam);
    
    ObjectNode countParam = objectMapper.createObjectNode();
    countParam.put("type", "number");
    countParam.put("description", "Number of top moves to return (default 5)");
    topMovesProps.set("count", countParam);
    
    topMovesSchema.set("properties", topMovesProps);
    ArrayNode topMovesRequired = objectMapper.createArrayNode();
    topMovesRequired.add("gameId");
    topMovesSchema.set("required", topMovesRequired);
    
    ObjectNode topMovesInputSchema = objectMapper.createObjectNode();
    topMovesInputSchema.put("type", "object");
    topMovesInputSchema.set("properties", topMovesProps);
    topMovesInputSchema.set("required", topMovesRequired);
    getTopMovesTool.set("inputSchema", topMovesInputSchema);
    
    tools.add(getTopMovesTool);

    ObjectNode result = objectMapper.createObjectNode();
    result.set("tools", tools);
    response.set("result", result);
  }

  private void handleToolCall(ObjectNode response, JsonNode params) {
    String toolName = params.get("name").asText();
    JsonNode arguments = params.get("arguments");

    try {
      switch (toolName) {
        case "get_user_profile":
          handleGetUserProfile(response, arguments);
          break;
        case "get_game":
          handleGetGame(response, arguments);
          break;
        case "create_game":
          handleCreateGame(response, arguments);
          break;
        case "play_move":
          handlePlayMove(response, arguments);
          break;
        case "get_game_state":
          handleGetGameState(response, arguments);
          break;
        case "accept_challenge":
          handleAcceptChallenge(response, arguments);
          break;
        case "analyze_game":
          handleAnalyzeGame(response, arguments);
          break;
        case "make_llm_move":
          handleMakeLlmMove(response, arguments);
          break;
        case "get_move_history":
          handleGetMoveHistory(response, arguments);
          break;
        case "get_board_position":
          handleGetBoardPosition(response, arguments);
          break;
        case "get_legal_moves":
          handleGetLegalMoves(response, arguments);
          break;
        case "watch_game":
          handleWatchGame(response, arguments);
          break;
        case "poll_for_my_turn":
          handlePollForMyTurn(response, arguments);
          break;
        case "get_best_move":
          handleGetBestMove(response, arguments);
          break;
        case "analyze_position":
          handleAnalyzePosition(response, arguments);
          break;
        case "get_top_moves":
          handleGetTopMoves(response, arguments);
          break;
        default:
          handleError(response, -32602, "Unknown tool", null);
      }
    } catch (Exception e) {
      logger.error("Error calling tool: " + toolName, e);
      handleError(response, -32603, "Tool execution error", e.getMessage());
    }
  }

  private void handleGetUserProfile(ObjectNode response, JsonNode arguments) throws Exception {
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

  private void handleGetGame(ObjectNode response, JsonNode arguments) throws Exception {
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

  private void handleCreateGame(ObjectNode response, JsonNode arguments) throws Exception {
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

  private void handlePlayMove(ObjectNode response, JsonNode arguments) throws Exception {
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

  private void handleGetGameState(ObjectNode response, JsonNode arguments) throws Exception {
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

  private void handleAcceptChallenge(ObjectNode response, JsonNode arguments) throws Exception {
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

  private void handleAnalyzeGame(ObjectNode response, JsonNode arguments) throws Exception {
    String gameId = arguments.get("gameId").asText();
    
    // Get game PGN from Lichess
    String pgn = lichessClient.getGamePgn(gameId);
    
    // Analyze with chess engine
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

  private void handleMakeLlmMove(ObjectNode response, JsonNode arguments) throws Exception {
    String gameId = arguments.get("gameId").asText();
    String move = arguments.get("move").asText();
    
    // Make the LLM-suggested move
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

  private void handleGetMoveHistory(ObjectNode response, JsonNode arguments) throws Exception {
    String gameId = arguments.get("gameId").asText();
    
    // Get game PGN
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

  private void handleGetBoardPosition(ObjectNode response, JsonNode arguments) throws Exception {
    String gameId = arguments.get("gameId").asText();
    
    // Get the current position FEN from game state (same as handleGetBestMove)
    String gameState = lichessClient.getBotGameState(gameId);
    String finalFen = extractFenFromGameState(gameState);
    chessEngine.setFen(finalFen);
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

  private void handleGetLegalMoves(ObjectNode response, JsonNode arguments) throws Exception {
    String gameId = arguments.get("gameId").asText();
    
    // Get the current position FEN from game state (same as handleGetBestMove)
    String gameState = lichessClient.getBotGameState(gameId);
    String finalFen = extractFenFromGameState(gameState);
    chessEngine.setFen(finalFen);
    
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

  private void handleWatchGame(ObjectNode response, JsonNode arguments) throws Exception {
    String gameId = arguments.get("gameId").asText();
    
    // Watch for game updates - limit to 10 updates to avoid infinite streaming
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

  private void handlePollForMyTurn(ObjectNode response, JsonNode arguments) throws Exception {
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

  private void handleGetBestMove(ObjectNode response, JsonNode arguments) throws Exception {
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

  private void handleAnalyzePosition(ObjectNode response, JsonNode arguments) throws Exception {
    String gameId = arguments.get("gameId").asText();
    int depth = arguments.has("depth") ? arguments.get("depth").asInt() : 4;
    
    String pgn = lichessClient.getGamePgn(gameId);
    String finalFen = chessEngine.applyMoves(pgn);
    
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

  private void handleGetTopMoves(ObjectNode response, JsonNode arguments) throws Exception {
    String gameId = arguments.get("gameId").asText();
    int count = arguments.has("count") ? arguments.get("count").asInt() : 5;
    
    String pgn = lichessClient.getGamePgn(gameId);
    String finalFen = chessEngine.applyMoves(pgn);
    
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

  private void handleError(ObjectNode response, int code, String message, String data) {
    ObjectNode error = objectMapper.createObjectNode();
    error.put("code", code);
    error.put("message", message);
    if (data != null) {
      error.put("data", data);
    }
    response.set("error", error);
  }

  private void sendError(int code, String message, String data) {
    ObjectNode errorResponse = objectMapper.createObjectNode();
    errorResponse.put("jsonrpc", "2.0");
    errorResponse.putNull("id");

    ObjectNode error = objectMapper.createObjectNode();
    error.put("code", code);
    error.put("message", message);
    if (data != null) {
      error.put("data", data);
    }
    errorResponse.set("error", error);

    try {
      out.println(objectMapper.writeValueAsString(errorResponse));
    } catch (Exception e) {
      logger.error("Error sending error response", e);
    }
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

