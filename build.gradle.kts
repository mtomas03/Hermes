plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
}

allprojects {
    group = "it.unibo.hermes"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    pluginManager.apply("java")
    pluginManager.apply("jacoco")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    jacoco {
        toolVersion = "0.8.14"
    }

    tasks.test {
        useJUnitPlatform()
        finalizedBy(tasks.jacocoTestReport)
    }

    tasks.jacocoTestReport {
        dependsOn(tasks.test)

        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }
}

configure(subprojects.filter { it.name in listOf("client", "gateway", "worker") }) {
    pluginManager.apply("org.springframework.boot")
    pluginManager.apply("io.spring.dependency-management")

    tasks.named<Jar>("jar") {
        enabled = false
    }
}
