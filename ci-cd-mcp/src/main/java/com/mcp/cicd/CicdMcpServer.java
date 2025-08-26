package com.mcp.cicd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class CicdMcpServer {
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String githubToken;
    private final String baseUrl = "https://api.github.com";

    public CicdMcpServer() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.mapper = new ObjectMapper();
        this.githubToken = System.getenv("GITHUB_TOKEN");
        System.err.println("CI/CD MCP Server initialized");
    }

    public static void main(String[] args) {
        new CicdMcpServer().run();
    }

    private void run() {
        System.err.println("Starting MCP server main loop...");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.err.println("Received request: " + line);
                try {
                    JsonNode request = mapper.readTree(line);
                    JsonNode response = handleRequest(request);
                    
                    // Only send response if it's not null (notifications don't need responses)
                    if (response != null) {
                        String responseStr = mapper.writeValueAsString(response);
                        System.err.println("Sending response: " + responseStr);
                        System.out.println(responseStr);
                        System.out.flush();
                    }
                } catch (Exception e) {
                    System.err.println("Parse error: " + e.getMessage());
                    e.printStackTrace();
                    ObjectNode errorResponse = createErrorResponse(null, -32700, "Parse error: " + e.getMessage());
                    System.out.println(mapper.writeValueAsString(errorResponse));
                    System.out.flush();
                }
            }
            System.err.println("Input stream closed, exiting...");
        } catch (Exception e) {
            System.err.println("Fatal error in main loop: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private JsonNode handleRequest(JsonNode request) {
        String method = request.get("method").asText();
        JsonNode id = request.get("id");
        
        try {
            JsonNode result = switch (method) {
                case "initialize" -> handleInitialize();
                case "initialized" -> handleInitialized();
                case "tools/list" -> handleToolsList();
                case "tools/call" -> handleToolCall(request.get("params"));
                default -> throw new RuntimeException("Unknown method: " + method);
            };
            
            return result != null ? createSuccessResponse(id, result) : null;
        } catch (Exception e) {
            System.err.println("Error handling request: " + e.getMessage());
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
        serverInfo.put("name", "cicd-mcp-server");
        serverInfo.put("version", "1.0.0");
        response.set("serverInfo", serverInfo);
        
        System.err.println("MCP Server initialized successfully");
        return response;
    }

    private JsonNode handleInitialized() {
        // This is a notification, no response needed
        System.err.println("MCP Server initialization completed");
        return null;
    }

    private JsonNode handleToolsList() {
        ArrayNode tools = mapper.createArrayNode();
        
        tools.add(createTool("health_check", "Check GitHub API connectivity"));
        tools.add(createTool("list_workflows", "List workflows in repository",
            createParam("owner", "string", "Repository owner", true),
            createParam("repo", "string", "Repository name", true)));
        tools.add(createTool("trigger_workflow", "Trigger workflow run",
            createParam("owner", "string", "Repository owner", true),
            createParam("repo", "string", "Repository name", true),
            createParam("workflow_id", "string", "Workflow ID or filename", true),
            createParam("ref", "string", "Git reference (branch/tag)", false)));
        tools.add(createTool("get_workflow_runs", "Get workflow run history",
            createParam("owner", "string", "Repository owner", true),
            createParam("repo", "string", "Repository name", true),
            createParam("workflow_id", "string", "Workflow ID (optional)", false)));
        tools.add(createTool("get_run_status", "Get specific run status",
            createParam("owner", "string", "Repository owner", true),
            createParam("repo", "string", "Repository name", true),
            createParam("run_id", "string", "Run ID", true)));
        tools.add(createTool("get_run_artifacts", "Get run artifacts",
            createParam("owner", "string", "Repository owner", true),
            createParam("repo", "string", "Repository name", true),
            createParam("run_id", "string", "Run ID", true)));
        tools.add(createTool("cancel_workflow_run", "Cancel workflow run",
            createParam("owner", "string", "Repository owner", true),
            createParam("repo", "string", "Repository name", true),
            createParam("run_id", "string", "Run ID", true)));
        
        ObjectNode response = mapper.createObjectNode();
        response.set("tools", tools);
        return response;
    }

    private JsonNode handleToolCall(JsonNode params) {
        String name = params.get("name").asText();
        JsonNode arguments = params.get("arguments");
        
        return switch (name) {
            case "health_check" -> healthCheck();
            case "list_workflows" -> listWorkflows(
                arguments.get("owner").asText(),
                arguments.get("repo").asText());
            case "trigger_workflow" -> triggerWorkflow(
                arguments.get("owner").asText(),
                arguments.get("repo").asText(),
                arguments.get("workflow_id").asText(),
                arguments.has("ref") ? arguments.get("ref").asText() : "main");
            case "get_workflow_runs" -> getWorkflowRuns(
                arguments.get("owner").asText(),
                arguments.get("repo").asText(),
                arguments.has("workflow_id") ? arguments.get("workflow_id").asText() : null);
            case "get_run_status" -> getRunStatus(
                arguments.get("owner").asText(),
                arguments.get("repo").asText(),
                arguments.get("run_id").asText());
            case "get_run_artifacts" -> getRunArtifacts(
                arguments.get("owner").asText(),
                arguments.get("repo").asText(),
                arguments.get("run_id").asText());
            case "cancel_workflow_run" -> cancelWorkflowRun(
                arguments.get("owner").asText(),
                arguments.get("repo").asText(),
                arguments.get("run_id").asText());
            default -> throw new RuntimeException("Unknown tool: " + name);
        };
    }

    private JsonNode healthCheck() {
        if (githubToken == null || githubToken.isEmpty()) {
            return createToolResponse("text", "❌ GitHub token not configured. Set GITHUB_TOKEN environment variable.");
        }
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/user"))
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode user = mapper.readTree(response.body());
                return createToolResponse("text", "✅ GitHub API connected successfully! User: " + user.get("login").asText());
            } else {
                return createToolResponse("text", "❌ GitHub API authentication failed. Status: " + response.statusCode());
            }
        } catch (Exception e) {
            return createToolResponse("text", "❌ GitHub API connection failed: " + e.getMessage());
        }
    }

    private JsonNode listWorkflows(String owner, String repo) {
        try {
            String url = String.format("%s/repos/%s/%s/actions/workflows", baseUrl, owner, repo);
            JsonNode response = makeGitHubRequest(url);
            
            ArrayNode workflows = mapper.createArrayNode();
            for (JsonNode workflow : response.get("workflows")) {
                ObjectNode wf = mapper.createObjectNode();
                wf.put("id", workflow.get("id").asText());
                wf.put("name", workflow.get("name").asText());
                wf.put("path", workflow.get("path").asText());
                wf.put("state", workflow.get("state").asText());
                workflows.add(wf);
            }
            
            return createToolResponse("text", mapper.writeValueAsString(workflows));
        } catch (Exception e) {
            return createToolResponse("text", "Failed to list workflows: " + e.getMessage());
        }
    }

    private JsonNode triggerWorkflow(String owner, String repo, String workflowId, String ref) {
        try {
            String url = String.format("%s/repos/%s/%s/actions/workflows/%s/dispatches", baseUrl, owner, repo, workflowId);
            
            ObjectNode payload = mapper.createObjectNode();
            payload.put("ref", ref);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 204) {
                return createToolResponse("text", "✅ Workflow triggered successfully");
            } else {
                return createToolResponse("text", "Failed to trigger workflow. Status: " + response.statusCode());
            }
        } catch (Exception e) {
            return createToolResponse("text", "Failed to trigger workflow: " + e.getMessage());
        }
    }

    private JsonNode getWorkflowRuns(String owner, String repo, String workflowId) {
        try {
            String url = workflowId != null 
                ? String.format("%s/repos/%s/%s/actions/workflows/%s/runs", baseUrl, owner, repo, workflowId)
                : String.format("%s/repos/%s/%s/actions/runs", baseUrl, owner, repo);
            
            JsonNode response = makeGitHubRequest(url);
            
            ArrayNode runs = mapper.createArrayNode();
            for (JsonNode run : response.get("workflow_runs")) {
                ObjectNode runObj = mapper.createObjectNode();
                runObj.put("id", run.get("id").asText());
                runObj.put("name", run.get("name").asText());
                runObj.put("status", run.get("status").asText());
                runObj.put("conclusion", run.has("conclusion") ? run.get("conclusion").asText() : "");
                runObj.put("created_at", run.get("created_at").asText());
                runObj.put("html_url", run.get("html_url").asText());
                runs.add(runObj);
            }
            
            return createToolResponse("text", mapper.writeValueAsString(runs));
        } catch (Exception e) {
            return createToolResponse("text", "Failed to get workflow runs: " + e.getMessage());
        }
    }

    private JsonNode getRunStatus(String owner, String repo, String runId) {
        try {
            String url = String.format("%s/repos/%s/%s/actions/runs/%s", baseUrl, owner, repo, runId);
            JsonNode run = makeGitHubRequest(url);
            
            ObjectNode status = mapper.createObjectNode();
            status.put("id", run.get("id").asText());
            status.put("name", run.get("name").asText());
            status.put("status", run.get("status").asText());
            status.put("conclusion", run.has("conclusion") ? run.get("conclusion").asText() : "");
            status.put("created_at", run.get("created_at").asText());
            status.put("updated_at", run.get("updated_at").asText());
            status.put("html_url", run.get("html_url").asText());
            
            return createToolResponse("text", mapper.writeValueAsString(status));
        } catch (Exception e) {
            return createToolResponse("text", "Failed to get run status: " + e.getMessage());
        }
    }

    private JsonNode getRunArtifacts(String owner, String repo, String runId) {
        try {
            String url = String.format("%s/repos/%s/%s/actions/runs/%s/artifacts", baseUrl, owner, repo, runId);
            JsonNode response = makeGitHubRequest(url);
            
            ArrayNode artifacts = mapper.createArrayNode();
            for (JsonNode artifact : response.get("artifacts")) {
                ObjectNode art = mapper.createObjectNode();
                art.put("id", artifact.get("id").asText());
                art.put("name", artifact.get("name").asText());
                art.put("size_in_bytes", artifact.get("size_in_bytes").asLong());
                art.put("created_at", artifact.get("created_at").asText());
                art.put("download_url", artifact.get("archive_download_url").asText());
                artifacts.add(art);
            }
            
            return createToolResponse("text", mapper.writeValueAsString(artifacts));
        } catch (Exception e) {
            return createToolResponse("text", "Failed to get artifacts: " + e.getMessage());
        }
    }

    private JsonNode cancelWorkflowRun(String owner, String repo, String runId) {
        try {
            String url = String.format("%s/repos/%s/%s/actions/runs/%s/cancel", baseUrl, owner, repo, runId);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + githubToken)
                .header("Accept", "application/vnd.github.v3+json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 202) {
                return createToolResponse("text", "✅ Workflow run cancelled successfully");
            } else {
                return createToolResponse("text", "Failed to cancel run. Status: " + response.statusCode());
            }
        } catch (Exception e) {
            return createToolResponse("text", "Failed to cancel run: " + e.getMessage());
        }
    }

    private JsonNode makeGitHubRequest(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + githubToken)
            .header("Accept", "application/vnd.github.v3+json")
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("GitHub API error: " + response.statusCode());
        }
        
        return mapper.readTree(response.body());
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
        if (id != null && !id.isNull()) {
            response.set("id", id);
        }
        response.set("result", result);
        return response;
    }

    private ObjectNode createErrorResponse(JsonNode id, int code, String message) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null && !id.isNull()) {
            response.set("id", id);
        } else {
            response.putNull("id");
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