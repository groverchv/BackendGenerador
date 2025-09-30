# ---- Etapa de build ----
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# 1) Cache de dependencias
COPY pom.xml .
RUN mvn -B -ntp -DskipTests dependency:go-offline

# 2) Código fuente
COPY src ./src
RUN mvn -B -ntp -DskipTests package

# ---- Etapa de runtime (liviana) ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Opcional: flags de JVM/memoria y puerto inyectable (Render usa $PORT)
ENV JAVA_OPTS=""
ENV SERVER_PORT=${PORT:-8080}

# Copiamos el .jar construido
COPY --from=build /app/target/*-SNAPSHOT.jar app.jar

EXPOSE 8080
# Usa SERVER_PORT si la plataforma define PORT
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -Dserver.port=$SERVER_PORT -jar app.jar"]
