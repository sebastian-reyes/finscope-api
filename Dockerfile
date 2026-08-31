# Imagen de produccion de la API de FinScope.
#
# Se construye en dos etapas para que la imagen final no arrastre ni Maven ni el codigo
# fuente: lo unico que necesita para funcionar es el JAR y una maquina virtual.

# ---------------------------------------------------------------------------
# Etapa 1: compilacion
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build

WORKDIR /build

# Las dependencias cambian mucho menos que el codigo, asi que se resuelven en su propia
# capa: mientras el pom no se toque, reconstruir no vuelve a descargarlas. Si la resolucion
# falla, el build se detiene aqui: silenciar el error solo lo aplaza hasta el empaquetado,
# donde aparece mezclado con el resto de la compilacion y cuesta mucho mas de leer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# El nombre del JAR lleva la version, que cambia con cada release. Se renombra aqui para
# que la etapa siguiente y el arranque no tengan que conocerla.
RUN cp target/*.jar /build/app.jar

# ---------------------------------------------------------------------------
# Etapa 2: ejecucion
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine

# curl es lo unico que se agrega al runtime, y solo para que el healthcheck pueda preguntar
# por HTTP: la imagen base no trae ningun cliente con el que hacerlo.
RUN apk add --no-cache curl

# Un fallo dentro de la aplicacion no debe traducirse en permisos de administrador sobre el
# contenedor, asi que el proceso corre con una cuenta sin privilegios y sin shell de acceso.
RUN addgroup --system --gid 1001 finscope \
 && adduser --system --uid 1001 --ingroup finscope --no-create-home --shell /sbin/nologin finscope

WORKDIR /app
COPY --from=build --chown=finscope:finscope /build/app.jar /app/app.jar

USER finscope

# Puerto por defecto de la aplicacion. La plataforma puede publicar otro mediante
# SERVER_PORT sin reconstruir la imagen.
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod
# SERVER_PORT no se fija aqui a proposito: Spring lo enlaza directamente sobre server.port y
# taparia al PORT que inyecta la plataforma. El puerto por defecto lo decide la aplicacion.
# Sin este porcentaje la maquina virtual dimensiona el monton contando toda la memoria de la
# maquina y no la que el contenedor tiene asignada, y acaba muriendo por consumo.
# `--enable-native-access` autoriza a Netty a cargar su libreria nativa. Sin esta opcion
# Java 25 lo permite pero avisa en cada arranque, y en versiones futuras lo bloqueara.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError --enable-native-access=ALL-UNNAMED"

# El estado se consulta por el endpoint publico, que responde si vive sin decir de que esta
# hecha. El margen de arranque cubre el tiempo de las migraciones en una base vacia.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${PORT:-${SERVER_PORT:-8080}}/actuator/health" || exit 1

# `exec` deja a Java como proceso 1 para que reciba la senal de parada directamente y el
# cierre ordenado de Spring llegue a ejecutarse.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
