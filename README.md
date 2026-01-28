# Distributed File System (DFS)

A full-stack distributed file system implementation with a Java/Spring Boot backend and a modern Next.js frontend. This project demonstrates core HDFS concepts including NameNodes, DataNodes, and a Gateway API.

## Project Overview

This project implements a simplified version of the Hadoop Distributed File System (HDFS) with the following components:

- **NameNodes** (3 instances): Manage the file system namespace and maintain the file system tree and metadata for all files/directories in the system
- **DataNodes** (5 instances): Perform block creation, deletion, and replication based on instructions from the NameNodes
- **Gateway** (2 instances): Provides REST API endpoints for client interactions with the file system
- **Frontend**: Modern web UI for browsing and managing files in the distributed system

## Technology Stack

### Backend
- **Language**: Java 21
- **Framework**: Spring Boot 3.3.4
- **Build Tool**: Maven
- **Architecture**: Microservices with multiple modules

### Frontend
- **Framework**: Next.js with TypeScript
- **UI Components**: Radix UI
- **Styling**: TailwindCSS
- **Package Manager**: pnpm

## Project Structure

```
DC_PROJECT/
├── backend/                    # Java backend services
│   ├── shared/                # Shared utilities and models
│   ├── namenode/              # NameNode implementation
│   ├── datanode/              # DataNode implementation
│   ├── gateway/               # Gateway API implementation
│   └── pom.xml               # Parent Maven configuration
├── hdfs-mini-project/         # Next.js frontend application
│   ├── app/                   # Next.js app directory
│   │   ├── api/              # API routes (upload, download, list-files)
│   │   ├── page.tsx          # Main application page
│   │   └── layout.tsx        # Root layout
│   ├── components/            # Reusable React components
│   │   ├── ui/               # Shadcn UI components
│   │   ├── file-table.tsx    # File listing component
│   │   ├── upload-area.tsx   # File upload component
│   │   └── ...
│   └── package.json          # Frontend dependencies
├── cluster.json              # Cluster configuration
├── metadata.json             # Project metadata
└── scripts/                  # Startup scripts
    ├── startNameNodes.ps1    # Script to start NameNodes
    └── startGateWay.ps1      # Script to start Gateway
```

## Configuration

### Cluster Configuration (`cluster.json`)

The cluster is configured with:

| Component | Count | Port Range | Purpose |
|-----------|-------|-----------|---------|
| **NameNodes** | 3 | 9001-9003 | Manage file system namespace |
| **DataNodes** | 5 | 9101-9105 (REST), 10001-10005 (Block) | Store file blocks |
| **Gateways** | 2 | 9201-9202 | Client API endpoints |

### Default Settings

- **Replication Factor**: 3
- **Block Size**: 128 MB
- **Heartbeat Interval**: 3 seconds
- **Clock Sync Interval**: 30 seconds

## Getting Started

### Prerequisites

- **Java 21** or higher
- **Maven 3.6+** for building backend
- **Node.js 18+** and **pnpm** for frontend
- **PowerShell** (for Windows startup scripts)

### Backend Setup

1. Navigate to the backend directory:
   ```bash
   cd backend
   ```

2. Build all modules:
   ```bash
   mvn clean install
   ```

3. Start individual components using the provided PowerShell scripts:
   ```powershell
   # Start all NameNodes
   ..\scripts\startNameNodes.ps1

   # Start Gateway
   ..\scripts\startGateWay.ps1
   ```

### Frontend Setup

1. Navigate to the frontend directory:
   ```bash
   cd hdfs-mini-project
   ```

2. Install dependencies:
   ```bash
   pnpm install
   ```

3. Start the development server:
   ```bash
   pnpm dev
   ```

   The application will be available at `http://localhost:3000`

4. For production build:
   ```bash
   pnpm build
   pnpm start
   ```

## API Endpoints

The Gateway provides the following REST API endpoints:

- **List Files**: `GET /api/list-files`
- **Upload File**: `POST /api/upload`
- **Download File**: `GET /api/download/:filename`

## Features

### Frontend UI
- 📁 File browser with directory tree navigation
- 📤 Drag-and-drop file upload with progress tracking
- 📥 File download functionality
- 🎨 Dark/Light theme support
- 📱 Responsive design
- 🔔 Toast notifications for user feedback

### Backend Services
- ✓ Distributed file storage across multiple DataNodes
- ✓ Automatic block replication
- ✓ NameNode failover support (multiple NameNodes)
- ✓ Heartbeat monitoring between NameNodes and DataNodes
- ✓ REST API for file operations

## Development

### Building Backend Modules

```bash
cd backend

# Build entire project
mvn clean install

# Build specific module
mvn clean install -DskipTests

# Run tests
mvn test
```

### Frontend Development

```bash
cd hdfs-mini-project

# Start dev server
pnpm dev

# Run linter
pnpm lint

# Build for production
pnpm build
```

## Project Modules

### Shared Module
Common utilities, data models, and configurations shared across all backend services.

### NameNode
- Manages file system namespace
- Maintains file system hierarchy
- Controls file access permissions
- Does not directly store user data

### DataNode
- Stores the actual data blocks
- Performs block creation, deletion, and replication
- Sends heartbeats to NameNode
- Performs block report

### Gateway
- REST API interface for clients
- Coordinates between NameNodes and DataNodes
- Handles file upload/download operations
- Load balancing for multiple instances

## Configuration Details

Edit `cluster.json` to modify:
- Number of NameNodes, DataNodes, and Gateways
- Port assignments
- Replication factor
- Block size
- Heartbeat and sync intervals

## Troubleshooting

### Backend
- Ensure Java 21 is installed: `java -version`
- Verify Maven is available: `mvn -version`
- Check port availability before starting services

### Frontend
- Ensure Node.js 18+ is installed: `node -version`
- Clear node_modules and reinstall if dependency issues: `rm -r node_modules && pnpm install`
- Check that the backend services are running before using the UI

## License

This is an educational project demonstrating distributed file system concepts.

## Contact & Support

For issues or questions about this project, please refer to the project documentation or contact the development team.

---

**Note**: This is a mini/educational project demonstrating HDFS concepts and should not be used in production environments.
