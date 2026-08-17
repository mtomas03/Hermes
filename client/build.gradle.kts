plugins {
    java
    application
    id("com.gradleup.shadow") version "9.3.0"
}

group = "it.unibo.hermes.client"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

application {
    mainClass.set("it.unibo.hermes.client.HermesClientApp")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework:spring-context:6.2.7")
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")

    implementation("org.springframework:spring-webflux:6.2.7")
    implementation("io.projectreactor.netty:reactor-netty-http:1.1.22")

    implementation("org.springframework:spring-websocket:6.2.7")
    implementation("org.springframework:spring-messaging:6.2.7")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:2.1.5")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")

    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.13")
}
