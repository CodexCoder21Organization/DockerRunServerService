package dockerrunserver

import foundation.url.protocol.*
import foundation.url.resolver.UrlProtocol2
import java.io.File

/**
 * Loads the SJVM stdlib JAR for sandbox execution.
 */
private fun loadStdlibJar(): ByteArray {
    val resourceStream = object {}.javaClass.getResourceAsStream("/stdlib.jar")
    if (resourceStream != null) {
        println("Loading SJVM stdlib JAR from /stdlib.jar")
        return resourceStream.use { it.readBytes() }
    }
    throw IllegalStateException(
        "Cannot find SJVM stdlib JAR at /stdlib.jar. Ensure net.javadeploy.sjvm:avianStdlibHelper-jvm is available on the classpath."
    )
}

/**
 * Loads the pre-compiled client JAR bytes from one of:
 * 1. Classpath resource /client-impl.jar
 * 2. Environment variable CLIENT_JAR_PATH
 * 3. File ./client-impl.jar in current directory
 */
private fun loadClientJar(): ByteArray {
    val resourceStream = object {}.javaClass.getResourceAsStream("/client-impl.jar")
    if (resourceStream != null) {
        println("Loading client JAR from classpath resource")
        return resourceStream.use { it.readBytes() }
    }

    val envPath = System.getenv("CLIENT_JAR_PATH")
    if (envPath != null) {
        val file = File(envPath)
        if (file.exists()) {
            println("Loading client JAR from $envPath")
            return file.readBytes()
        }
    }

    val localFile = File("client-impl.jar")
    if (localFile.exists()) {
        println("Loading client JAR from ./client-impl.jar")
        return localFile.readBytes()
    }

    throw IllegalStateException(
        "Cannot find client implementation JAR. Ensure client-impl.jar is available via:\n" +
        "  - Classpath resource /client-impl.jar\n" +
        "  - Environment variable CLIENT_JAR_PATH\n" +
        "  - File ./client-impl.jar in current directory"
    )
}

/**
 * url://dockerrun/ Service Provider
 *
 * A URL service that provides remote Docker container management. Clients resolve
 * url://dockerrun/ and receive a DockerRunService implementation that makes
 * RPC calls back to this server for managing containers.
 *
 * The server:
 * - Starts Docker containers from image references
 * - Manages container lifecycle (pause, unpause, terminate)
 * - Auto-terminates containers after configurable durations
 *
 * When URL_BIND_DOMAIN is set (lazy-start mode): binds to TCP port for ContainerNursery
 * When URL_BIND_DOMAIN is not set (standalone mode): uses UrlResolver for P2P networking
 */
fun main() {
    println("=== url://dockerrun/ Service Provider ===")
    println()
    println("This service provides remote Docker container management via URL protocol.")
    println("Clients receive a DockerRunService implementation that proxies to this server.")
    println()

    val clientJarBytes = loadClientJar()
    BytecodeGenerator.clientJarBytes = clientJarBytes

    val stdlibJarBytes = loadStdlibJar()
    println("Loaded SJVM stdlib JAR: ${stdlibJarBytes.size} bytes")

    val dataDir = System.getenv("DOCKER_RUN_DATA_DIR")?.let { File(it) }
        ?: File("/root/docker-run-data")

    println("Storage location: ${dataDir.absolutePath}")
    if (!dataDir.exists()) {
        dataDir.mkdirs()
        println("Created storage directory")
    }

    val dockerRunService = dockerrun.embedded.DockerRunEmbedded(dataDir)

    val implementationJar = BytecodeGenerator.generateImplementationJar()
    val implementationClassName = BytecodeGenerator.getImplementationClassName()
    println("Generated implementation JAR: ${implementationJar.size} bytes")
    println("Implementation class: $implementationClassName")
    println()

    val rawBindDomain = System.getenv(UrlProtocol.ENV_BIND_DOMAIN)
    val port = System.getenv("PORT")
    val bindDomain = when {
        rawBindDomain == null -> null
        rawBindDomain.contains("\${PORT}") && port != null -> rawBindDomain.replace("\${PORT}", port)
        else -> rawBindDomain
    }

    if (bindDomain != null) {
        println("Running in lazy-start mode with URL_BIND_DOMAIN=$bindDomain")
        runUrlMode(bindDomain, implementationJar, implementationClassName, dockerRunService, stdlibJarBytes)
    } else {
        println("Running in standalone P2P mode")
        runP2pMode(implementationJar, implementationClassName, dockerRunService, stdlibJarBytes)
    }
}

