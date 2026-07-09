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

"$DIR/.maven/apache-maven-3.9.6/bin/mvn" -f "$DIR/pom.xml" spring-boot:run
