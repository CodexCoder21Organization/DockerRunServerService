@KotlinBuildScript("https://tools.kotlin.build/")
@file:WithArtifact("kompile:build-kotlin-jvm:0.0.1")
package dockerrunserver

import build.kotlin.withartifact.WithArtifact
import java.io.File
import build.kotlin.jvm.*
import build.kotlin.annotations.MavenArtifactCoordinates

val dependencies = resolveDependencies(
    // DockerRunApi interfaces and Embedded implementation
    MavenPrebuilt("dockerrun.api:docker-run-api:0.0.1"),
    MavenPrebuilt("dockerrun.embedded:docker-run-embedded:0.0.1"),
    // UrlResolver and UrlProtocol
    MavenPrebuilt("foundation.url:resolver:0.0.362"),
    MavenPrebuilt("foundation.url:protocol:0.0.255"),
    // SJVM for stdlib JAR (needed for bytecode responses)
    MavenPrebuilt("net.javadeploy.sjvm:avianStdlibHelper-jvm:0.0.26"),
    // Clock abstraction (required by UrlProtocol)
    MavenPrebuilt("community.kotlin.clocks.simple:community-kotlin-clocks-simple:0.0.1"),
    // libp2p dependencies
    MavenPrebuilt("io.libp2p:jvm-libp2p:1.2.2-RELEASE"),
    MavenPrebuilt("org.jetbrains.kotlin:kotlin-reflect:1.9.22"),
    MavenPrebuilt("community.kotlin.rpc:protocol-api:0.0.2"),
    MavenPrebuilt("community.kotlin.rpc:protocol-impl:0.0.14"),
    MavenPrebuilt("com.google.protobuf:protobuf-java:3.25.1"),
    MavenPrebuilt("tech.pegasys:noise-java:22.1.0"),
    // JSON
    MavenPrebuilt("org.json:json:20250517"),
    // Kotlin stdlib
    MavenPrebuilt("org.jetbrains.kotlin:kotlin-stdlib:1.9.22"),
    MavenPrebuilt("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22"),
    MavenPrebuilt("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22"),
    // Coroutines
    MavenPrebuilt("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0"),
    // Okio
    MavenPrebuilt("com.squareup.okio:okio-jvm:3.4.0"),
    // Logging
    MavenPrebuilt("org.slf4j:slf4j-api:1.7.36"),
    MavenPrebuilt("org.slf4j:slf4j-simple:1.7.36"),
    // Netty (for libp2p)
    MavenPrebuilt("io.netty:netty-buffer:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-codec:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-codec-http:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-codec-http2:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-common:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-handler:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-resolver:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-transport:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-transport-classes-epoll:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-transport-classes-kqueue:4.1.101.Final"),
    MavenPrebuilt("io.netty:netty-transport-native-unix-common:4.1.101.Final"),
    // BouncyCastle
    MavenPrebuilt("org.bouncycastle:bcpkix-jdk18on:1.78.1"),
    MavenPrebuilt("org.bouncycastle:bcprov-jdk18on:1.78.1"),
    MavenPrebuilt("org.bouncycastle:bcutil-jdk18on:1.78.1"),
    // Guava (required by libp2p)
    MavenPrebuilt("com.google.guava:guava:33.2.0-jre"),
    MavenPrebuilt("com.google.guava:failureaccess:1.0.2"),
)

// Client code dependencies - implements interfaces from DockerRunApi
val clientDependencies = resolveDependencies(
    MavenPrebuilt("org.jetbrains.kotlin:kotlin-stdlib:1.9.22"),
    MavenPrebuilt("dockerrun.api:docker-run-api:0.0.1"),
    MavenPrebuilt("foundation.url:service-bridge-stub:0.0.1"),
)

@MavenArtifactCoordinates("dockerrunserver:docker-run-server-service:")
fun buildMaven(): File {
    return buildSimpleKotlinMavenArtifact(
        // 0.0.1: Initial release
        //        - URL Protocol service for url://dockerrun/
        //        - Supports ContainerNursery lazy-start and standalone P2P modes
        //        - RPC handler for container lifecycle operations
        //        - Pure Java SJVM client implementation
        coordinates = "dockerrunserver:docker-run-server-service:0.0.1",
        src = File("src"),
        compileDependencies = dependencies
    )
}

fun buildSkinnyJar(): File {
    return buildMaven().jar
}

/**
 * Build the Java client JAR for SJVM execution.
 */
