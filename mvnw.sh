#!/bin/bash
# Maven Wrapper script for bash environments

set -e

MAVEN_BASEDIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$MAVEN_BASEDIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_LAUNCHER="org.apache.maven.wrapper.MavenWrapperMain"

# Download wrapper JAR if it doesn't exist
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Maven wrapper JAR..."
    mkdir -p "$MAVEN_BASEDIR/.mvn/wrapper"

    WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"

    if command -v curl &> /dev/null; then
        curl -L "$WRAPPER_URL" -o "$WRAPPER_JAR"
    elif command -v wget &> /dev/null; then
        wget "$WRAPPER_URL" -O "$WRAPPER_JAR"
    else
        echo "Error: Neither curl nor wget found. Cannot download Maven wrapper JAR."
        exit 1
    fi
fi

# Set JAVA_HOME if not set
if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME="/c/Users/henry/AppData/Local/Programs/Eclipse Adoptium/jdk-25.0.1.8-hotspot"
    export JAVA_HOME
fi

# Find java executable
if [ ! -f "$JAVA_HOME/bin/java" ]; then
    echo "Error: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    echo "JAVA_HOME should point to a valid JDK installation."
    exit 1
fi

# Run Maven
"$JAVA_HOME/bin/java" \
    $MAVEN_OPTS \
    -classpath "$WRAPPER_JAR" \
    "-Dmaven.home=$MAVEN_BASEDIR" \
    "-Dmaven.multiModuleProjectDirectory=$MAVEN_BASEDIR" \
    "org.apache.maven.wrapper.MavenWrapperMain" "$@"
