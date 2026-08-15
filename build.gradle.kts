plugins {
    id("java")
    id("com.gradleup.shadow") version "9.0.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven {
        name = "local-m2-repo"
        url = uri(System.getProperty("user.home") + "/.m2/repository")
    }
}

dependencies {
    // Paper provides the Dialog API and Adventure at runtime.
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // The pure chess engine (rules + game/service impls), bundled into the jar so the plugin
    // has no external dependency. The engine is wired by hand (see EngineFactory), so its Guice
    // module is unused and Guice/Guava are NOT pulled in — keeping the jar tiny. Relocated under
    // codes.castled.chess.engine so it never clashes with another plugin shipping the same engine.
    implementation("com.dxzell:pocket-chess-api:1.0-SNAPSHOT") { isTransitive = false }
    implementation("com.dxzell:pocket-chess-common:1.0-SNAPSHOT") { isTransitive = false }

    // Compile-only: the engine's .class files carry @Inject annotations; keep Guice on the
    // compile classpath to resolve them, but never bundle it (we wire by hand at runtime).
    compileOnly("com.google.inject:guice:7.0.0")

    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")

    // Tests: JUnit 5, the Paper API (Component/dialog types) and the engine, which is already
    // on the implementation classpath. The tests exercise the pure content builders and the
    // engine only, so no running server is required.
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

group = "codes.castled"
version = "1.0.2"

java {
    toolchain {
        // Build with JDK 21 — Lombok silently no-ops on newer JDKs.
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Zip the editable resource pack at src/main/resources/resourcepack into a single
// resourcepack.zip placed at the jar root. The plugin extracts it to its data folder on
// enable so ResourcePackManager (or the direct-send fallback) has a real file to serve.
val resourcePackZip by tasks.registering(Zip::class) {
    archiveFileName.set("resourcepack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated/resourcepack"))
    from("src/main/resources/resourcepack") {
        exclude("**/*.zip")
    }
    // Deterministic entries so identical sources produce an identical pack (stable SHA-1).
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

tasks.processResources {
    dependsOn(resourcePackZip)
    // Ship only the zipped pack, not the raw tree.
    exclude("resourcepack/**")
    from(resourcePackZip)
    // Inject the project version into plugin.yml's ${version} placeholder.
    val tokens = mapOf("version" to project.version)
    inputs.properties(tokens)
    filesMatching("plugin.yml") { expand(tokens) }
}

tasks.shadowJar {
    // Always produce Chess.jar regardless of version.
    archiveFileName.set("Chess.jar")
    // Relocate the bundled engine so it never clashes with another plugin shipping it.
    relocate("com.dxzell.pocketchess", "codes.castled.chess.engine")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
