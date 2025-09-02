# KIRO MCP Servers

Collection of Model Context Protocol (MCP) servers for various integrations.

## Available Servers

### 1. S3 MCP Server (`s3-mcp`)
- **Purpose**: AWS S3 operations and management
- **Port**: N/A (MCP protocol only)
- **Features**: Bucket operations, file upload/download, S3 management
- **Run**: `cd s3-mcp && ./run-server.sh`

### 2. CI/CD MCP Server (`ci-cd-mcp`)
- **Purpose**: GitHub Actions pipeline management
- **Port**: N/A (MCP protocol only)
- **Features**: Pipeline management, build automation, deployment workflows
- **Run**: `cd ci-cd-mcp && ./run-server.sh`

### 3. Git Notification MCP Server (`git-notify-mcp`)
- **Purpose**: GitHub workflow status notifications
- **Port**: 8080 (webhook listener)
- **Features**: Webhook listener, workflow polling, real-time notifications
- **Run**: `cd git-notify-mcp && ./start-server-background.sh`
- **Environment**: Set `GITHUB_TOKEN` and `GITHUB_REPO`

### 4. Git Custom MCP Server (`git-custom-mcp`) ⭐ NEW
- **Purpose**: Git operations with webhook integration
- **Port**: 8081 (webhook endpoint)
- **Features**: Complete Git operations, webhook events, pipeline ID extraction
- **Run**: `cd git-custom-mcp && ./run-server.sh`
- **Standalone JAR**: `java -jar target/git-custom-mcp-server-1.0.0.jar`
- **Webhook**: `http://localhost:8081/webhook`

## Running Servers Standalone

### Prerequisites
- Java 17+
- Maven 3.6+
- Git (for git-custom-mcp)
- AWS CLI configured (for s3-mcp)

### Build All Servers
```bash
mvn clean compile
```

### Run Individual Servers

**S3 Server:**
```bash
cd s3-mcp
export AWS_REGION=your-region
./run-server.sh
```

**CI/CD Server:**
```bash
cd ci-cd-mcp
export GITHUB_TOKEN=your_token
./run-server.sh
```

**Git Notify Server (Background):**
```bash
cd git-notify-mcp
export GITHUB_TOKEN=your_token
export GITHUB_REPO=owner/repo
./start-server-background.sh
```

**Git Custom Server:**
```bash
cd git-custom-mcp
./run-server.sh
# OR run standalone JAR
mvn package
java -jar target/git-custom-mcp-server-1.0.0.jar
# Webhook available at http://localhost:8081/webhook
```

## Git Custom MCP Server Tools

- `git_status`: Get repository status
- `git_log`: View commit history
- `git_branch`: List/create branches
- `git_add`: Stage files
- `git_commit`: Commit changes
- `git_push`: Push to remote
- `git_pull`: Pull from remote
- `git_diff`: Show differences
- `get_repo_info`: Repository information
- `get_pipeline_info`: Project/Pipeline IDs
- `webhook_status`: Check webhook server

## Usage Examples

**Git Operations:**
- "Show git status"
- "Get last 5 commits"
- "Create branch feature-xyz"
- "Add all files and commit with message 'fix bug'"
- "Push to origin main"

**CI/CD Operations:**
- "List workflows in owner/repo"
- "Trigger workflow build.yml in owner/repo"
- "Get status of run 12345 in owner/repo"

**S3 Operations:**
- "List my S3 buckets"
- "List files in my-bucket"
- "Get content of file.txt from my-bucket"

## Webhook Configuration

**For Git Custom Server:**
1. Go to GitHub Repository Settings → Webhooks
2. Add webhook: `http://your-server:8081/webhook`
3. Select "Push" events
4. Server will display git status and extract pipeline info
5. **Test endpoint**: `curl http://your-server:8081/webhook` returns "Welcome to webhook of mcp server"

**For Git Notify Server:**
1. Add webhook: `http://your-server:8080/webhook`
2. Select "Workflow runs" events
3. Server logs notifications to console

## EC2 Deployment

**Security Group Rules:**
- Port 8080: Git Notify Server
- Port 8081: Git Custom Server
- Port 22: SSH Access

**Run on EC2:**
```bash
# Upload JAR to EC2
scp target/git-custom-mcp-server-1.0.0.jar ec2-user@your-ec2:/home/ec2-user/

# Run on EC2
ssh ec2-user@your-ec2
nohup java -jar git-custom-mcp-server-1.0.0.jar > server.log 2>&1 &

# Test webhook
curl http://EC2_PUBLIC_IP:8081/webhook
```

## Configuration

Each server has its own configuration requirements. Check individual README files for specific setup instructions.