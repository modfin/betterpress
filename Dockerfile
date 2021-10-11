FROM openjdk:17-jdk-alpine3.14 AS builder

COPY . /usr/src/betterpress/
WORKDIR /usr/src/betterpress/
RUN apk --no-cache add maven

RUN mvn package

FROM openjdk:11-jre-slim
WORKDIR /root/
COPY --from=builder /usr/src/betterpress/target/betterpress.jar .

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "./betterpress.jar"]
