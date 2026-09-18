import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom")
    // Minecraft 26.x ships unobfuscated, which stock Loom's mapping lookup does not handle on its
    // own; this supplies the official Mojang mappings for it.
    id("dev.kikugie.loom-back-compat")
    id("org.jetbrains.kotlin.jvm")
}

version = "${property("mod_version")}+${property("minecraft_version")}"
group = property("maven_group")!!

base {
    archivesName = property("archives_base_name") as String
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
}

// Minecraft 26.2 is built for Java 25, and the mixins here weave into its classes, so the mod has
// to be compiled for the same release rather than an older one.
val javaVersion = JavaVersion.VERSION_25

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion.majorVersion.toInt()
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

java {
    withSourcesJar()
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

// Resolved here rather than inside the task, where `property` would look at the task itself.
val resourceProperties = mapOf(
    "version" to version,
    "minecraft_version" to property("minecraft_version"),
    "loader_version" to property("loader_version"),
    "fabric_kotlin_version" to property("fabric_kotlin_version"),
    "java_version" to javaVersion.majorVersion,
)

tasks.processResources {
    val properties = resourceProperties
    inputs.properties(properties)
    filesMatching("fabric.mod.json") { expand(properties) }
    filesMatching("aboutface.mixins.json") { expand(properties) }
}

tasks.jar {
    from("LICENSE") { rename { "${it}_${base.archivesName.get()}" } }
}
