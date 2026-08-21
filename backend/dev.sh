#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$DIR/.env.local"

if [ ! -f "$ENV_FILE" ]; then
  echo "Arquivo $ENV_FILE não encontrado. Copie .env.local.example para .env.local e ajuste os valores." >&2
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

exec "$JAVA_HOME/bin/java" \
  -Dmaven.multiModuleProjectDirectory="$DIR" \
  -classpath "$DIR/.mvn/wrapper/maven-wrapper.jar" \
  org.apache.maven.wrapper.MavenWrapperMain \
  -f "$DIR/pom.xml" spring-boot:run