private fun runUrlMode(
    bindDomain: String,
    jarBytes: ByteArray,
    implClassName: String,
    dockerRunService: dockerrun.embedded.DockerRunEmbedded,
    stdlibJarBytes: ByteArray
) {
    val protocol = UrlProtocol()
    val rpcHandler = DockerRunRpcHandler(dockerRunService, jarBytes, implClassName, stdlibJarBytes)

    val handler: suspend (Libp2pRpcProtocol.RpcRequest, EffectPropagator) -> Libp2pRpcProtocol.RpcResponse = { request, _ ->
        println("[DockerRunServer] URL request: method='${request.method}', params=${request.params.keys}")
        rpcHandler.handleRequest(request)
    }

    val serviceUrl = "url://$bindDomain/"
    println("Binding url://dockerrun/ service to $serviceUrl...")
    val binding = protocol.bind(serviceUrl, handler)

    println()
    println("Service bound successfully!")
    println("  Service identifier: ${binding.serviceIdentifier}")
    println("  Active: ${binding.isActive}")
    println()
    println("Clients can connect with:")
    println("  resolver.openConnection(\"url://dockerrun/\", dockerrun.api.DockerRunService::class)")
    println()
    println("Press Ctrl+C to stop.")

    Runtime.getRuntime().addShutdownHook(Thread {
        println()
        println("Shutting down URL service...")
        binding.stop()
        protocol.close()
        println("Goodbye!")
    })

    Thread.currentThread().join()
}

private fun runP2pMode(
    jarBytes: ByteArray,
    implClassName: String,
    dockerRunService: dockerrun.embedded.DockerRunEmbedded,
    stdlibJarBytes: ByteArray
) {
    val resolver = foundation.url.resolver.UrlResolver(UrlProtocol2())
    val rpcHandler = DockerRunRpcHandler(dockerRunService, jarBytes, implClassName, stdlibJarBytes)

    val handler = object : foundation.url.protocol.ServiceHandler {
        override suspend fun handleRequest(
            path: String,
            params: Map<String, Any?>,
            metadata: Map<String, String>
        ): Any? {
            println("[DockerRunServer] P2P request: path='$path', params=${params.keys}")
            return rpcHandler.handleP2pRequest(path, params)
        }

        override fun getImplementationJar(): ByteArray = jarBytes
        override fun getImplementationClassName(): String = implClassName
        override fun getStdlibJar(): ByteArray = stdlibJarBytes
        override fun supportsSandboxedExecution(): Boolean = true

        override fun onShutdown() {
            println("[DockerRunServer] P2P service shutting down")
        }
    }

    println("Registering url://dockerrun/ service with P2P network...")
    val registration = resolver.registerGlobalService(
        serviceUrl = "url://dockerrun/",
        handler = handler,
        config = foundation.url.protocol.ServiceRegistrationConfig(
            metadata = mapOf(
                "description" to "Remote Docker container management service",
                "type" to "rpc"
            ),
            reannounceIntervalMs = 5 * 60 * 1000
        )
    )

    println()
    println("Service registered successfully!")
    println("  Peer ID: ${registration.peerId}")
    println("  Multiaddresses: ${registration.multiaddresses.joinToString(", ")}")
    println("  Service URL: url://dockerrun/")
    println()
    println("Clients can connect with:")
    println("  resolver.openConnection(\"url://dockerrun/\", dockerrun.api.DockerRunService::class)")
    println()
    println("Press Ctrl+C to stop.")

    Runtime.getRuntime().addShutdownHook(Thread {
        println()
        println("Shutting down P2P service...")
        registration.unregister()
        resolver.close()
        println("Goodbye!")
    })

    Thread.currentThread().join()
}
