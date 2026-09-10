# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
RUN apk add --no-cache maven
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# Stage 2: Runtime — non-root
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S walletgrp && adduser -S walletapp -G walletgrp
WORKDIR /app
COPY --from=builder /app/target/wallet-transfer-*.jar app.jar
RUN chown walletapp:walletgrp app.jar
USER walletapp
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
