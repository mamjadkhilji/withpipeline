# MCP Servers

Model Context Protocol (MCP) servers for various operations built with Java.

## S3 MCP Server

AWS S3 operations server.

### Features
- List S3 buckets
- List objects in a bucket
- Get object content
- Upload objects
- Delete objects

### Setup
1. Configure AWS credentials: `aws configure`
2. Build: `mvn compile`
3. Run: `mvn exec:java -Dexec.mainClass="com.mcp.s3.S3McpServer"`

## CI/CD MCP Server

GitHub Actions pipeline management server.

### Features
- List workflows in repository
- Trigger workflow runs
- Get workflow run history
- Check run status and artifacts
- Cancel running workflows

### Setup
1. Get GitHub token from Settings → Developer settings → Personal access tokens
2. Set environment variable: `export GITHUB_TOKEN=your_token`
3. Build: `mvn compile`
4. Run: `mvn exec:java -Dexec.mainClass="com.mcp.cicd.CicdMcpServer"`

### Tools
- `health_check`: Check GitHub API connectivity
- `list_workflows`: List workflows in repository
- `trigger_workflow`: Trigger workflow run
- `get_workflow_runs`: Get workflow run history
- `get_run_status`: Get specific run status
- `get_run_artifacts`: Get run artifacts
- `cancel_workflow_run`: Cancel workflow run

## Usage Examples

**List workflows:**
"List workflows in owner/repo"

**Trigger build:**
"Trigger workflow build.yml in owner/repo on main branch"

**Check status:**
"Get status of run 12345 in owner/repo"