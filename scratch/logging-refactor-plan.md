# Logging System Refactor - Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal**: Refactor logging to use industry best practices: proper log levels, async writes, compression, time-based rotation, and per-component filtering.

**Architecture**: Replace binary debug mode with Java logging levels (TRACE/DEBUG/INFO/WARN/ERROR). Add async write queue to DebugLogger. Maintain backwards compatibility with existing `debug: true/false` config.

**Tech Stack**: Java 21, Bukkit Logger, java.util.logging.Level, CompletableFuture for async, GZIP compression

---

## Phase 1: Add Log Levels to LogHelper

**Goal**: Replace binary debug mode with proper log levels while maintaining backwards compatibility.

### Files
- Modify: `src/main/java/com/miracle/arcanesigils/utils/LogHelper.java`
- Modify: `src/main/java/com/miracle/arcanesigils/config/ConfigManager.java`
- Modify: `src/main/resources/config.yml`

### Steps

**Step 1: Add LogLevel enum to LogHelper**

Add after line 12 in LogHelper.java:

```java
/**
 * Log levels matching industry standards.
 */
public enum LogLevel {
    TRACE(0),   // Very detailed flow tracing
    DEBUG(1),   // Diagnostic information
    INFO(2),    // Normal application events
    WARN(3),    // Potentially harmful situations
    ERROR(4);   // Error events

    private final int priority;

    LogLevel(int priority) {
        this.priority = priority;
    }

    public boolean isEnabled(LogLevel threshold) {
        return this.priority >= threshold.priority;
    }

    public static LogLevel fromString(String level) {
        try {
            return valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            return INFO; // Default to INFO if invalid
        }
    }
}
```

**Step 2: Replace debugEnabled with log level fields**

Replace lines 20-21 in LogHelper.java:

```java
private static Logger logger;
private static LogLevel globalLogLevel = LogLevel.INFO; // Default production level
private static final Map<String, LogLevel> componentLogLevels = new HashMap<>();
private static String debugOutput = "BOTH"; // FILE, CONSOLE, or BOTH
```

Add import at top:
```java
import java.util.HashMap;
import java.util.Map;
```

**Step 3: Update refreshDebugSetting for log levels**

Replace lines 39-48 in LogHelper.java:

```java
/**
 * Refresh log level settings from config.
 * Call this after config reloads.
 */
public static void refreshDebugSetting(ArmorSetsPlugin plugin) {
    if (plugin.getConfigManager() == null) {
        return;
    }

    var config = plugin.getConfigManager().getMainConfig();

    // Backwards compatibility: honor legacy debug: true/false
    if (config.contains("settings.debug")) {
        boolean debugEnabled = config.getBoolean("settings.debug", false);
        globalLogLevel = debugEnabled ? LogLevel.DEBUG : LogLevel.INFO;
    }

    // New config: settings.log_level overrides legacy debug flag
    if (config.contains("settings.log_level")) {
        String levelStr = config.getString("settings.log_level", "INFO");
        globalLogLevel = LogLevel.fromString(levelStr);
    }

    // Per-component log levels
    componentLogLevels.clear();
    if (config.contains("settings.log_levels")) {
        var section = config.getConfigurationSection("settings.log_levels");
        if (section != null) {
            for (String component : section.getKeys(false)) {
                String levelStr = section.getString(component, "INFO");
                componentLogLevels.put(component, LogLevel.fromString(levelStr));
            }
        }
    }

    // Debug output routing
    debugOutput = config.getString("settings.debug_logging.output", "BOTH").toUpperCase();
}
```

**Step 4: Add new logging methods with levels**

Add after line 170 in LogHelper.java:

