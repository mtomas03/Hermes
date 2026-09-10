plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.gradleup.shadow")
}

group = "it.unibo.hermes.worker"

springBoot {
    mainClass.set("it.unibo.hermes.worker.HermesWorkerApp")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-cassandra")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
