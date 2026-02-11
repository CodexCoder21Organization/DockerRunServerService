# DockerRunServerService

A URL Protocol service that provides remote Docker container management via `url://dockerrun/`. Clients connect and receive a proxied `DockerRunService` implementation that makes RPC calls back to this server.

## Overview

DockerRunServerService registers as a `url://dockerrun/` service and uses [DockerRunEmbedded](https://github.com/CodexCoder21Organization/DockerRunEmbedded) internally to manage Docker containers. It supports both ContainerNursery lazy-start and standalone P2P networking modes.

When a client connects, the server provides:
1. A pre-compiled client implementation JAR for SJVM sandbox execution
2. An SJVM stdlib JAR for the sandbox runtime
3. RPC-based request handling for all container operations

## Features

- Start containers from any Docker image reference with environment variables
- Pause and unpause running containers
- Terminate containers manually or via auto-termination
- Dual-mode: ContainerNursery lazy-start and standalone P2P
- SJVM sandboxed client execution

## Building

```bash
# Build the fat JAR (server + bundled client)
scripts/build.bash dockerrunserver.buildFatJar

# Build just the Maven artifact
scripts/build.bash dockerrunserver.buildMaven

# Run the server
java -jar docker-run-server-service.jar
```

### Running with ContainerNursery

```bash
export URL_BIND_DOMAIN="dockerrun.example.com:\${PORT}"
java -jar docker-run-server-service.jar
```

### Running Standalone

```bash
java -jar docker-run-server-service.jar
```

## Client Connection

```kotlin
import dockerrun.api.DockerRunService
import foundation.url.resolver.UrlResolver

val resolver = UrlResolver(UrlProtocol2())
val service = resolver.openSandboxedConnection("url://dockerrun/", DockerRunService::class).proxy

val container = service.startContainer(
    imageReference = "docker.io/library/nginx:latest",
    environmentVariables = mapOf("PORT" to "8080"),
    autoTerminateSeconds = 3600
)
```

## Environment Variables

- `URL_BIND_DOMAIN` - Domain for ContainerNursery lazy-start mode (enables URL mode)
- `PORT` - Port number (substituted into `URL_BIND_DOMAIN` via `\${PORT}`)
- `DOCKER_RUN_DATA_DIR` - Data storage directory (default: `/root/docker-run-data`)

## Maven Coordinates

```
dockerrunserver:docker-run-server-service:0.0.1
```

## Prerequisites

- Docker daemon must be running on the server
- Java 11+ runtime
