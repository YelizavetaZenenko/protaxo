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
# Spring Boot 4.x перейменував "layertools" на "tools" з іншим синтаксисом
# (--layers розбиває на шари, --launcher додає spring-boot-loader для JarLauncher).
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination target/extracted

# Програма друку наклейок (Print Agent) збирається разом із сервером, щоб на сайті
# («Основна інформація» → «Програма друку наклейок») завжди лежала остання версія.
# Окремий Maven-проєкт без залежностей; кеш ~/.m2 вже прогрітий збіркою вище.
COPY print-agent/pom.xml print-agent/pom.xml
COPY print-agent/src/ print-agent/src/
COPY print-agent/printagent.properties.example print-agent/printagent.properties.example
RUN ./mvnw -B -q -f print-agent/pom.xml package

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN useradd --system --create-home --shell /usr/sbin/nologin app
# logback-spring.xml пише аудит-лог у відносний шлях "logs/audit.log" —
# директорія має існувати заздалегідь, бо непривілейований user не створить
# її сам при першому записі.
RUN mkdir -p /app/logs && chown -R app:app /app
USER app

# Шари копіюються окремо (від найстабільнішого до найчастіше змінюваного),
# щоб зміна коду застосунку не інвалідувала кеш шарів залежностей.
COPY --from=build --chown=app:app /build/target/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/target/extracted/application/ ./
# Файли для завантаження з сайту: print-agent.jar і приклад налаштувань (PrintAgentDownloadController).
COPY --from=build --chown=app:app /build/print-agent/target/print-agent.jar /app/downloads/print-agent.jar
COPY --from=build --chown=app:app /build/print-agent/printagent.properties.example /app/downloads/printagent.properties.example

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
