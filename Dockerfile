FROM maven:3.9.5-eclipse-temurin-21-alpine AS builder

COPY . /usr/src/betterpress/
WORKDIR /usr/src/betterpress/

RUN mvn package

FROM eclipse-temurin:21-alpine
WORKDIR /root/
COPY --from=builder /usr/src/betterpress/target/betterpress.jar .

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "./betterpress.jar"]