fun buildClientJar(): File {
    val srcDir = File("src-client-java")
    val outputDir = java.io.File.createTempFile("java-client-classes", "").apply { delete(); mkdirs() }

    val javaFiles = srcDir.walkTopDown().filter { it.extension == "java" }.toList()
    val classpath = clientDependencies.map { it.jar.absolutePath }.joinToString(File.pathSeparator)

    val javacArgs = mutableListOf(
        "javac",
        "-d", outputDir.absolutePath,
        "-cp", classpath,
        "-source", "11",
        "-target", "11"
    )
    javacArgs.addAll(javaFiles.map { it.absolutePath })

    val javacProcess = ProcessBuilder(javacArgs)
        .redirectErrorStream(true)
        .start()
    val javacOutput = javacProcess.inputStream.bufferedReader().readText()
    val javacExitCode = javacProcess.waitFor()

    if (javacExitCode != 0) {
        throw IllegalStateException("Java compilation failed:\n$javacOutput")
    }

    val jarFile = java.io.File.createTempFile("java-client", ".jar")
    java.util.jar.JarOutputStream(jarFile.outputStream()).use { jos ->
        outputDir.walkTopDown().filter { it.isFile }.forEach { classFile ->
            val entryName = classFile.relativeTo(outputDir).path.replace(File.separatorChar, '/')
            jos.putNextEntry(java.util.jar.JarEntry(entryName))
            jos.write(classFile.readBytes())
            jos.closeEntry()
        }
    }

    return jarFile
}

/**
 * Creates a fat client JAR including:
 * - Client implementation classes (pure Java)
 * - API classes (ContainerStatus enum, DockerContainer/DockerRunService interfaces)
 * - ServiceBridge stub (SJVM RPC intrinsic)
 * - Kotlin stdlib (required by SJVM/UrlResolver internals)
 */
fun buildFatClientJar(): File {
    val clientJar = buildClientJar()
    val apiJar = clientDependencies.find { it.jar.name.contains("docker-run-api") }?.jar
        ?: throw IllegalStateException("Could not find docker-run-api dependency")
    val kotlinStdlibJar = clientDependencies.find { it.jar.name.contains("kotlin-stdlib") && !it.jar.name.contains("jdk") }?.jar
        ?: throw IllegalStateException("Could not find kotlin-stdlib dependency")
    val serviceBridgeStubJar = clientDependencies.find { it.jar.name.contains("service-bridge-stub") }?.jar
        ?: throw IllegalStateException("Could not find service-bridge-stub dependency")

    val tempFile = java.io.File.createTempFile("fat-client", ".jar")
    val processedEntries = mutableSetOf<String>()

    fun addEntriesFromJar(jis: java.util.jar.JarInputStream, jos: java.util.jar.JarOutputStream) {
        var entry = jis.nextJarEntry
        while (entry != null) {
            if (!entry.isDirectory && !processedEntries.contains(entry.name)) {
                processedEntries.add(entry.name)
                jos.putNextEntry(java.util.jar.JarEntry(entry.name))
                jos.write(jis.readBytes())
                jos.closeEntry()
            }
            entry = jis.nextJarEntry
        }
    }

    java.util.jar.JarOutputStream(tempFile.outputStream()).use { jos ->
        java.util.jar.JarInputStream(clientJar.inputStream()).use { jis ->
            addEntriesFromJar(jis, jos)
        }
        java.util.jar.JarInputStream(apiJar.inputStream()).use { jis ->
            addEntriesFromJar(jis, jos)
        }
        java.util.jar.JarInputStream(serviceBridgeStubJar.inputStream()).use { jis ->
            addEntriesFromJar(jis, jos)
        }
        java.util.jar.JarInputStream(kotlinStdlibJar.inputStream()).use { jis ->
            addEntriesFromJar(jis, jos)
        }
    }
    return tempFile
}

/**
 * Creates a JAR containing the fat client JAR as a resource entry /client-impl.jar.
 */
fun buildClientResourcesJar(): File {
    val fatClientJar = buildFatClientJar()
    val tempFile = java.io.File.createTempFile("client-resources", ".jar")
    java.util.jar.JarOutputStream(tempFile.outputStream()).use { jos ->
        val entry = java.util.jar.JarEntry("client-impl.jar")
        jos.putNextEntry(entry)
        jos.write(fatClientJar.readBytes())
        jos.closeEntry()
    }
    return tempFile
}

/**
 * Build fat JAR with bundled client.
 */
fun buildFatJar(): File {
    val manifest = Manifest("dockerrunserver.MainKt")
    val clientResourcesJar = buildClientResourcesJar()
    return BuildJar(manifest, dependencies.map { it.jar } + buildSkinnyJar() + clientResourcesJar)
}