```java
/**
 * Log a message at the specified level.
 * Uses component-specific level if configured, otherwise global level.
 */
public static void log(LogLevel level, String component, String message) {
    // Determine effective log level for this component
    LogLevel threshold = componentLogLevels.getOrDefault(component, globalLogLevel);

    // Skip if below threshold
    if (!level.isEnabled(threshold)) {
        return;
    }

    String formattedMessage = String.format("[%s] [%s] %s", level.name(), component, message);

    // Route based on level and output setting
    if (level.priority >= LogLevel.WARN.priority) {
        // WARN and ERROR always go to console
        if (level == LogLevel.ERROR) {
            logger.severe(formattedMessage);
        } else {
            logger.warning(formattedMessage);
        }
    } else if (level == LogLevel.INFO) {
        logger.info(formattedMessage);
    } else {
        // TRACE and DEBUG respect output routing
        if ("CONSOLE".equals(debugOutput) || "BOTH".equals(debugOutput)) {
            logger.info(formattedMessage);
        }
    }

    // Always write TRACE/DEBUG to file if file output enabled
    if (level.priority <= LogLevel.DEBUG.priority) {
        if ("FILE".equals(debugOutput) || "BOTH".equals(debugOutput)) {
            DebugLogger.log(formattedMessage);
        }
    }
}

/**
 * Log a formatted message at the specified level.
 */
public static void log(LogLevel level, String component, String format, Object... args) {
    log(level, component, String.format(format, args));
}

// Convenience methods for each level
public static void trace(String component, String message) {
    log(LogLevel.TRACE, component, message);
}

public static void trace(String component, String format, Object... args) {
    log(LogLevel.TRACE, component, format, args);
}

public static void debug(String component, String message) {
    log(LogLevel.DEBUG, component, message);
}

public static void debug(String component, String format, Object... args) {
    log(LogLevel.DEBUG, component, format, args);
}

public static void info(String component, String message) {
    log(LogLevel.INFO, component, message);
}

public static void info(String component, String format, Object... args) {
    log(LogLevel.INFO, component, format, args);
}

public static void warn(String component, String message) {
    log(LogLevel.WARN, component, message);
}

public static void warn(String component, String format, Object... args) {
    log(LogLevel.WARN, component, format, args);
}

public static void error(String component, String message) {
    log(LogLevel.ERROR, component, message);
}

public static void error(String component, String format, Object... args) {
    log(LogLevel.ERROR, component, format, args);
}
```

**Step 5: Keep old debug() methods for backwards compatibility**

Replace lines 145-164 in LogHelper.java:

```java
/**
 * @deprecated Use debug(component, message) instead for better filtering
 */
@Deprecated
public static void debug(String message) {
    // Extract component from message if present ([Component] format)
    String component = "General";
    if (message.startsWith("[") && message.contains("]")) {
        int end = message.indexOf("]");
        component = message.substring(1, end);
    }
    debug(component, message);
}

/**
 * @deprecated Use debug(component, format, args) instead
 */
@Deprecated
public static void debug(String format, Object... args) {
    debug(String.format(format, args));
}
```

**Step 6: Update config.yml with new settings**

Add after line 7 in config.yml:

```yaml
  # Global log level (TRACE, DEBUG, INFO, WARN, ERROR)
  # TRACE: Very detailed flow tracing
  # DEBUG: Diagnostic information
  # INFO:  Normal application events (production default)
  # WARN:  Potentially harmful situations
  # ERROR: Error events
  log_level: INFO

  # Per-component log levels (override global level)
  # Useful for debugging specific systems without spam
  log_levels:
    # Examples (uncomment to enable):
    # MarkManager: DEBUG
    # FlowExecutor: TRACE
    # BindsListener: DEBUG
```

Keep existing `debug: true` for backwards compatibility (now processed in refreshDebugSetting).

**Step 7: Test log level filtering**

Manual test plan:
1. Set `log_level: INFO` in config
2. Restart server
3. Verify only INFO/WARN/ERROR messages appear
4. Set `log_levels: { MarkManager: DEBUG }` in config
5. Restart server
6. Verify MarkManager debug messages appear, but others don't

**Step 8: Commit Phase 1**

```bash
git add src/main/java/com/miracle/arcanesigils/utils/LogHelper.java src/main/resources/config.yml
git commit -m "feat: add proper log levels to logging system

- Add LogLevel enum (TRACE/DEBUG/INFO/WARN/ERROR)
- Add per-component log level filtering
- Maintain backwards compatibility with debug: true
- Deprecate old debug() methods in favor of component-based logging"
```

---

## Phase 2: Add Async Writes to DebugLogger

**Goal**: Prevent disk I/O from blocking game thread under high log volume.

