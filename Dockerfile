# --- Build stage -----------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Кешування залежностей окремим шаром: цей шар перебудовується лише коли
# міняється pom.xml, а не при кожній зміні коду.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Репозиторій клонується з Windows-хосту, де git не зберігає прапорець
# "виконуваний" — без цього ./mvnw падає з exit code 126 на Linux.
RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN useradd --system --create-home --shell /usr/sbin/nologin app
USER app

# Шари копіюються окремо (від найстабільнішого до найчастіше змінюваного),
# щоб зміна коду застосунку не інвалідувала кеш шарів залежностей.
COPY --from=build --chown=app:app /build/target/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/target/extracted/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
