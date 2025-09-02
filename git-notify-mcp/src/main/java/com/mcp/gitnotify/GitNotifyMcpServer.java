package com.mcp.gitnotify;

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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GitNotifyMcpServer {
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String githubToken;
    private final String githubRepo;
    private final ScheduledExecutorService scheduler;
    private HttpServer webhookServer;

    public GitNotifyMcpServer() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.mapper = new ObjectMapper();
        this.githubToken = System.getenv("GITHUB_TOKEN");
        this.githubRepo = System.getenv("GITHUB_REPO");
        this.scheduler = Executors.newScheduledThreadPool(2);
        System.err.println("Git Notify MCP Server initialized");
    }

    public static void main(String[] args) {
        new GitNotifyMcpServer().run();
    }

    private void run() {
        startWebhookServer();
        startPolling();
        
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
            shutdown();
        }
    }

    private void startWebhookServer() {
        try {
            webhookServer = HttpServer.create(new InetSocketAddress(8080), 0);
            webhookServer.createContext("/webhook", new WebhookHandler());
            webhookServer.setExecutor(null);
            webhookServer.start();
            System.err.println("Webhook server started on port 8080");
        } catch (IOException e) {
            System.err.println("Failed to start webhook server: " + e.getMessage());
        }
    }

    private void startPolling() {
        scheduler.scheduleAtFixedRate(this::pollWorkflowStatus, 0, 30, TimeUnit.SECONDS);
        System.err.println("Started polling workflow status every 30 seconds");
    }

    private void pollWorkflowStatus() {
        if (githubToken == null || githubRepo == null) return;
        
        try {
            String url = String.format("https://api.github.com/repos/%s/actions/runs?per_page=5", githubRepo);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode runs = mapper.readTree(response.body());
                processWorkflowRuns(runs.get("workflow_runs"));
            }
        } catch (Exception e) {
            System.err.println("Polling error: " + e.getMessage());
        }
    }

    private void processWorkflowRuns(JsonNode runs) {
        for (JsonNode run : runs) {
            String status = run.get("status").asText();
            String conclusion = run.has("conclusion") ? run.get("conclusion").asText() : "";
            String name = run.get("name").asText();
            String runId = run.get("id").asText();
            
            if ("completed".equals(status)) {
                String message = String.format("🔔 Workflow '%s' completed with status: %s (Run ID: %s)", 
                    name, conclusion, runId);
                System.err.println(message);
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
        serverInfo.put("name", "git-notify-mcp-server");
        serverInfo.put("version", "1.0.0");
        response.set("serverInfo", serverInfo);
        
        return response;
    }

    private JsonNode handleToolsList() {
        ArrayNode tools = mapper.createArrayNode();
        
        tools.add(createTool("health_check", "Check notification service status"));
        tools.add(createTool("get_notifications", "Get recent workflow notifications"));
        tools.add(createTool("webhook_status", "Check webhook server status"));
        
        ObjectNode response = mapper.createObjectNode();
        response.set("tools", tools);
        return response;
    }

    private JsonNode handleToolCall(JsonNode params) {
        String name = params.get("name").asText();
        
        return switch (name) {
            case "health_check" -> healthCheck();
            case "get_notifications" -> getNotifications();
            case "webhook_status" -> webhookStatus();
            default -> throw new RuntimeException("Unknown tool: " + name);
        };
    }

    private JsonNode healthCheck() {
        StringBuilder status = new StringBuilder();
        status.append("🔔 Git Notify MCP Server Status:\n\n");
        
        if (githubToken != null && githubRepo != null) {
            status.append("✅ GitHub configuration: OK\n");
            status.append("📍 Repository: ").append(githubRepo).append("\n");
        } else {
            status.append("❌ GitHub configuration: Missing GITHUB_TOKEN or GITHUB_REPO\n");
        }
        
        if (webhookServer != null) {
            status.append("✅ Webhook server: Running on port 8080\n");
        } else {
            status.append("❌ Webhook server: Not running\n");
        }
        
        status.append("✅ Polling: Active (every 30 seconds)\n");
        
        return createToolResponse("text", status.toString());
    }

    private JsonNode getNotifications() {
        return createToolResponse("text", "📋 Recent notifications are logged to stderr. Check server logs for workflow status updates.");
    }

    private JsonNode webhookStatus() {
        String status = webhookServer != null 
            ? "✅ Webhook server running on http://localhost:8080/webhook"
            : "❌ Webhook server not running";
        
        return createToolResponse("text", status);
    }

    private void shutdown() {
        if (webhookServer != null) {
            webhookServer.stop(0);
        }
        scheduler.shutdown();
    }

    private class WebhookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    JsonNode payload = mapper.readTree(body);
                    
                    if (payload.has("action") && payload.has("workflow_run")) {
                        JsonNode run = payload.get("workflow_run");
                        String action = payload.get("action").asText();
                        String name = run.get("name").asText();
                        String status = run.get("status").asText();
                        
                        String message = String.format("🔔 Webhook: Workflow '%s' %s (status: %s)", 
                            name, action, status);
                        System.err.println(message);
                    }
                    
                    String response = "OK";
                    exchange.sendResponseHeaders(200, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                } catch (Exception e) {
                    exchange.sendResponseHeaders(500, 0);
                    exchange.getResponseBody().close();
                }
            } else {
                exchange.sendResponseHeaders(405, 0);
                exchange.getResponseBody().close();
            }
        }
    }

    private ObjectNode createTool(String name, String description) {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");
        inputSchema.set("properties", mapper.createObjectNode());
        inputSchema.set("required", mapper.createArrayNode());
        tool.set("inputSchema", inputSchema);
        
        return tool;
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