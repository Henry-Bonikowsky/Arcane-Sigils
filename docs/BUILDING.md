# Building the ArmorSets Plugin

This guide explains how to build the plugin from source.

---

## Prerequisites

- **Java 21** or higher
- **Maven 3.6** or higher (Apache Maven)

### Check Your Java Version

```bash
java -version
```

Should output something like: `java version "21.x.x"`

[Download Java 21](https://www.oracle.com/java/technologies/downloads/)

### Check Your Maven Version

```bash
mvn --version
```

Should output: `Apache Maven 3.6.0` or higher

[Download Maven](https://maven.apache.org/download.cgi)

---

## Quick Build

The simplest way to build:

```bash
cd "C:\Users\henry\Programs(self)\Custom ArmorWeapon Plugin"
mvn clean package -DskipTests
```

**Output:** `target/ArmorSets-1.0.0.jar`

---

## Build with Tests

To run tests during build (takes longer):

```bash
mvn clean package
```

---

## Build Details

### What `mvn clean package` does:

1. **Clean** - Removes old build files (`target/` directory)
2. **Compile** - Compiles all Java source files in `src/main/java/`
3. **Resource Processing** - Copies `src/main/resources/` files (config.yml, plugin.yml, etc)
4. **Jar Creation** - Creates executable JAR file with all dependencies
5. **Testing** - Runs tests (skipped with `-DskipTests`)

### Maven Build Stages

```
clean → validate → compile → test → package → verify → install → deploy
                                    ↑ (stops here with 'package')
                          (or here with -DskipTests)
```

---

## Deploy to Server

### 1. Build the Plugin

```bash
mvn clean package -DskipTests
```

### 2. Copy JAR to Server

```bash
copy target\ArmorSets-1.0.0.jar "C:\Users\henry\Minecraft Server\plugins\ArmorSets.jar"
```

**Or manually:**
- Navigate to `target/` folder
- Copy `ArmorSets-1.0.0.jar`
- Paste into `<Server Root>/plugins/`
- Rename to `ArmorSets.jar` (optional but cleaner)

### 3. Restart Server

Stop and restart your Minecraft server. The plugin will load on startup.

### 4. Verify Installation

In-game, run:

```
/as help
```

If you see the help menu, plugin loaded successfully!

---

## Troubleshooting

### "Maven not found"

**Solution:** Install Maven and add to PATH, or use full path:

```bash
C:\apache-maven\bin\mvn clean package -DskipTests
```

### "Java not found"

**Solution:** Install Java 21 and add to PATH

### "Build failed - compilation errors"

**Solution:** Check the error message in console. Usually a missing dependency or Java version issue.

```bash
mvn clean package -X  # -X for debug output
```

### "JAR not in target folder"

**Solution:** Check for build errors in console output. Look for `[ERROR]` lines.

---

## IDE Integration

### IntelliJ IDEA

IntelliJ automatically detects Maven projects:

1. Open the plugin folder as a project
2. Right-click `pom.xml` → "Add as Maven Project"
3. Maven panel appears on right side
4. Double-click `clean` then `package` to build

### Eclipse

1. File → Import → Maven → Existing Maven Projects
2. Select the plugin folder
3. Click Finish
4. Right-click project → Run As → Maven Build
5. Enter goals: `clean package -DskipTests`

### VS Code

Install "Extension Pack for Java" and Maven will be auto-detected.

---

## Build Artifacts

After building, check the `target/` folder:

```
target/
├── ArmorSets-1.0.0.jar          ← This is what you deploy
├── classes/                      ← Compiled Java classes
├── maven-archiver/               ← Maven metadata
├── maven-status/                 ← Build status info
└── lib/                          ← Dependencies
```

---

## Manual Build (No Maven)

If you don't have Maven, you can compile manually with Java:

```bash
cd src/main/java
javac -d ../../target/classes com/zenax/armorsets/*.java com/zenax/armorsets/**/*.java
```

**Not recommended** - Maven handles dependencies and packaging automatically.

---

## Build Flags

### Skip Tests

```bash
mvn clean package -DskipTests
```

Faster, skips test phase. Use for development.

### Debug Output

```bash
mvn clean package -X
```

Very verbose output for troubleshooting.

### Offline Build

```bash
mvn clean package -o
```

Uses cached dependencies, no internet needed.

### Update Dependencies

```bash
mvn clean package -U
```

Downloads latest versions of dependencies.

---

## Next Steps

After building:

1. ✅ Copy JAR to server `plugins/` folder
2. ✅ Restart server
3. ✅ Check console for load messages
4. ✅ Run `/as help` to verify
5. ✅ Configure armor sets in `plugins/ArmorSets/` folder

See **ADMIN_GUIDE.md** for configuration instructions.

