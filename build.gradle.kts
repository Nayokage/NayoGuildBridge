import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom-remap")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.3.10"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

base {
	archivesName = providers.gradleProperty("archives_base_name")
}

repositories {
	maven("https://maven.terraformersmc.com/releases/")
	maven("https://api.modrinth.com/maven")
}

dependencies {
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	mappings(loom.officialMojangMappings())

	modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
	modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	modImplementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

	modApi("com.terraformersmc:modmenu:16.0.0")
	modApi("maven.modrinth:1eAoo2KR:3.8.2+1.21.10-fabric")

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
	options.release = 21
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_21
	}
}

java {
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
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
