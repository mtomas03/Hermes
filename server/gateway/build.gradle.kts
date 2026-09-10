plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.gradleup.shadow")
}

group = "it.unibo.hermes.gateway"

springBoot {
    mainClass.set("it.unibo.hermes.gateway.HermesGatewayApp")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-cassandra")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    implementation("org.springframework.kafka:spring-kafka")

    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
