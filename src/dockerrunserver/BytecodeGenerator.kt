package dockerrunserver

/**
 * Provides the client-side implementation JAR for SJVM execution.
 *
 * The client implementation consists of pre-compiled Java classes:
 * - `dockerrunserver/DockerRunServiceClientImpl` - service implementation with RPC calls
 * - `dockerrun/api/DockerContainerImpl` - container implementation with RPC-backed property accessors
 *
 * These classes are compiled from Java source code in src-client-java/
 * and loaded at runtime. ServiceBridge.rpc() is an SJVM intrinsic that gets
 * intercepted and routed to the host's RPC handler.
 */
object BytecodeGenerator {

    private const val SERVICE_IMPL_CLASS = "dockerrunserver/DockerRunServiceClientImpl"

    /**
     * The pre-compiled client JAR bytes. Must be set before calling
     * generateImplementationJar().
     */
    var clientJarBytes: ByteArray? = null

    fun getImplementationClassName(): String = SERVICE_IMPL_CLASS

    /**
     * Returns the client implementation JAR bytes.
     */
    fun generateImplementationJar(): ByteArray {
        return clientJarBytes
            ?: throw IllegalStateException(
                "BytecodeGenerator.clientJarBytes must be set before calling generateImplementationJar(). " +
                "Set it to the bytes of the pre-compiled client JAR from src-client-java/."
            )
    }
}
