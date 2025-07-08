# Lichess MCP Server

A Model Context Protocol (MCP) server that provides integration with the Lichess chess platform API. This server enables LLM clients to access Lichess data and functionality through standardized MCP tools.

## Features

- **User Profile Access**: Retrieve detailed user profiles including ratings, statistics, and preferences
- **Game Data**: Access specific game information including moves, analysis, and metadata
- **Game Creation**: Create new Lichess games with custom time controls and variants, returning shareable URLs
- **Bot Gameplay**: Play as a bot in Lichess games with move execution and game state monitoring
- **Extensible Architecture**: Clean, modular design ready for additional Lichess API integrations

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher
- A Lichess API token (optional, for authenticated requests)

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/lichess-mcp.git
   cd lichess-mcp
   ```

2. Build the project:
   ```bash
   mvn clean package
   ```

3. Set up your Lichess API token (required for bot functionality):
   ```bash
   export LICHESS_API_TOKEN=your_token_here
   ```

4. For bot gameplay, upgrade your Lichess account to a bot account:
   - Go to your Lichess preferences
   - Navigate to the "Bot" section
   - Click "Upgrade to Bot Account"
   - **Note:** This is irreversible and the account can only play against other bots

## Usage

### Running the Server

The server supports two transport methods:

#### Console/stdin version (for local MCP clients)
```bash
java -cp target/lichess-mcp-server-1.0.0.jar com.example.lichess.LichessMcpServer
```

#### HTTP server version (for remote access)
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

### MCP Client Configuration

#### For stdin/stdout transport:
```json
{
  "mcpServers": {
    "lichess": {
      "command": "java",
      "args": ["-cp", "target/lichess-mcp-server-1.0.0.jar", "com.example.lichess.LichessMcpServer"],
      "env": {
        "LICHESS_API_TOKEN": "your_lichess_api_token_here"
      }
    }
  }
}
```

#### For HTTP transport:
```bash
# Test HTTP endpoint
curl -X POST http://localhost:8080/mcp \
  -H "Authorization: Bearer your_lichess_token" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"initialize","params":{},"id":1}'

# Get user profile
curl -X POST http://localhost:8080/mcp \
  -H "Authorization: Bearer your_lichess_token" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/call","params":{"name":"get_user_profile","arguments":{"username":"jacobhm98"}},"id":2}'
```

## Available Tools

### get_user_profile
Retrieves comprehensive user profile information from Lichess.

**Parameters:**
- `username` (string): The Lichess username to query

**Example:**
```json
{
  "name": "get_user_profile",
  "arguments": {
    "username": "magnus"
  }
}
```

### get_game
Fetches detailed information about a specific game.

**Parameters:**
- `gameId` (string): The game ID to retrieve

**Example:**
```json
{
  "name": "get_game",
  "arguments": {
    "gameId": "abcd1234"
  }
}
```

### create_game
Creates a new Lichess game with two specified players and returns the game URL.

**Parameters:**
- `player1` (string, required): First player name
- `player2` (string, required): Second player name  
- `timeControl` (string, optional): Time control in format 'minutes+increment' (e.g., '10+0', '5+3'). Default is '10+0'
- `variant` (string, optional): Chess variant (standard, chess960, crazyhouse, antichess, atomic, horde, kingOfTheHill, racingKings, threeCheck). Default is 'standard'

**Example:**
```json
{
  "name": "create_game",
  "arguments": {
    "player1": "Alice",
    "player2": "Bob",
    "timeControl": "5+3",
    "variant": "standard"
  }
}
```

**Response:** Returns a formatted message with game details and a shareable URL that both players can use to access the game board.

### accept_challenge
Accepts a Lichess challenge to start playing as a bot.

**Parameters:**
- `challengeId` (string, required): The challenge ID to accept

**Example:**
```json
{
  "name": "accept_challenge",
  "arguments": {
    "challengeId": "challenge123"
  }
}
```

**Note:** Requires the account to be upgraded to a bot account via Lichess settings.

### play_move
Makes a move in a Lichess game as a bot player.

**Parameters:**
- `gameId` (string, required): The game ID to make a move in
- `move` (string, required): The move in UCI format (e.g., 'e2e4', 'e7e5', 'e1g1' for castling)

**Example:**
```json
{
  "name": "play_move",
  "arguments": {
    "gameId": "game123",
    "move": "e2e4"
  }
}
```

### get_game_state
Retrieves the current state of a Lichess game including position and moves.

**Parameters:**
- `gameId` (string, required): The game ID to get state for

**Example:**
```json
{
  "name": "get_game_state",
  "arguments": {
    "gameId": "game123"
  }
}
```

**Response:** Returns detailed game state including move history, current position, and game status.

## Development

### Building
```bash
mvn clean compile
```

### Running Tests
```bash
mvn test
```

### Packaging
```bash
mvn clean package
```

## Architecture

The server follows a clean, modular architecture:

- **LichessMcpServer**: Main MCP protocol handler managing JSON-RPC communication
- **LichessApiClient**: HTTP client for Lichess API interactions with proper error handling and rate limiting
- **Logging**: Comprehensive logging to both console and file (`logs/lichess-mcp-server.log`)

## API Rate Limits

Please be mindful of Lichess API rate limits:
- Public API: 15 requests per second
- Authenticated API: 50 requests per second (with API token)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
- Check the logs at `logs/lichess-mcp-server.log`
- Review the [Lichess API documentation](https://lichess.org/api)
- Open an issue in this repository