### Files
- Modify: `src/main/java/com/miracle/arcanesigils/utils/DebugLogger.java`
- Modify: `src/main/resources/config.yml`

### Steps

**Step 1: Add async queue and background thread**

Add after line 24 in DebugLogger.java:

```java
// Async write queue
private static final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(10000);
private static Thread writerThread;
private static volatile boolean running = false;
```

Add imports at top:
```java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
```

**Step 2: Start background writer thread in initialize()**

Replace lines 45-74 in DebugLogger.java:

```java
public static void initialize(File dataFolder) {
    try {
        // Create logs folder
        logFolder = new File(dataFolder, LOG_FOLDER);
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }

        logFile = new File(logFolder, LOG_FILE);

        // Startup rotation: archive existing log from previous session
        if (logFile.exists() && logFile.length() > 0) {
            rotateLog();
        }

        // Clean up old archived files
        cleanupOldArchives();

        // Open writer in append mode
        writer = new BufferedWriter(
            new FileWriter(logFile, StandardCharsets.UTF_8, true)
        );

        enabled = true;

        // Start async writer thread
        running = true;
        writerThread = new Thread(DebugLogger::writerLoop, "ArcaneSigils-LogWriter");
        writerThread.setDaemon(true); // Don't prevent JVM shutdown
        writerThread.start();

    } catch (IOException e) {
        System.err.println("[ArcaneSigils] Failed to initialize debug logger: " + e.getMessage());
        enabled = false;
    }
}
```

**Step 3: Add writer loop method**

Add after initialize() method (around line 80):

```java
/**
 * Background thread loop that writes queued messages to file.
 */
private static void writerLoop() {
    while (running || !messageQueue.isEmpty()) {
        try {
            // Block for up to 50ms waiting for a message
            String message = messageQueue.poll(50, TimeUnit.MILLISECONDS);

            if (message != null && writer != null) {
                // Add timestamp
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                writer.write(timestamp + " | " + message);
                writer.newLine();

                // Flush after each write for now (can batch later)
                writer.flush();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        } catch (IOException e) {
            System.err.println("[ArcaneSigils] Debug file logging disabled due to I/O error: " + e.getMessage());
            enabled = false;
            closeWriter();
            break;
        }
    }
}
```

**Step 4: Update log() to queue messages asynchronously**

Replace lines 79-102 in DebugLogger.java:

```java
/**
 * Log a debug message to file asynchronously.
 */
public static void log(String message) {
    if (!enabled) {
        return;
    }

    // Check if file size exceeds limit - rotate if needed
    // (Rotation is still synchronous to avoid race conditions)
    if (logFile.exists() && logFile.length() >= maxFileSizeBytes) {
        synchronized (DebugLogger.class) {
            // Double-check after acquiring lock
            if (logFile.exists() && logFile.length() >= maxFileSizeBytes) {
                rotateLog();
                cleanupOldArchives();
            }
        }
    }

    // Queue message for async write
    if (!messageQueue.offer(message)) {
        // Queue full - log dropped message count
        System.err.println("[ArcaneSigils] Debug log queue full - message dropped");
    }
}
```

**Step 5: Update shutdown() to wait for queue to drain**

Replace lines 142-157 in DebugLogger.java:

```java
/**
 * Shutdown the debug logger.
 * Waits for queued messages to be written before closing.
 */
public static void shutdown() {
    enabled = false;
    running = false;

    // Wait for writer thread to finish (max 5 seconds)
    if (writerThread != null) {
        try {
            writerThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    closeWriter();
}
```

**Step 6: Test async writes**

Manual test plan:
1. Set `debug: true` and `log_level: DEBUG` in config
2. Restart server
3. Generate high log volume (spawn entities, activate binds rapidly)
4. Verify server doesn't lag (check /timings)
5. Check logs/ folder - messages should appear with slight delay
6. Verify shutdown waits for queue to drain (check last messages appear in log)

**Step 7: Commit Phase 2**

```bash
git add src/main/java/com/miracle/arcanesigils/utils/DebugLogger.java
git commit -m "feat: add async writes to debug logger

- Add background writer thread with message queue
- Prevent disk I/O from blocking game thread
- Queue size: 10,000 messages (configurable)
- Graceful shutdown waits for queue to drain"
```

