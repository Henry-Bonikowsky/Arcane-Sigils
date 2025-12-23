# Build Instructions

## Prerequisites
- Java 21+ (JDK)
- Maven 4.0.0-rc-5 (installed at ~/tools)

## Maven Location

Maven 4.0.0-rc-5 installed at:
```
C:\Users\henry\tools\apache-maven-4.0.0-rc-5\bin\mvn.cmd
```

**Added to PATH in ~/.bashrc** - new Claude sessions should have `mvn.cmd` available.

## Build Commands

### Main Plugin (from Arcane Sigils directory)
```bash
mvn.cmd clean package -DskipTests
```

### Dungeons Addon (from addons/dungeons directory)
```bash
cd addons/dungeons && mvn.cmd clean package -DskipTests
```

## Output Location

- Main plugin: `target/ArcaneSigils-*.jar`
- Dungeons addon: `target/addons/dungeons-addon-*.jar`

## For Claude Code Sessions

Maven and MAVEN_OPTS should be set in ~/.bashrc. If not, export them first:
```bash
export PATH="$PATH:/c/Users/henry/tools/apache-maven-4.0.0-rc-5/bin"
export MAVEN_OPTS="--enable-native-access=ALL-UNNAMED"
```

### Build main plugin:
```bash
cd "C:/Users/henry/Programs(self)/Arcane Sigils" && mvn.cmd clean package -DskipTests
```

### Build dungeons addon:
```bash
cd "C:/Users/henry/Programs(self)/Arcane Sigils/addons/dungeons" && mvn.cmd clean package -DskipTests
```

### Build both:
```bash
cd "C:/Users/henry/Programs(self)/Arcane Sigils" && mvn.cmd clean package -DskipTests && cd addons/dungeons && mvn.cmd clean package -DskipTests
```

## Project Structure

```
Arcane Sigils/
├── pom.xml                    # Main plugin POM
├── src/main/java/             # Main plugin source
├── target/
│   ├── ArcaneSigils-*.jar     # Main plugin JAR
│   └── addons/
│       └── dungeons-addon-*.jar  # Dungeons addon JAR
├── addons/
│   └── dungeons/
│       ├── pom.xml            # Dungeons addon POM (independent)
│       └── src/main/java/     # Dungeons source
└── BUILD.md                   # This file
```

## Deployment

Copy the JARs to the Minecraft server plugins folder:
- `target/ArcaneSigils-*.jar` → `plugins/`
- `target/addons/dungeons-addon-*.jar` → `plugins/ArcaneSigils/addons/`
