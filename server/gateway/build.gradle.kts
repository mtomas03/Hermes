plugins {
    java
    id("org.springframework.boot") version "3.2.5"
    id("com.gradleup.shadow") version "9.3.0"
}

group = "it.unibo.hermes.gateway"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

springBoot {
    mainClass.set("it.unibo.hermes.gateway.HermesGatewayApp")
}

repositories {
    mavenCentral()
}

dependencies {
    
}
