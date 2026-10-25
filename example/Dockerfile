# Conformance example app (Ktor + kotlinx.html). Build with the package folder
# as the context:
#   docker build -f example/Dockerfile .
FROM gradle:8-jdk21 AS build
WORKDIR /src
# The plugin from this repo's own source (includeBuild("..") in settings.gradle.kts).
COPY . .
RUN gradle --no-daemon --console=plain -q -p example installDist

FROM eclipse-temurin:21-jre
COPY --from=build /src/example/build/install/example /app
EXPOSE 8080
USER 65534
CMD ["/app/bin/example"]
