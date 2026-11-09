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
    implementation("org.springframework.boot:spring-boot-starter-webflux") // For WebClient

    // Database
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // JWT validation via JWKS
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // ShedLock for distributed scheduling
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.13.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.13.0")

    // OpenFeign
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")

    // JSON
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // AWS SDK for MinIO/S3
    implementation(platform("software.amazon.awssdk:bom:2.25.45"))
    implementation("software.amazon.awssdk:s3")

    // ULID
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
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootJar {
    archiveBaseName.set("trip-service")
}
