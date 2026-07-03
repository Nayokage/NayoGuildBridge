import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.3.20"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

base {
	archivesName = providers.gradleProperty("archives_base_name")
}

repositories {
	maven("https://maven.terraformersmc.com/releases/")
	maven("https://api.modrinth.com/maven")
	maven("https://maven.isxander.dev/releases")
}

dependencies {
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")

	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

	compileOnly("com.terraformersmc:modmenu:${providers.gradleProperty("modmenu_version").get()}")
	compileOnly("maven.modrinth:1eAoo2KR:${providers.gradleProperty("yacl_version").get()}")

	implementation("org.java-websocket:Java-WebSocket:1.5.7")
	include("org.java-websocket:Java-WebSocket:1.5.7")

	testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.processResources {
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_25
	}
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
	withSourcesJar()
}

tasks.jar {
	inputs.property("archivesName", base.archivesName)

	from("LICENSE") {
		rename { "${it}_${base.archivesName.get()}" }
	}
}

tasks.test {
	useJUnitPlatform()
}

tasks.register("rebuild") {
	group = "build"
	description = "Clean and rebuild the mod (clean + build)"
	dependsOn("clean", "build")
}
tasks.named("build").configure {
	mustRunAfter("clean")
}

publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			artifactId = base.archivesName.get()
			from(components["java"])
		}
	}

	repositories {
	}
}