---

## Phase 3: Add Time-Based Rotation and Compression

**Goal**: Rotate logs daily and compress archives to save disk space.

### Files
- Modify: `src/main/java/com/miracle/arcanesigils/utils/DebugLogger.java`
- Modify: `src/main/resources/config.yml`

### Steps

**Step 1: Add time-based rotation tracking**

Add after line 31 in DebugLogger.java:

```java
private static long lastRotationTime = System.currentTimeMillis();
private static boolean compressArchives = true;
```

**Step 2: Add compression method**

Add after rotateLog() method (around line 136):

```java
/**
 * Compress a log file to .gz format and delete the original.
 */
private static void compressLogFile(File logFile) {
    if (!compressArchives || !logFile.exists()) {
        return;
    }

    File gzFile = new File(logFile.getParent(), logFile.getName() + ".gz");

    try (FileInputStream fis = new FileInputStream(logFile);
         FileOutputStream fos = new FileOutputStream(gzFile);
         java.util.zip.GZIPOutputStream gzos = new java.util.zip.GZIPOutputStream(fos)) {

        byte[] buffer = new byte[8192];
        int len;
        while ((len = fis.read(buffer)) != -1) {
            gzos.write(buffer, 0, len);
        }

        // Delete original after successful compression
        if (logFile.delete()) {
            System.out.println("[ArcaneSigils] Compressed log: " + logFile.getName() + " -> " + gzFile.getName());
        }

    } catch (IOException e) {
        System.err.println("[ArcaneSigils] Failed to compress log: " + e.getMessage());
    }
}
```

Add import:
```java
import java.io.FileInputStream;
import java.io.FileOutputStream;
```

**Step 3: Update rotateLog() to compress archives**

Replace lines 107-136 in DebugLogger.java:

```java
/**
 * Rotate the log file by renaming it with a timestamp.
 */
public static void rotateLog() {
    if (logFile == null || !logFile.exists()) {
        return;
    }

    try {
        // Close current writer if open
        if (writer != null) {
            writer.close();
            writer = null;
        }

        // Generate archive filename with timestamp
        String timestamp = DATE_FORMAT.format(new Date());
        File archivedLog = new File(logFolder, "debug-" + timestamp + ".log");

        // Rename current log to archived log
        Files.move(logFile.toPath(), archivedLog.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Compress the archived log in background
        if (compressArchives) {
            CompletableFuture.runAsync(() -> compressLogFile(archivedLog));
        }

        // Update rotation time
        lastRotationTime = System.currentTimeMillis();

        // Reopen writer for new log file
        if (enabled) {
            writer = new BufferedWriter(
                new FileWriter(logFile, StandardCharsets.UTF_8, true)
            );
        }

    } catch (IOException e) {
        System.err.println("[ArcaneSigils] Failed to rotate debug log: " + e.getMessage());
    }
}
```

Add import:
```java
import java.util.concurrent.CompletableFuture;
```

**Step 4: Add time-based rotation check to writer loop**

Update writerLoop() method (replace section after poll()):

```java
if (message != null && writer != null) {
    // Check for daily rotation (rotate at midnight or every 24h)
    long now = System.currentTimeMillis();
    if (now - lastRotationTime >= 24 * 60 * 60 * 1000) { // 24 hours
        rotateLog();
        cleanupOldArchives();
    }

    // Add timestamp
    String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    writer.write(timestamp + " | " + message);
    writer.newLine();

    // Flush after each write
    writer.flush();
}
```

**Step 5: Update setConfig to accept compression setting**

Replace lines 38-41 in DebugLogger.java:

```java
/**
 * Set configuration for file rotation and compression.
 * Should be called before initialize() or after config is loaded.
 */
public static void setConfig(int maxFileSizeMB, int maxFiles, boolean compress) {
    maxFileSizeBytes = maxFileSizeMB * 1024L * 1024L;
    maxFilesToKeep = maxFiles;
    compressArchives = compress;
}
```

**Step 6: Update cleanupOldArchives to handle .gz files**

Replace lines 163-187 in DebugLogger.java:

