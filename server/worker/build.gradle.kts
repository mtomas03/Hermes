plugins {
    java
    id("com.gradleup.shadow") version "9.3.0"
}

group = "it.unibo.hermes.worker"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {

}
