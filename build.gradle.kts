plugins {
    java
    id("org.springframework.boot") version "3.3.5" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("com.diffplug.spotless") version "6.25.0"
}

allprojects {
    group = "app.trilo"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.3")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

spotless {
    java {
        target("**/src/**/*.java")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        googleJavaFormat("1.22.0")
    }
}
