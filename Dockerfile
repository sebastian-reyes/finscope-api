# Imagen de produccion de la API de FinScope.
#
# Se construye en tres etapas para que la imagen final no arrastre ni Maven ni el codigo
# fuente: lo unico que necesita para funcionar es la aplicacion, su cache de arranque y una
# maquina virtual.

# El reparto de la memoria se fija a mano porque el contenedor es pequeno y la maquina
# virtual solo dimensiona el monton: metaspace, cache de codigo, pilas y buffers nativos
# quedan fuera de ese porcentaje y se suman encima. Con un 75% en 512 MB el monton se lleva
# 384 y al resto le quedan 128, que no alcanzan; el contenedor se pasa del limite y el
# sistema mata el proceso sin que Java llegue a lanzar OutOfMemoryError, asi que el fallo se
# ve como un reinicio sin rastro en los logs. De ahi que cada zona lleve su propio tope.
#
# Reparto pensado para 512 MB: 256 de monton, hasta 128 de metaspace, hasta 64 de cache de
# codigo y 32 de memoria directa. Ese ultimo tope importa mas de lo que parece: sin el,
# Netty toma el tamano del monton como su propio limite y reserva mucho mas de lo necesario.
#
# El recolector serie es el que la maquina virtual ya elige con una CPU y esta memoria; se
# deja explicito para que no cambie si la plataforma informa otra cosa, y porque los
# concurrentes cobran hilos propios y estructuras por region que aqui no sobran.
#
# No se toca el tamano de pila: las cadenas reactivas anidan mucho y recortarlo cambia un
# problema de memoria por desbordamientos de pila, mientras el ahorro real es minimo porque
# solo cuentan las paginas que cada hilo llega a tocar.
#
# "--enable-native-access" autoriza a Netty a cargar su libreria nativa. Sin esta opcion
# Java 25 lo permite pero avisa en cada arranque, y en versiones futuras lo bloqueara.
#
# Vive en un ARG global, y no escrito dos veces, porque la etapa 3 lo publica como JAVA_OPTS
# y la etapa 2 tiene que entrenar la cache con EXACTAMENTE la misma lista. Un solo desajuste
# la invalida: entrenar sin "--enable-native-access" y arrancar con el basta para que la
# maquina virtual descarte la cache entera y el arranque vuelva a costar lo mismo que antes,
# avisando solo con una linea de error que en produccion nadie mira.
ARG JVM_OPTS="-XX:MaxRAMPercentage=50 \
-XX:MaxMetaspaceSize=128m \
-XX:ReservedCodeCacheSize=64m \
-XX:MaxDirectMemorySize=32m \
-XX:+UseSerialGC \
-XX:+ExitOnOutOfMemoryError \
--enable-native-access=ALL-UNNAMED"

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
# que las etapas siguientes y el arranque no tengan que conocerla.
RUN cp target/*.jar /build/app.jar

# ---------------------------------------------------------------------------
# Etapa 2: cache de arranque (AOT)
# ---------------------------------------------------------------------------
# Con medio nucleo de CPU el arranque lo domina cargar, verificar y enlazar clases, que es
# trabajo de un solo hilo y no se reparte. La cache AOT de Java 25 hace ese trabajo una vez
# aqui, en tiempo de build, y lo deja escrito en un archivo que el arranque real solo mapea.
# Medido sobre este JAR: 2.9 s -> 1.35 s.
#
# Tambien ahorra memoria, que en un contenedor de 512 MB pesa mas que el tiempo: los
# metadatos de las clases se leen de la cache en lugar de construirse en metaspace, que baja
# de ~43 MB a ~4 MB. Lo que la cache ocupa son paginas mapeadas del archivo, limpias, que el
# nucleo puede descartar bajo presion; el metaspace que sustituye era memoria anonima que no
# se recuperaba nunca.
#
# La cache queda atada a la maquina virtual exacta que la escribe, asi que esta etapa parte
# de la MISMA imagen base que la de ejecucion. Generarla en la etapa de compilacion no
# serviria: ese JDK va sobre glibc y el runtime sobre musl, y se descartaria al arrancar.
FROM eclipse-temurin:25-jre-alpine AS aot

ARG JVM_OPTS

# El directorio de trabajo es parte del contrato. La cache guarda las rutas del classpath tal
# como las vio al grabarse, relativas al JAR: mover el arbol completo a otro sitio no la
# rompe, pero cambiar el nombre del JAR o su posicion respecto a lib/ si. De ahi que esta
# etapa y la siguiente usen /app y "-jar app.jar", nunca una ruta absoluta.
WORKDIR /app

# El JAR ejecutable de Spring lleva las dependencias anidadas dentro, y la cache necesita un
# classpath plano que pueda verificar entrada por entrada. "jarmode=tools extract" lo abre en
# un JAR delgado mas un directorio lib/, que es la forma que la cache sabe validar. El JAR
# original se borra en el mismo RUN para no dejarlo pesando en la capa.
COPY --from=build /build/app.jar /tmp/app.jar
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app && rm /tmp/app.jar

# El entrenamiento arranca la aplicacion de verdad para observar que clases se usan, asi que
# necesita las variables del perfil de produccion. Ninguna apunta a nada real ni es un
# secreto: Flyway queda apagado y el pool arranca sin conexiones, que es lo que permite que
# el contexto levante sin base de datos. El puerto 0 pide uno libre al sistema para no
# chocar con nada dentro del build.
#
# "spring.context.exit=onRefresh" corta el proceso justo cuando el contexto termina de
# levantar, sin llegar a atender peticiones: es todo lo que hace falta observar.
#
# La segunda invocacion es la comprobacion. En el arranque real un desajuste de la cache solo
# imprime un error y sigue sin ella, asi que una cache inservible pasaria inadvertida y el
# ahorro se perderia en silencio; "AOTMode=on" convierte ese mismo desajuste en un fallo, y
# con "set -e" el build se detiene aqui en lugar de publicar una imagen que finge estar
# optimizada.
RUN set -eu; \
    export SPRING_PROFILES_ACTIVE=prod PORT=0 FLYWAY_ENABLED=false DB_POOL_INITIAL_SIZE=0; \
    export DB_URL="r2dbc:postgresql://entrenamiento:5432/finscope"; \
    export DB_USERNAME=entrenamiento DB_PASSWORD=entrenamiento; \
    export JWT_SECRET="valor-falso-solo-para-entrenar-la-cache-aot"; \
    export CORS_ALLOWED_ORIGINS="https://entrenamiento.invalid"; \
    java ${JVM_OPTS} -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar app.jar; \
    java ${JVM_OPTS} -XX:AOTMode=on -XX:AOTCache=app.aot -Dspring.context.exit=onRefresh -jar app.jar

# ---------------------------------------------------------------------------
# Etapa 3: ejecucion
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine

ARG JVM_OPTS

# curl es lo unico que se agrega al runtime, y solo para que el healthcheck pueda preguntar
# por HTTP: la imagen base no trae ningun cliente con el que hacerlo.
RUN apk add --no-cache curl

# Un fallo dentro de la aplicacion no debe traducirse en permisos de administrador sobre el
# contenedor, asi que el proceso corre con una cuenta sin privilegios y sin shell de acceso.
RUN addgroup --system --gid 1001 finscope \
 && adduser --system --uid 1001 --ingroup finscope --no-create-home --shell /sbin/nologin finscope

WORKDIR /app

# Se copia el arbol completo que dejo la etapa anterior: JAR delgado, lib/ y la cache. Los
# tres tienen que viajar juntos y conservar su disposicion relativa, que es lo que la cache
# valida al arrancar.
COPY --from=aot --chown=finscope:finscope /app/ /app/

USER finscope

# Puerto por defecto de la aplicacion. La plataforma puede publicar otro mediante
# SERVER_PORT sin reconstruir la imagen.
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod
# SERVER_PORT no se fija aqui a proposito: Spring lo enlaza directamente sobre server.port y
# taparia al PORT que inyecta la plataforma. El puerto por defecto lo decide la aplicacion.
#
# Las opciones son las del ARG global, las mismas con las que se entreno la cache. Cambiarlas
# desde el panel de la plataforma sigue siendo posible --- bajar el monton sin reconstruir la
# imagen, por ejemplo --- pero quitar o agregar opciones de modulo invalida la cache: eso no
# rompe el arranque, solo lo devuelve a su coste original.
ENV JAVA_OPTS="${JVM_OPTS}"

# El estado se consulta por el endpoint publico, que responde si vive sin decir de que esta
# hecha. El margen de arranque cubre el tiempo de las migraciones en una base vacia.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${PORT:-${SERVER_PORT:-8080}}/actuator/health" || exit 1

# "exec" deja a Java como proceso 1 para que reciba la senal de parada directamente y el
# cierre ordenado de Spring llegue a ejecutarse.
#
# La cache va fuera de JAVA_OPTS para que sobreescribir esa variable no la desactive sin
# querer, y antes que ella para que quien quiera desactivarla a proposito pueda repetir la
# opcion y ganar.
ENTRYPOINT ["sh", "-c", "exec java -XX:AOTCache=app.aot $JAVA_OPTS -jar app.jar"]