```java
/**
 * Clean up old archived log files.
 * Keeps only the most recent N files (configured by maxFilesToKeep).
 */
private static void cleanupOldArchives() {
    if (logFolder == null || !logFolder.exists()) {
        return;
    }

    // Find all archived debug logs (debug-*.log and debug-*.log.gz)
    File[] archives = logFolder.listFiles((dir, name) ->
        (name.startsWith("debug-") && name.endsWith(".log")) ||
        (name.startsWith("debug-") && name.endsWith(".log.gz"))
    );

    if (archives == null || archives.length <= maxFilesToKeep) {
        return; // Nothing to clean up
    }

    // Sort by last modified time (oldest first)
    Arrays.sort(archives, Comparator.comparingLong(File::lastModified));

    // Delete oldest files beyond the limit
    int filesToDelete = archives.length - maxFilesToKeep;
    for (int i = 0; i < filesToDelete; i++) {
        if (archives[i].delete()) {
            System.out.println("[ArcaneSigils] Deleted old debug log: " + archives[i].getName());
        }
    }
}
```

**Step 7: Update config.yml with compression setting**

Modify debug_logging section in config.yml:

```yaml
  debug_logging:
    output: BOTH
    max_file_size_mb: 10
    max_files_to_keep: 7
    compress_archives: true    # Compress rotated logs to .gz (saves ~90% space)
```

**Step 8: Update ConfigManager to load compression setting**

Modify loadDebugLoggingConfig() in ConfigManager.java:

```java
private void loadDebugLoggingConfig() {
    int maxFileSizeMB = mainConfig.getInt("settings.debug_logging.max_file_size_mb", 10);
    int maxFilesToKeep = mainConfig.getInt("settings.debug_logging.max_files_to_keep", 7);
    boolean compress = mainConfig.getBoolean("settings.debug_logging.compress_archives", true);

    com.miracle.arcanesigils.utils.DebugLogger.setConfig(maxFileSizeMB, maxFilesToKeep, compress);
}
```

**Step 9: Test rotation and compression**

Manual test plan:
1. Set `max_file_size_mb: 1` for fast testing
2. Restart server
3. Generate heavy debug output (spawn many entities)
4. Verify size-based rotation creates debug-[timestamp].log
5. Wait ~30 seconds, verify .log file becomes .log.gz
6. Check file size: .gz should be ~10% of original
7. Verify old archives deleted when exceeding max_files_to_keep

**Step 10: Commit Phase 3**

```bash
git add src/main/java/com/miracle/arcanesigils/utils/DebugLogger.java src/main/java/com/miracle/arcanesigils/config/ConfigManager.java src/main/resources/config.yml
git commit -m "feat: add time-based rotation and compression

- Rotate logs daily (24h) in addition to size-based rotation
- Compress archived logs to .gz (saves ~90% disk space)
- Cleanup handles both .log and .log.gz files
- Compression runs async to avoid blocking"
```

---

## Phase 4: Update High-Volume Call Sites (Optional)

**Goal**: Migrate critical high-volume components to use new log level methods for better filtering.

### Files
- Modify: `src/main/java/com/miracle/arcanesigils/binds/BindsListener.java`
- Modify: `src/main/java/com/miracle/arcanesigils/flow/nodes/EffectNode.java`
- Modify: `src/main/java/com/miracle/arcanesigils/effects/impl/MessageEffect.java`

### Steps

**Step 1: Update BindsListener debug calls**

Example migration in BindsListener.java:

```java
// Old (line 99):
LogHelper.debug("[Binds] Activated bind at slot " + slotOrId);

// New:
LogHelper.debug("Binds", "Activated bind at slot %d", slotOrId);
```

Migrate all 24 debug() calls in BindsListener to use component parameter.

**Step 2: Update EffectNode debug calls**

Example migration in EffectNode.java:

```java
// Old:
LogHelper.debug("[EffectNode] Executing effect: " + effectId);

// New:
LogHelper.debug("EffectNode", "Executing effect: %s", effectId);
```

**Step 3: Update MessageEffect debug calls**

Example migration in MessageEffect.java:

```java
// Old:
LogHelper.debug("[MessageEffect] Sending message to " + player.getName());

// New:
LogHelper.debug("MessageEffect", "Sending message to %s", player.getName());
```

**Step 4: Test component filtering**

