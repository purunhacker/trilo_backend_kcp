plugins {
    java
    `java-library`
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // OpenFeign for service-to-service clients
    api("org.springframework.cloud:spring-cloud-starter-openfeign")

    // Security for CurrentUser annotation support
    api("org.springframework.security:spring-security-core")
    api("org.springframework.security:spring-security-oauth2-jose")

    // JPA API for EntityNotFoundException handling
    compileOnly("jakarta.persistence:jakarta.persistence-api")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
