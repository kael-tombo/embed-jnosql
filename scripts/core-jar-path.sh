#!/usr/bin/env bash

find_core_jar() {
  local jar
  local jars=()

  while IFS= read -r jar; do
    [ -n "$jar" ] && jars+=("$jar")
  done < <(
    find target -maxdepth 1 -type f -name 'embed-jnosql-core-*.jar' \
      ! -name 'embed-jnosql-core-*-sources.jar' \
      ! -name 'embed-jnosql-core-*-javadoc.jar' \
      ! -name 'embed-jnosql-core-*-shaded.jar' | sort
  )

  if [ "${#jars[@]}" -ne 1 ]; then
    echo "FAIL: expected exactly one versioned core JAR in target (found ${#jars[@]}); run ./mvnw clean package" >&2
    return 1
  fi

  printf '%s\n' "${jars[0]}"
}

core_java_classpath() {
  local jar="$1"
  local classpath_file="target/core-gate-classpath.txt"
  local separator=":"
  local dependencies
  local mvn=()

  if [ -x ./mvnw ]; then
    mvn=(./mvnw)
  elif [ -f ./mvnw ]; then
    mvn=(sh ./mvnw)
  else
    mvn=(mvn)
  fi

  if ! "${mvn[@]}" -B -q dependency:build-classpath \
    -Dmdep.includeScope=compile "-Dmdep.outputFile=$classpath_file"; then
    echo "FAIL: could not resolve the core gate dependency classpath" >&2
    return 1
  fi

  dependencies=$(<"$classpath_file") || {
    echo "FAIL: could not read the generated dependency classpath: $classpath_file" >&2
    return 1
  }
  if [ -z "$dependencies" ]; then
    echo "FAIL: Maven generated an empty dependency classpath" >&2
    return 1
  fi

  case "$(uname -s)" in
    CYGWIN*|MINGW*|MSYS*) separator=";" ;;
  esac

  printf '%s%s%s\n' "$jar" "$separator" "$dependencies"
}
