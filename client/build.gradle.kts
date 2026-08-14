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
    
}
