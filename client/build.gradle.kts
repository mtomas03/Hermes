plugins {
    application
    id("com.gradleup.shadow")
}

group = "it.unibo.hermes.client"

application {
    mainClass.set("it.unibo.hermes.client.HermesClientApp")
}

dependencies {
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

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.springframework:spring-test")
    testImplementation("io.projectreactor:reactor-test")
}
