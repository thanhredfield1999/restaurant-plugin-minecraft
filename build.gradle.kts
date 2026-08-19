import org.gradle.api.file.DuplicatesStrategy

plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

val postgresIntegrationTest by sourceSets.creating

postgresIntegrationTest.compileClasspath += sourceSets.main.get().output
postgresIntegrationTest.runtimeClasspath += sourceSets.main.get().output

configurations[postgresIntegrationTest.implementationConfigurationName].extendsFrom(
    configurations.testImplementation.get()
)
configurations[postgresIntegrationTest.runtimeOnlyConfigurationName].extendsFrom(
    configurations.testRuntimeOnly.get()
)

group = "vn.restauranttycoon"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.DecentSoftware-eu:DecentHolograms:2.9.9") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.flywaydb:flyway-core:10.20.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.1")
    implementation("org.postgresql:postgresql:42.7.4")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.h2database:h2:2.2.224")
    testCompileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testRuntimeOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Test>("postgresIntegrationTest") {
    description = "Runs opt-in integration tests against a real PostgreSQL database."
    group = "verification"
    testClassesDirs = postgresIntegrationTest.output.classesDirs
    classpath = postgresIntegrationTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    onlyIf("RT_TEST_POSTGRES_URL, RT_TEST_POSTGRES_USER and RT_TEST_POSTGRES_PASSWORD are set") {
        listOf(
            "RT_TEST_POSTGRES_URL",
            "RT_TEST_POSTGRES_USER",
            "RT_TEST_POSTGRES_PASSWORD"
        ).all { !System.getenv(it).isNullOrBlank() }
    }
}

tasks.shadowJar {
    archiveClassifier = ""
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    filesNotMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    relocate("com.zaxxer.hikari", "vn.restauranttycoon.libs.hikari")
    relocate("org.flywaydb", "vn.restauranttycoon.libs.flyway")
    mergeServiceFiles()
}

val verifyShadedJdbc by tasks.registering {
    dependsOn(tasks.shadowJar)
    doLast {
        val archive = tasks.shadowJar.get().archiveFile.get().asFile
        val contents = zipTree(archive)
        require(contents.matching { include("org/postgresql/Driver.class") }.files.size == 1) {
            "Shaded plugin is missing org/postgresql/Driver.class"
        }
        val service = contents.matching {
            include("META-INF/services/java.sql.Driver")
        }.singleFile.readText()
        require(service.lineSequence().any { it.trim() == "org.postgresql.Driver" }) {
            "Shaded plugin JDBC service descriptor does not register org.postgresql.Driver"
        }
        val flywayPlugins = contents.matching {
            include("META-INF/services/vn.restauranttycoon.libs.flyway.core.extensibility.Plugin")
        }.singleFile.readText()
        require(flywayPlugins.lineSequence().any {
            it.trim() == "vn.restauranttycoon.libs.flyway.core.internal.resource.CoreResourceTypeProvider"
        }) {
            "Shaded plugin is missing Flyway core resource-type provider"
        }
        require(flywayPlugins.lineSequence().any {
            it.trim() == "vn.restauranttycoon.libs.flyway.database.postgresql.PostgreSQLDatabaseType"
        }) {
            "Shaded plugin is missing Flyway PostgreSQL database provider"
        }
    }
}

tasks.jar {
    archiveClassifier = "thin"
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.check {
    dependsOn(verifyShadedJdbc)
}

tasks.register<Exec>("paperSmokeGuardTest") {
    description = "Runs Windows-only regression tests for the paper-smoke guard helpers."
    group = "verification"
    onlyIf("Windows host with PowerShell available") {
        System.getProperty("os.name").lowercase().contains("windows")
    }
    workingDir = projectDir
    commandLine(
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        "scripts/test-paper-smoke-guard.ps1"
    )
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
