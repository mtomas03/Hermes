group = "it.unibo.hermes.e2e"

tasks.test {
    enabled = false
}

sourceSets {
    create("e2eTest") {
        java.srcDir("src/e2eTest/java")
        resources.srcDir("src/e2eTest/resources")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val e2eTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.implementation.get())
}
val e2eTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.runtimeOnly.get())
}

dependencies {
    implementation(project(":client"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.5"))
    implementation("org.springframework:spring-context")
    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation("org.springframework:spring-webflux")
    implementation("io.projectreactor.netty:reactor-netty-http")
    implementation("org.springframework:spring-websocket")
    implementation("org.springframework:spring-messaging")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.slf4j:slf4j-api")
    implementation("ch.qos.logback:logback-classic")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:2.1.5")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

    e2eTestImplementation("org.junit.jupiter:junit-jupiter")
    e2eTestRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.register<Test>("e2eTest") {
    description = "Runs the Hermes end-to-end suite on a running Minikube deployment."
    group = "verification"

    testClassesDirs = sourceSets["e2eTest"].output.classesDirs
    classpath = sourceSets["e2eTest"].runtimeClasspath
    useJUnitPlatform()

    outputs.upToDateWhen { false }

    systemProperty("hermes.e2e.baseUrl", System.getProperty("hermes.e2e.baseUrl", "http://hermes.local"))
    systemProperty("hermes.e2e.wsUrl", System.getProperty("hermes.e2e.wsUrl", "ws://hermes.local/ws"))
    systemProperty("hermes.e2e.namespace", System.getProperty("hermes.e2e.namespace", "hermes-namespace"))
    systemProperty("hermes.e2e.timeoutSeconds", System.getProperty("hermes.e2e.timeoutSeconds", "60"))

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    maxParallelForks = 1
}
