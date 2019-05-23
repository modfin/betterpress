FROM openjdk:8-jdk-alpine3.9 AS builder

COPY . /usr/src/betterpress/
WORKDIR /usr/src/betterpress/
RUN apk --no-cache add maven

RUN mvn package

FROM openjdk:8-jre-alpine3.9
WORKDIR /root/
COPY --from=builder /usr/src/betterpress/target/betterpress.jar .

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "./betterpress.jar"]
