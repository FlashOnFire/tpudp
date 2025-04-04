plugins {
    java
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(23)
    }
}

group = "fr.polytech"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {}

tasks.register("buildAllJars") {
    dependsOn("jarPortScanner", "jarUDPClient", "jarUDPServer", "jarChatUDPServer", "jarChatUDPClient")
}

tasks.register<JavaExec>("runPortScanner") {
    mainClass.set("fr.polytech.UDPPortScanner")
    classpath = sourceSets.main.get().runtimeClasspath
    standardInput = System.`in`
}

tasks.register<JavaExec>("runUDPClient") {
    mainClass.set("fr.polytech.UDPClient")
    classpath = sourceSets.main.get().runtimeClasspath
    standardInput = System.`in`
}

tasks.register<JavaExec>("runUDPServer") {
    mainClass.set("fr.polytech.UDPServer")
    classpath = sourceSets.main.get().runtimeClasspath
    standardInput = System.`in`
}

tasks.register<JavaExec>("runChatUDPServer") {
    mainClass.set("fr.polytech.ChatUDPServer")
    classpath = sourceSets.main.get().runtimeClasspath
    standardInput = System.`in`
}

tasks.register<JavaExec>("runChatUDPClient") {
    mainClass.set("fr.polytech.ChatUDPClient")
    classpath = sourceSets.main.get().runtimeClasspath
    standardInput = System.`in`
}

tasks.register<Jar>("jarPortScanner") {
    archiveBaseName.set("port-scanner")
    manifest {
        attributes["Main-Class"] = "fr.polytech.UDPPortScanner"
    }
    from(sourceSets.main.get().output)
}

tasks.register<Jar>("jarUDPClient") {
    archiveBaseName.set("udp-client")
    manifest {
        attributes["Main-Class"] = "fr.polytech.UDPClient"
    }
    from(sourceSets.main.get().output)
}

tasks.register<Jar>("jarChatUDPClient") {
    archiveBaseName.set("chat-udp-client")
    manifest {
        attributes["Main-Class"] = "fr.polytech.ChatUDPClient"
    }
    from(sourceSets.main.get().output)
}

tasks.register<Jar>("jarUDPServer") {
    archiveBaseName.set("udp-server")
    manifest {
        attributes["Main-Class"] = "fr.polytech.UDPServer"
    }
    from(sourceSets.main.get().output)
}

tasks.register<Jar>("jarChatUDPServer") {
    archiveBaseName.set("chat-udp-server")
    manifest {
        attributes["Main-Class"] = "fr.polytech.ChatUDPServer"
    }
    from(sourceSets.main.get().output)
}

tasks.named<JavaExec>("run") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = false
}
