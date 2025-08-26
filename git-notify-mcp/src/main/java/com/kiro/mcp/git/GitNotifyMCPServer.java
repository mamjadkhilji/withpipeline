package com.kiro.mcp.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GitNotifyMCPServer extends HttpServlet {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private GitHub github;
    private String repoName;
    
    public static void main(String[] args) throws Exception {
        GitNotifyMCPServer mcpServer = new GitNotifyMCPServer();
        mcpServer.start();
    }
    
    public void start() throws Exception {
        // Initialize GitHub client
        String token = System.getenv("GITHUB_TOKEN");
        this.repoName = System.getenv("GITHUB_REPO");
        
        if (token != null) {
            this.github = new GitHubBuilder().withOAuthToken(token).build();
        }
        
        // Start webhook server
        Server server = new Server(8080);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(this), "/webhook");
        server.setHandler(context);
        
        server.start();
        System.out.println("Git Notify MCP Server started on port 8080");
        
        // Start polling only if token and repo are configured
        if (github != null && repoName != null && token != null && !token.isEmpty()) {
            startPolling();
        } else {
            System.out.println("GitHub polling disabled - no token configured. Webhook-only mode.");
        }
        
        server.join();
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String event = req.getHeader("X-GitHub-Event");
        
        if ("workflow_run".equals(event)) {
            JsonNode payload = mapper.readTree(req.getInputStream());
            handleWorkflowRun(payload);
        }
        
        resp.setStatus(200);
    }
    
    private void handleWorkflowRun(JsonNode payload) {
        JsonNode workflowRun = payload.get("workflow_run");
        String status = workflowRun.get("status").asText();
        String conclusion = workflowRun.has("conclusion") ? workflowRun.get("conclusion").asText() : null;
        String workflowName = workflowRun.get("name").asText();
        String htmlUrl = workflowRun.get("html_url").asText();
        
        if ("completed".equals(status) && conclusion != null) {
            notifyClient(workflowName, conclusion, htmlUrl);
        }
    }
    
    private void startPolling() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                var repo = github.getRepository(repoName);
                var workflows = repo.listWorkflows().toList();
                
                for (var workflow : workflows) {
                    var runs = workflow.listRuns().withPageSize(5).toList();
                    for (var run : runs) {
                        if ("completed".equals(run.getStatus()) && run.getConclusion() != null) {
                            notifyClient(workflow.getName(), run.getConclusion().toString(), run.getHtmlUrl().toString());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Polling error: " + e.getMessage());
            }
        }, 0, 30, TimeUnit.SECONDS);
    }
    
    private void notifyClient(String workflow, String conclusion, String url) {
        CompletableFuture.runAsync(() -> {
            try {
                String message = String.format("Workflow '%s' %s: %s", workflow, conclusion, url);
                System.out.println("NOTIFICATION: " + message);
                
                // Send to MCP client
                var notification = mapper.createObjectNode();
                notification.put("method", "notifications/message");
                var params = notification.putObject("params");
                params.put("level", "success".equals(conclusion) ? "info" : "error");
                params.put("message", message);
                
                System.out.println(mapper.writeValueAsString(notification));
            } catch (Exception e) {
                System.err.println("Notification error: " + e.getMessage());
            }
        });
    }
}