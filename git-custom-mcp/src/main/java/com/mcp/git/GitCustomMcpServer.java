package com.mcp.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GitCustomMcpServer {
    private final ObjectMapper mapper;
    private final String workingDir;
    private HttpServer webhookServer;

    public GitCustomMcpServer() {
        this.mapper = new ObjectMapper();
        this.workingDir = System.getProperty("user.dir");
        System.err.println("Git Custom MCP Server initialized");
    }

    public static void main(String[] args) {
        new GitCustomMcpServer().run();
    }

    private void run() {
        startWebhookServer();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonNode request = mapper.readTree(line);
                    JsonNode response = handleRequest(request);
                    System.out.println(mapper.writeValueAsString(response));
                    System.out.flush();
                } catch (Exception e) {
                    ObjectNode errorResponse = createErrorResponse(null, -32603, "Parse error: " + e.getMessage());
                    System.out.println(mapper.writeValueAsString(errorResponse));
                    System.out.flush();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (webhookServer != null) {
                webhookServer.stop(0);
            }
        }
    }

    private JsonNode handleRequest(JsonNode request) {
        String method = request.get("method").asText();
        JsonNode id = request.get("id");
        
        try {
            JsonNode result = switch (method) {
                case "initialize" -> handleInitialize();
                case "tools/list" -> handleToolsList();
                case "tools/call" -> handleToolCall(request.get("params"));
                default -> throw new RuntimeException("Unknown method: " + method);
            };
            
            return createSuccessResponse(id, result);
        } catch (Exception e) {
            return createErrorResponse(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    private JsonNode handleInitialize() {
        ObjectNode response = mapper.createObjectNode();
        response.put("protocolVersion", "2024-11-05");
        
        ObjectNode capabilities = mapper.createObjectNode();
        ObjectNode tools = mapper.createObjectNode();
        tools.put("listChanged", false);
        capabilities.set("tools", tools);
        response.set("capabilities", capabilities);
        
        ObjectNode serverInfo = mapper.createObjectNode();
        serverInfo.put("name", "git-custom-mcp-server");
        serverInfo.put("version", "1.0.0");
        response.set("serverInfo", serverInfo);
        
        return response;
    }

    private JsonNode handleToolsList() {
        ArrayNode tools = mapper.createArrayNode();
        
        tools.add(createTool("git_status", "Get git repository status"));
        tools.add(createTool("git_log", "Get git commit history", 
            createParam("limit", "number", "Number of commits to show", false)));
        tools.add(createTool("git_branch", "List or create branches",
            createParam("branch_name", "string", "Branch name to create", false)));
        tools.add(createTool("git_add", "Add files to staging",
            createParam("files", "string", "Files to add (. for all)", true)));
        tools.add(createTool("git_commit", "Commit staged changes",
            createParam("message", "string", "Commit message", true)));
        tools.add(createTool("git_push", "Push commits to remote",
            createParam("remote", "string", "Remote name", false),
            createParam("branch", "string", "Branch name", false)));
        tools.add(createTool("git_pull", "Pull changes from remote"));
        tools.add(createTool("git_diff", "Show differences",
            createParam("file", "string", "Specific file to diff", false)));
        tools.add(createTool("get_repo_info", "Get repository information"));
        tools.add(createTool("webhook_status", "Check webhook server status"));
        
        ObjectNode response = mapper.createObjectNode();
        response.set("tools", tools);
        return response;
    }

    private JsonNode handleToolCall(JsonNode params) {
        String name = params.get("name").asText();
        JsonNode arguments = params.get("arguments");
        
        return switch (name) {
            case "git_status" -> gitStatus();
            case "git_log" -> gitLog(arguments.has("limit") ? arguments.get("limit").asInt() : 10);
            case "git_branch" -> gitBranch(arguments.has("branch_name") ? arguments.get("branch_name").asText() : null);
            case "git_add" -> gitAdd(arguments.get("files").asText());
            case "git_commit" -> gitCommit(arguments.get("message").asText());
            case "git_push" -> gitPush(
                arguments.has("remote") ? arguments.get("remote").asText() : "origin",
                arguments.has("branch") ? arguments.get("branch").asText() : null);
            case "git_pull" -> gitPull();
            case "git_diff" -> gitDiff(arguments.has("file") ? arguments.get("file").asText() : null);
            case "get_repo_info" -> getRepoInfo();
            case "webhook_status" -> webhookStatus();
            default -> throw new RuntimeException("Unknown tool: " + name);
        };
    }

    private JsonNode gitStatus() {
        try {
            String output = executeGitCommand("git", "status", "--porcelain");
            if (output.trim().isEmpty()) {
                return createToolResponse("text", "✅ Working directory clean");
            }
            return createToolResponse("text", "📋 Git Status:\n" + output);
        } catch (Exception e) {
            return createToolResponse("text", "❌ Error: " + e.getMessage());
        }
    }

    private JsonNode gitLog(int limit) {
        try {
            String output = executeGitCommand("git", "log", "--oneline", "-" + limit);
            return createToolResponse("text", "📜 Recent Commits:\n" + output);
        } catch (Exception e) {
            return createToolResponse("text", "❌ Error: " + e.getMessage());
        }
    }

    private JsonNode gitBranch(String branchName) {
        try {
            if (branchName == null) {
                String output = executeGitCommand("git", "branch", "-a");
                return createToolResponse("text", "🌿 Branches:\n" + output);
            } else {
                String output = executeGitCommand("git", "checkout", "-b", branchName);
                return createToolResponse("text", "✅ Created and switched to branch: " + branchName);
            }
        } catch (Exception e) {
            return createToolResponse("text", "❌ Error: " + e.getMessage());
        }
    }

    private JsonNode gitAdd(String files) {
        try {
            String output = executeGitCommand("git", "add", files);
            return createToolResponse("text", "✅ Added files: " + files);
        } catch (Exception e) {
            return createToolResponse("text", "❌ Error: " + e.getMessage());
        }
    }

    private JsonNode gitCommit(String message) {
        try {
            String output = executeGitCommand("git", "commit", "-m", message);
            return createToolResponse("text", "✅ Committed: " + message + "\n" + output);
        } catch (Exception e) {
            return createToolResponse("text", "❌ Error: " + e.getMessage());
        }
    }

    private JsonNode gitPush(String remote, String branch) {
        try {
            String[] command = branch != null 
                ? new String[]{"git", "push", remote, branch}
                : new String[]{"git", "push", remote};
            String output = executeGitCommand(command);
            return createToolResponse("text", "✅ Pushed to " + remote + "\n" + output);
        } catch (Exception e) {
            return createToolResponse("text", "❌ Error: " + e.getMessage());
        }
    }

    private JsonNode gitPull() {
        try {
            String output = executeGitCommand("git", "pull");
            return createToolResponse("text", "✅ Pulled changes:\n" + output);
        } catch (Exception e) {
            return createToolResponse("text", "❌ Error: " + e.getMessage());
        }
    }

    private JsonNode gitDiff(String file) {
        try {
            String[] command = file != null 
                ? new String[]{"git", "diff", file}
                : new String[]{"git", "diff"};
            String output = executeGitCommand(command);
            if (output.trim().isEmpty()) {
                return createToolResponse("text", "✅ No differences found");
            }
            return createToolResponse("text", "📊 Differences:\n" + output);
        } catch (Exception e) {
            return createToolResponse("text", "❌ Error: " + e.getMessage());
        }
    }

    private JsonNode getRepoInfo() {
        try {
            String remote = executeGitCommand("git", "remote", "get-url", "origin");
            String branch = executeGitCommand("git", "branch", "--show-current");
            String lastCommit = executeGitCommand("git", "log", "-1", "--oneline");
            
            StringBuilder info = new StringBuilder();
            info.append("📍 Repository Information:\n\n");
            info.append("🔗 Remote: ").append(remote.trim()).append("\n");
            info.append("🌿 Current Branch: ").append(branch.trim()).append("\n");
            info.append("📝 Last Commit: ").append(lastCommit.trim()).append("\n");
            info.append("📂 Working Directory: ").append(workingDir);
            
            return createToolResponse("text", info.toString());
        } catch (Exception e) {
            return createToolResponse("text", "❌ Error: " + e.getMessage());
        }
    }

    private void startWebhookServer() {
        try {
            webhookServer = HttpServer.create(new InetSocketAddress(8081), 0);
            webhookServer.createContext("/webhook", new WebhookHandler());
            webhookServer.setExecutor(null);
            webhookServer.start();
            System.err.println("Webhook server started on port 8081");
        } catch (IOException e) {
            System.err.println("Failed to start webhook server: " + e.getMessage());
        }
    }

    private JsonNode webhookStatus() {
        String status = webhookServer != null 
            ? "✅ Webhook server running on http://localhost:8081/webhook"
            : "❌ Webhook server not running";
        
        return createToolResponse("text", status);
    }

    private void showGitStatusOnConsole() {
        try {
            String status = executeGitCommand("git", "status", "--porcelain");
            String branch = executeGitCommand("git", "branch", "--show-current");
            
            System.out.println("\n=== GIT STATUS DETAILS ===");
            System.out.println("📍 Current Branch: " + branch.trim());
            
            if (status.trim().isEmpty()) {
                System.out.println("✅ Working directory clean");
            } else {
                System.out.println("📋 Changes detected:");
                System.out.println(status);
            }
            System.out.println("========================\n");
        } catch (Exception e) {
            System.err.println("Error getting git status: " + e.getMessage());
        }
    }

    private class WebhookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    JsonNode payload = mapper.readTree(body);
                    
                    System.out.println("\n🔔 Webhook received:");
                    System.out.println("Event: " + exchange.getRequestHeaders().getFirst("X-GitHub-Event"));
                    System.out.println("Repository: " + payload.path("repository").path("full_name").asText());
                    
                    showGitStatusOnConsole();
                    
                    String response = "OK";
                    exchange.sendResponseHeaders(200, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                } catch (Exception e) {
                    System.err.println("Webhook error: " + e.getMessage());
                    exchange.sendResponseHeaders(500, 0);
                    exchange.getResponseBody().close();
                }
            } else {
                exchange.sendResponseHeaders(405, 0);
                exchange.getResponseBody().close();
            }
        }
    }

    private String executeGitCommand(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(Paths.get(workingDir).toFile());
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            StringBuilder error = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    error.append(line).append("\n");
                }
            }
            throw new RuntimeException("Git command failed: " + error.toString());
        }
        
        return output.toString();
    }

    private ObjectNode createTool(String name, String description, ObjectNode... params) {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        ArrayNode required = mapper.createArrayNode();
        
        for (ObjectNode param : params) {
            String paramName = param.get("name").asText();
            properties.set(paramName, param);
            if (param.get("required").asBoolean()) {
                required.add(paramName);
            }
        }
        
        inputSchema.set("properties", properties);
        inputSchema.set("required", required);
        tool.set("inputSchema", inputSchema);
        
        return tool;
    }

    private ObjectNode createParam(String name, String type, String description, boolean required) {
        ObjectNode param = mapper.createObjectNode();
        param.put("name", name);
        param.put("type", type);
        param.put("description", description);
        param.put("required", required);
        return param;
    }

    private ObjectNode createSuccessResponse(JsonNode id, JsonNode result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.set("id", id);
        }
        response.set("result", result);
        return response;
    }

    private ObjectNode createErrorResponse(JsonNode id, int code, String message) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.set("id", id);
        }
        
        ObjectNode error = mapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);
        
        return response;
    }

    private ObjectNode createToolResponse(String type, String content) {
        ObjectNode response = mapper.createObjectNode();
        ArrayNode contentArray = mapper.createArrayNode();
        
        ObjectNode contentObj = mapper.createObjectNode();
        contentObj.put("type", type);
        contentObj.put("text", content);
        contentArray.add(contentObj);
        
        response.set("content", contentArray);
        return response;
    }
}