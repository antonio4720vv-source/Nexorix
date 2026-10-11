# Nexorix: compila y ejecuta con Java 21. Sirve para x86 y para ARM (Oracle Ampere).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/*.jar /app/nexorix.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home nexorix
WORKDIR /app
COPY --from=build /app/nexorix.jar /app/nexorix.jar
USER nexorix
EXPOSE 8080
# Usa hasta el 60 % de la memoria de la maquina (la JVM sola gasta ~250 MB).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "/app/nexorix.jar"]