Manual test:
1. Set `log_level: INFO` in config
2. Set `log_levels: { Binds: DEBUG }` in config
3. Restart server
4. Activate binds - verify Binds debug messages appear
5. Execute other sigils - verify their debug messages don't appear

**Step 5: Commit Phase 4**

```bash
git add src/main/java/com/miracle/arcanesigils/binds/BindsListener.java src/main/java/com/miracle/arcanesigils/flow/nodes/EffectNode.java src/main/java/com/miracle/arcanesigils/effects/impl/MessageEffect.java
git commit -m "refactor: migrate high-volume components to new logging API

- Update BindsListener to use component-based debug()
- Update EffectNode to use component-based debug()
- Update MessageEffect to use component-based debug()
- Enables per-component log filtering"
```

---

## Phase 5: Documentation and Deployment

**Goal**: Document new logging system and deploy to server.

### Steps

**Step 1: Update CLAUDE.md with logging guidelines**

Add to project CLAUDE.md:

```markdown
## Logging Guidelines

**Use proper log levels:**
```java
LogHelper.trace("Component", "Detailed flow: %s", detail); // Very verbose
LogHelper.debug("Component", "Diagnostic: %s", info);       // Debug sessions only
LogHelper.info("Component", "Event: %s", event);            // Normal operations
LogHelper.warn("Component", "Warning: %s", issue);          // Potential problems
LogHelper.error("Component", "Error: %s", error);           // Serious errors
```

**Production defaults:**
- Global log level: INFO
- Per-component debugging: Use `log_levels` in config.yml
- Never leave `debug: true` on production servers

**Log file management:**
- Size rotation: 10MB default (configurable)
- Time rotation: Daily at midnight
- Compression: Automatic .gz compression of archives
- Retention: 7 files default (configurable)
```

**Step 2: Increment version in pom.xml**

Update version from 1.1.11 to 1.1.12.

**Step 3: Build and deploy**

```bash
# Build
./build.bat

# Deploy
python deploy.py deploy

# Update server config
python deploy.py push "src/main/resources/config.yml" "./plugins/ArcaneSigils/config.yml"
```

**Step 4: Verify on server**

After server restart:
1. Check logs/ folder created
2. Verify debug.log exists (or doesn't, depending on log_level)
3. Test log level changes
4. Verify rotation and compression work

**Step 5: Final commit**

```bash
git add CLAUDE.md pom.xml
git commit -m "docs: update logging guidelines and bump version

- Document new log level system
- Add production logging best practices
- Bump version to 1.1.12"
```

---

## Rollback Plan

If issues occur:

1. **Quick disable**: Set `log_level: ERROR` in config (minimal logging)
2. **Revert async**: Comment out writerThread start in initialize()
3. **Full revert**: Deploy previous JAR version (1.1.11)

---

## Testing Checklist

### Phase 1: Log Levels
- [ ] INFO level filters out DEBUG messages
- [ ] Per-component levels override global level
- [ ] Legacy `debug: true` still works
- [ ] WARN/ERROR always appear in console

### Phase 2: Async Writes
- [ ] No TPS drop during heavy logging
- [ ] Messages appear in log file
- [ ] Shutdown waits for queue drain
- [ ] Queue full handling works

### Phase 3: Rotation & Compression
- [ ] Size-based rotation triggers at threshold
- [ ] Time-based rotation triggers daily
- [ ] Archives compress to .gz
- [ ] Old archives auto-delete
- [ ] .gz files included in cleanup count

### Phase 4: Component Filtering
- [ ] Can enable debug for specific components only
- [ ] Other components respect INFO level
- [ ] Component names extracted correctly

### Phase 5: Production
- [ ] Server starts without errors
- [ ] Default INFO level keeps console clean
- [ ] Can temporarily enable DEBUG for troubleshooting
- [ ] Log files don't cause disk issues

---

## Success Criteria

1. ✅ Production server runs with `log_level: INFO` (clean console)
2. ✅ Can enable per-component DEBUG without spam
3. ✅ Heavy logging doesn't cause TPS lag
4. ✅ Log files auto-rotate and compress
5. ✅ Disk usage under control (max 7 files, compressed)
6. ✅ Backwards compatible with existing config
