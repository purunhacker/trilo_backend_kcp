plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common-lib"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Database
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // BCrypt
    implementation("org.springframework.security:spring-security-crypto")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // ShedLock for distributed scheduling
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.13.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.13.0")

    // Google OAuth
    implementation("com.google.api-client:google-api-client:2.7.0")

    // Apple Sign In - JWKS verification
    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")

    // AWS S3 / MinIO
    implementation("software.amazon.awssdk:s3:2.25.0")

    // Image processing for avatar
    implementation("net.coobird:thumbnailator:0.4.20")

    // ULID generation
    implementation("com.github.f4b6a3:ulid-creator:5.2.3")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.apache.httpcomponents.client5:httpclient5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootJar {
    archiveBaseName.set("auth-service")
}
