package com.mcp.s3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

public class S3McpServer {
    private final S3Client s3Client;
    private final ObjectMapper mapper;

    public S3McpServer() {
        S3Client client;
        try {
            // Try to create S3 client with default configuration
            client = S3Client.create();
        } catch (Exception e) {
            // If default fails, try with us-east-1 as fallback
            System.err.println("Warning: Using default region us-east-1. Set AWS_REGION environment variable for your preferred region.");
            client = S3Client.builder()
                .region(Region.US_EAST_1)
                .build();
        }
        this.s3Client = client;
        this.mapper = new ObjectMapper();
        
        // Log initialization status
        System.err.println("S3 MCP Server initialized successfully");
    }

    public static void main(String[] args) {
        new S3McpServer().run();
    }

    private void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonNode request = mapper.readTree(line);
                    JsonNode response = handleRequest(request);
                    System.out.println(mapper.writeValueAsString(response));
                    System.out.flush();
                } catch (Exception e) {
                    System.err.println("Error processing request: " + e.getMessage());
                    // Send error response
                    ObjectNode errorResponse = createErrorResponse(null, -32603, "Parse error: " + e.getMessage());
                    System.out.println(mapper.writeValueAsString(errorResponse));
                    System.out.flush();
                }
            }
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
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
        serverInfo.put("name", "s3-mcp-server");
        serverInfo.put("version", "1.0.0");
        response.set("serverInfo", serverInfo);
        
        return response;
    }

    private JsonNode handleToolsList() {
        ArrayNode tools = mapper.createArrayNode();
        
        tools.add(createTool("health_check", "Check AWS credentials and S3 connectivity"));
        tools.add(createTool("list_buckets", "List all S3 buckets"));
        tools.add(createTool("list_objects", "List objects in a bucket", 
            createParam("bucket", "string", "Bucket name", true)));
        tools.add(createTool("get_object", "Get object content", 
            createParam("bucket", "string", "Bucket name", true),
            createParam("key", "string", "Object key", true)));
        tools.add(createTool("put_object", "Upload object", 
            createParam("bucket", "string", "Bucket name", true),
            createParam("key", "string", "Object key", true),
            createParam("content", "string", "Object content", true)));
        tools.add(createTool("delete_object", "Delete object", 
            createParam("bucket", "string", "Bucket name", true),
            createParam("key", "string", "Object key", true)));
        
        ObjectNode response = mapper.createObjectNode();
        response.set("tools", tools);
        return response;
    }

    private JsonNode handleToolCall(JsonNode params) {
        String name = params.get("name").asText();
        JsonNode arguments = params.get("arguments");
        
        return switch (name) {
            case "health_check" -> healthCheck();
            case "list_buckets" -> listBuckets();
            case "list_objects" -> listObjects(arguments.get("bucket").asText());
            case "get_object" -> getObject(arguments.get("bucket").asText(), arguments.get("key").asText());
            case "put_object" -> putObject(arguments.get("bucket").asText(), 
                arguments.get("key").asText(), arguments.get("content").asText());
            case "delete_object" -> deleteObject(arguments.get("bucket").asText(), arguments.get("key").asText());
            default -> throw new RuntimeException("Unknown tool: " + name);
        };
    }

    private JsonNode healthCheck() {
        try {
            // Try to list buckets as a connectivity test
            s3Client.listBuckets();
            return createToolResponse("text", "✅ AWS S3 connectivity successful! Credentials are properly configured.");
        } catch (Exception e) {
            String errorMsg = "❌ AWS S3 connectivity failed. Please check your AWS credentials and configuration.\n\n" +
                "Error: " + e.getMessage() + "\n\n" +
                "To fix this:\n" +
                "1. Configure AWS credentials using 'aws configure'\n" +
                "2. Or set environment variables: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY\n" +
                "3. Or use IAM roles if running on EC2\n" +
                "4. Ensure AWS_REGION is set (currently: " + System.getenv("AWS_REGION") + ")";
            return createToolResponse("text", errorMsg);
        }
    }

    private JsonNode listBuckets() {
        try {
            List<Bucket> buckets = s3Client.listBuckets().buckets();
            ArrayNode content = mapper.createArrayNode();
            
            for (Bucket bucket : buckets) {
                ObjectNode bucketNode = mapper.createObjectNode();
                bucketNode.put("name", bucket.name());
                bucketNode.put("creationDate", bucket.creationDate().toString());
                content.add(bucketNode);
            }
            
            return createToolResponse("text", mapper.writeValueAsString(content));
        } catch (Exception e) {
            String errorMsg = "Failed to list S3 buckets. Please ensure AWS credentials are configured. Error: " + e.getMessage();
            return createToolResponse("text", errorMsg);
        }
    }

    private JsonNode listObjects(String bucketName) {
        try {
            ListObjectsV2Response response = s3Client.listObjectsV2(
                ListObjectsV2Request.builder().bucket(bucketName).build());
            
            ArrayNode content = mapper.createArrayNode();
            for (S3Object obj : response.contents()) {
                ObjectNode objNode = mapper.createObjectNode();
                objNode.put("key", obj.key());
                objNode.put("size", obj.size());
                objNode.put("lastModified", obj.lastModified().toString());
                content.add(objNode);
            }
            
            return createToolResponse("text", mapper.writeValueAsString(content));
        } catch (Exception e) {
            String errorMsg = "Failed to list objects in bucket '" + bucketName + "'. Error: " + e.getMessage();
            return createToolResponse("text", errorMsg);
        }
    }

    private JsonNode getObject(String bucketName, String key) {
        try (var responseStream = s3Client.getObject(
            GetObjectRequest.builder().bucket(bucketName).key(key).build())) {
            
            String content = new String(responseStream.readAllBytes());
            return createToolResponse("text", content);
        } catch (Exception e) {
            String errorMsg = "Failed to get object '" + key + "' from bucket '" + bucketName + "'. Error: " + e.getMessage();
            return createToolResponse("text", errorMsg);
        }
    }

    private JsonNode putObject(String bucketName, String key, String content) {
        try {
            s3Client.putObject(
                PutObjectRequest.builder().bucket(bucketName).key(key).build(),
                software.amazon.awssdk.core.sync.RequestBody.fromString(content));
            
            return createToolResponse("text", "Object uploaded successfully");
        } catch (Exception e) {
            String errorMsg = "Failed to upload object '" + key + "' to bucket '" + bucketName + "'. Error: " + e.getMessage();
            return createToolResponse("text", errorMsg);
        }
    }

    private JsonNode deleteObject(String bucketName, String key) {
        try {
            s3Client.deleteObject(
                DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
            
            return createToolResponse("text", "Object deleted successfully");
        } catch (Exception e) {
            String errorMsg = "Failed to delete object '" + key + "' from bucket '" + bucketName + "'. Error: " + e.getMessage();
            return createToolResponse("text", errorMsg);
        }
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