# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Model Context Protocol (MCP) server that provides integration with the Lichess API. It allows LLM clients to interact with Lichess chess data through standardized MCP tools.

## Build and Development Commands

### Build the project
```bash
mvn clean compile
```

### Package the application
```bash
mvn clean package
```

### Run the server

#### Console/stdin version (original)
```bash
java -cp target/lichess-mcp-server-1.0.0.jar com.example.lichess.LichessMcpServer
```

#### HTTP server version (remote)
```bash
java -cp target/lichess-mcp-server-1.0.0.jar com.example.lichess.LichessMcpHttpServer [port]
```

Default port is 8080. Example:
```bash
java -cp target/lichess-mcp-server-1.0.0.jar com.example.lichess.LichessMcpHttpServer 8080
```

The HTTP server provides these endpoints:
- `POST /mcp` - MCP JSON-RPC requests (requires `Authorization: Bearer <lichess_token>` header)
- `DELETE /mcp` - Session termination
- `GET /health` - Health check

### Run tests
```bash
mvn test
```

## Architecture

The project offers two server implementations sharing common components:

### Core Components

- **LichessMcpServer** (`src/main/java/com/example/lichess/LichessMcpServer.java`): Original MCP server implementation that handles JSON-RPC communication over stdin/stdout. Implements the MCP protocol with initialize, tools/list, and tools/call methods.

- **LichessMcpHttpServer** (`src/main/java/com/example/lichess/LichessMcpHttpServer.java`): HTTP transport version that accepts remote MCP requests over HTTP. Uses Jetty embedded server and implements token delegation where clients pass Lichess API tokens via Authorization headers.

- **LichessApiClient** (`src/main/java/com/example/lichess/LichessApiClient.java`): HTTP client wrapper for the Lichess API. Handles authentication, rate limiting, and API calls with proper error handling. Supports both environment variable tokens (stdin version) and per-request tokens (HTTP version).

### MCP Protocol Implementation

The server implements MCP protocol version 2024-11-05 with the following capabilities:
- Tool execution for Lichess API operations
- JSON-RPC 2.0 communication over stdin/stdout
- Error handling with proper MCP error codes

### Available Tools

- `get_user_profile`: Retrieves user profile information
- `get_game`: Fetches specific game details
- `create_game`: Creates new Lichess games with specified players, time controls, and variants
- `accept_challenge`: Accepts a Lichess challenge to play as a bot
- `play_move`: Makes moves in Lichess games using UCI notation
- `get_game_state`: Retrieves current game state and position

### LLM Chess Bot Tools

The following tools enable the LLM to act as a chess bot by providing comprehensive game analysis and move execution:

- `get_board_position`: Get current board position in FEN format with visual ASCII representation
- `get_move_history`: Get complete move history of a game in PGN format
- `get_legal_moves`: Get all legal moves available in the current position
- `make_llm_move`: Make a move suggested by the LLM (requires move parameter in UCI format)
- `analyze_game`: Comprehensive game analysis including position, legal moves, and board state

### LLM Chess Bot Workflow

1. **Accept Challenge**: Use `accept_challenge` to accept a Lichess challenge
2. **Analyze Position**: Use `get_board_position` to see the current board state
3. **Review History**: Use `get_move_history` to understand the game progression
4. **Check Legal Moves**: Use `get_legal_moves` to see all available moves
5. **Make Move**: Use `make_llm_move` with your chosen move in UCI format (e.g., "e2e4")
6. **Repeat**: Continue the cycle for each turn

### Additional API Methods in LichessApiClient

Beyond the exposed MCP tools, the client supports:
- `getUserGames()`: Get user's game history
- `getLeaderboard()`: Get performance type leaderboards  
- `getTournaments()` / `getTournament()`: Tournament data
- `streamGameMoves()`: Stream live game moves
- `getUserRatingHistory()`: Rating progression over time
- `getUserStatus()`: Current online status
- `createGame()`: Create open challenges with custom settings
- `upgradeToBotAccount()`: Upgrade account to bot status
- `makeMove()`: Execute moves via UCI notation
- `getBotGameState()`: Stream game state for bots
- `resignGame()`: Resign from current game
- `sendChatMessage()`: Send chat messages in games

## Configuration

### Console/stdin Version
The server requires a `LICHESS_API_TOKEN` environment variable for authenticated API access.

### HTTP Version
The server uses token delegation - clients must provide the Lichess API token via the `Authorization: Bearer <token>` header with each request. No server-side token configuration needed.

**Bot Functionality Requirements:**
- API token is mandatory for bot operations
- Account must be upgraded to bot status on Lichess (irreversible)
- Bot accounts can only play against other bots

### HTTP Server Security
- Validates Authorization header on every request
- Returns HTTP 401 for missing/invalid tokens
- Supports CORS for web client access
- Binds to localhost by default for security

## Dependencies

- Jackson for JSON processing
- Apache HttpClient for HTTP requests
- SLF4J + Logback for logging
- JUnit for testing

## Logging

Logs are written to both console and `logs/lichess-mcp-server.log` file. The main application uses INFO level logging while other components use WARN level.