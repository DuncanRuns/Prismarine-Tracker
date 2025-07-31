package xyz.duncanruns.prismarinetracker;

import com.google.gson.*;
import org.apache.logging.log4j.Level;
import xyz.duncanruns.jingle.Jingle;
import xyz.duncanruns.jingle.instance.InstanceState;
import xyz.duncanruns.jingle.instance.OpenedInstance;
import xyz.duncanruns.jingle.plugin.PluginEvents;
import xyz.duncanruns.jingle.util.ExceptionUtil;
import xyz.duncanruns.jingle.util.FileUtil;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class PrismarineTracker {
    private static PlaySession session = new PlaySession();
    private static WatchService recordsWatcher = null;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path TRACKER_DIR = Jingle.FOLDER.resolve("prismarine-tracker");
    public static final Path SESSIONS_DIR = TRACKER_DIR.resolve("sessions");
    public static final Path OUTPUT_DIR = TRACKER_DIR.resolve("output");
    private static final Path SESSION_FILE_PATH = TRACKER_DIR.resolve("session.json");
    public static final Path RUNS_DIR = TRACKER_DIR.resolve("runs");
    private static final Path RECORDS_FOLDER = Paths.get(System.getProperty("user.home")).resolve("speedrunigt").resolve("records");

    private static long lastTick = 0;
    private static boolean shouldSave = false;
    private static boolean startedPlaying = false;

    private static ResetTracker resetTracker = null;

    private static long timeMin = Long.MAX_VALUE;
    private static long timeMax = 0;

    /**
     * Returned object should not be modified.
     */
    public static PlaySession getCurrentSession() {
        return session;
    }

    public static void init() {
        try {
            recordsWatcher = FileSystems.getDefault().newWatchService();
            RECORDS_FOLDER.register(recordsWatcher, StandardWatchEventKinds.ENTRY_CREATE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!Files.isDirectory(SESSIONS_DIR)) {
            try {
                Files.createDirectories(SESSIONS_DIR);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (Files.exists(SESSION_FILE_PATH)) {
            String s;
            try {
                s = FileUtil.readString(SESSION_FILE_PATH);
                PlaySession lastSession = GSON.fromJson(s, PlaySession.class);
                if (lastSession.runsWithGold > 0 && (System.currentTimeMillis() - lastSession.sessionEndTime < 300_000)) {
                    Jingle.log(Level.INFO, "(Prismarine Tracker) Last session was less than 5 minutes ago so it will be continued.");
                    session = lastSession;
                }
            } catch (IOException | JsonSyntaxException | NullPointerException e) {
                Jingle.log(Level.WARN, "(Prismarine Tracker) Last session couldn't be recovered, so a new one will be started");
            }
        }

        try {
            moveOldSessionFiles();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            saveOutputFiles();
        } catch (IOException e) {
            Jingle.log(Level.ERROR, "(Prismarine Tracker) Failed to save output files: " + ExceptionUtil.toDetailedString(e));
        }

        PluginEvents.END_TICK.register(PrismarineTracker::tick);
        PluginEvents.STATE_CHANGE.register(() -> Jingle.getMainInstance().ifPresent(instance -> {
            if (instance.stateTracker.isCurrentState(InstanceState.INWORLD)) {
                startedPlaying = true;
            }
        }));
    }

    private static void updateResets() {
        OpenedInstance instance = Jingle.getMainInstance().orElse(null);
        if (instance == null) return;
        if (!instance.versionString.equals("1.15.2")) return;

        if (resetTracker == null || !resetTracker.isOf(instance.instancePath)) {
            resetTracker = new ResetTracker(instance.instancePath, ResetTracker.AtumCounterType.RSG);
            resetTracker.countNewResets();
        }
        if (!(timeMax != 0 && timeMin != Long.MAX_VALUE)) return;

        int newResets = resetTracker.countNewResets();
        if (newResets == 0) return;

        session.resets += newResets;
        updateLastActivity();
    }

    private static void moveOldSessionFiles() throws IOException {
        Pattern sessionFilePattern = Pattern.compile("\\d+\\.json");
        Files.list(TRACKER_DIR)
                .map(p -> p.getFileName().toString())
                .filter(s -> sessionFilePattern.matcher(s).matches())
                .forEach(s -> {
                    try {
                        Files.move(TRACKER_DIR.resolve(s), SESSIONS_DIR.resolve(s));
                        Jingle.log(Level.INFO, "Session file " + s + " moved to new sessions folder.");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public static void stop() {
        tickInternal();
        if (session.runsWithGold > 0) {
            trySave();
        }
    }

    private static void trySave() {
        try {
            save();
            saveOutputFiles();
        } catch (IOException e) {
            Jingle.log(Level.ERROR, "(Prismarine Tracker) Failed to save session: " + ExceptionUtil.toDetailedString(e));
        }
    }

    private static void saveOutputFiles() throws IOException {
        PlaySession.CalculatedStats cs = session.toCalculatedStats();

        if (!Files.isDirectory(OUTPUT_DIR)) {
            Files.createDirectories(OUTPUT_DIR);
        }

        for (Field f : cs.getClass().getDeclaredFields()) {
            try {
                FileUtil.writeString(OUTPUT_DIR.resolve(f.getName() + ".txt"), f.get(cs).toString());
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void save() throws IOException {
        session.sessionEndTime = System.currentTimeMillis();
        String toWrite = GSON.toJson(session);
        FileUtil.writeString(SESSION_FILE_PATH, toWrite);
        FileUtil.writeString(SESSIONS_DIR.resolve(session.sessionStartTime + ".json"), toWrite);
    }

    private static void processJsonFile(Path recordPath) throws IOException {
        JsonObject json = GSON.fromJson(FileUtil.readString(recordPath), JsonObject.class);
        processJson(json);
    }

    private static void processJson(JsonObject json) {
        // If non-survival, cheats, or coop, or not random seed, don't track
        if (json.get("default_gamemode").getAsInt() != 0) return;
        if (!Objects.equals(json.get("run_type").getAsString(), "random_seed")) return;
        if (!Objects.equals(json.get("mc_version").getAsString(), "1.15.2")) return;
        if (json.get("is_coop").getAsBoolean()) return;

        long finalRta = json.get("final_rta").getAsLong();
        long date = System.currentTimeMillis() - finalRta; // better than record's date, that shit sucks!
        updateActionTimes(finalRta, date);

        long openToLanTime = 0;
        boolean hasOpenedToLan = false;
        try {
            openToLanTime = json.get("open_lan").getAsLong();
            hasOpenedToLan = true;
        } catch (Exception ignored) {
        }

        if (!hasOpenedToLan && json.get("is_cheat_allowed").getAsBoolean())
            return; // is_cheat_allowed will be true when open to lan and coping

        if (startedPlaying) session.worldsEntered++;

        Map<String, Long> timeLineEvents = new HashMap<>();

        for (JsonElement event : json.get("timelines").getAsJsonArray()) {
            JsonObject eventJson = event.getAsJsonObject();
            String name = eventJson.get("name").getAsString();
            long rta = eventJson.get("rta").getAsLong();
            long igt = eventJson.get("igt").getAsLong();
            if (hasOpenedToLan && rta > openToLanTime) {
                continue;
            }
            PrismarineLogger.queueLog(date + rta, name + " " + rta + " " + igt);
            timeLineEvents.put(name, igt);
        }

        if (json.get("is_completed").getAsBoolean() && (!hasOpenedToLan || (openToLanTime > finalRta))) {
            session.runsFinished++;
            session.runFinishTimes.add(json.get("retimed_igt").getAsLong());
            tryMakeRunFile(json, timeLineEvents);
        }

        countRunsWithStuffStats(timeLineEvents);

        if (timeLineEvents.containsKey("enter_end")) {
            session.endEnterTimes.add(timeLineEvents.get("enter_end"));
        }

        if (!timeLineEvents.containsKey("pick_gold_block")) return;

        if (timeLineEvents.containsKey("trade_with_villager")) {
            countRunsWithPearlsStat(json);
        }

        if (timeLineEvents.containsKey("enter_stronghold") && timeLineEvents.containsKey("trade_with_villager")
                && timeLineEvents.get("enter_stronghold") > timeLineEvents.get("trade_with_villager")
                && timeLineEvents.get("enter_stronghold") > timeLineEvents.get("enter_nether")
                && timeLineEvents.get("enter_stronghold") > timeLineEvents.get("pick_gold_block")
        ) {
            session.strongholdEnterTimes.add(timeLineEvents.get("enter_stronghold"));
        }


        // If there's a village enter, and it came before monument, it's not regular insomniac, don't count times for averages.
        if (timeLineEvents.containsKey("found_villager") && timeLineEvents.get("found_villager") < timeLineEvents.get("pick_gold_block"))
            return;

        session.goldBlockPickupTimes.add(timeLineEvents.get("pick_gold_block"));

        // If there's a nether enter and a village enter, and the nether enter came before the village enter, don't count the rest of the times (Monument is still fine).
        if (timeLineEvents.containsKey("found_villager") && timeLineEvents.containsKey("enter_nether") && timeLineEvents.get("enter_nether") < timeLineEvents.get("found_villager"))
            return;

        if (!timeLineEvents.containsKey("found_villager")) return;
        session.villageEnterTimes.add(timeLineEvents.get("found_villager"));

        if (!timeLineEvents.containsKey("enter_nether")) return;
        session.netherEnterTimes.add(timeLineEvents.get("enter_nether"));

        if (!timeLineEvents.containsKey("enter_fortress")) return;
        session.fortressEnterTimes.add(timeLineEvents.get("enter_fortress"));

        if (!timeLineEvents.containsKey("nether_travel")) return;
        session.netherExitTimes.add(timeLineEvents.get("nether_travel"));
    }

    private static void updateActionTimes(long finalRta, long date) {
        if (finalRta == 0) {
            timeMin = Math.min(date, timeMin);
            timeMax = Math.max(date, timeMax);
        } else {
            timeMin = Math.min(date, timeMin);
            timeMax = Math.max(date + finalRta, timeMax);
        }
    }

    private static void tryMakeRunFile(JsonObject json, Map<String, Long> timeLineEvents) {
        try {
            makeRunFile(json, timeLineEvents);
        } catch (Exception e) {
            Jingle.log(Level.ERROR, "Failed to make run file: " + ExceptionUtil.toDetailedString(e));
        }
    }

    private static void makeRunFile(JsonObject json, Map<String, Long> timeLineEvents) throws IOException {
        if (!Files.exists(RUNS_DIR)) {
            Files.createDirectories(RUNS_DIR);
        }

        long date = json.get("date").getAsLong();
        Path runPath = RUNS_DIR.resolve(date + ".json");
        CompletedRun cr = new CompletedRun();

        cr.date = date;
        cr.mineMonument = timeLineEvents.getOrDefault("pick_gold_block", -1L);
        cr.villageEnter = timeLineEvents.getOrDefault("found_villager", -1L);
        cr.firstTrade = timeLineEvents.getOrDefault("trade_with_villager", -1L);
        cr.netherEnter = timeLineEvents.getOrDefault("enter_nether", -1L);
        cr.fortressEnter = timeLineEvents.getOrDefault("enter_fortress", -1L);
        cr.netherExit = timeLineEvents.getOrDefault("nether_travel", -1L);
        cr.strongholdEnter = timeLineEvents.getOrDefault("enter_stronghold", -1L);
        cr.endEnter = timeLineEvents.getOrDefault("enter_end", -1L);

        cr.completionIGT = json.get("final_igt").getAsLong();
        cr.completionRTA = json.get("final_rta").getAsLong();
        cr.completionRetime = json.get("retimed_igt").getAsLong();

        FileUtil.writeString(runPath, GSON.toJson(cr));
    }

    private static void countRunsWithPearlsStat(JsonObject json) {
        if (!json.has("stats")) return;
        JsonObject exploringJson = json.getAsJsonObject("stats");
        Optional<String> uuid = exploringJson.keySet().stream().findAny();

        if (!uuid.isPresent()) return;
        exploringJson = exploringJson.getAsJsonObject(uuid.get());

        if (!exploringJson.has("stats")) return;
        exploringJson = exploringJson.getAsJsonObject("stats");

        if (!exploringJson.has("minecraft:crafted")) return;
        exploringJson = exploringJson.getAsJsonObject("minecraft:crafted");

        if (!exploringJson.has("minecraft:ender_pearl")) return;
        int pearls = exploringJson.get("minecraft:ender_pearl").getAsInt();
        if (pearls >= 10) {
            session.runsWith10Pearls++;
        }
    }

    private static void countRunsWithStuffStats(Map<String, Long> timeLineEvents) {
        if ((timeLineEvents.containsKey("enter_end"))) session.runsWithEndEnter++;

        if (!timeLineEvents.containsKey("pick_gold_block")) return;
        session.runsWithGold++;
        shouldSave = true;

        if (timeLineEvents.containsKey("found_villager")) session.runsWithVillage++;
        if (timeLineEvents.containsKey("trade_with_villager")) session.runsWithTrading++;
        if (timeLineEvents.containsKey("enter_nether")) {
            session.runsWithNether++;
            if (timeLineEvents.containsKey("enter_stronghold")) session.runsWithStronghold++;
        }
        if (timeLineEvents.containsKey("enter_fortress")) session.runsWithFort++;
        if (timeLineEvents.containsKey("nether_travel") || timeLineEvents.containsKey("enter_end"))
            session.runsWithNetherExit++;
    }

    private static void tick() {
        if (System.currentTimeMillis() - lastTick > 500) {
            lastTick = System.currentTimeMillis();
        } else {
            return;
        }
        new Thread(PrismarineTracker::tickInternal, "prismarine-tracker-tick").start();
    }

    private static synchronized void tickInternal() {
        updateResets();

        timeMin = Long.MAX_VALUE;
        timeMax = 0;

        WatchKey watchKey = recordsWatcher.poll();
        if (watchKey == null)
            return; // It's ok to cancel the shouldSave stuff because that can't be true unless some json processing happens

        for (WatchEvent<?> event : watchKey.pollEvents()) {
            if (event.kind() != StandardWatchEventKinds.ENTRY_CREATE || !(event.context() instanceof Path))
                continue;
            try {
                processJsonFile(RECORDS_FOLDER.resolve((Path) event.context()));
            } catch (IOException | JsonSyntaxException | NullPointerException e) {
                Jingle.log(Level.ERROR, "Failed to process a world: " + ExceptionUtil.toDetailedString(e));
            }
        }
        watchKey.reset();
        try {
            PrismarineLogger.flushLog();
        } catch (IOException e) {
            Jingle.log(Level.ERROR, "Failed to save log: " + ExceptionUtil.toDetailedString(e));
        }

        if (timeMax != 0 && timeMin != Long.MAX_VALUE) {
            updateLastActivity();
        }

        if (shouldSave) {
            shouldSave = false;
            trySave();
        }
    }

    private static void clearWatcher() {
        WatchKey key = recordsWatcher.poll();
        if (key == null) return;
        key.pollEvents();
        key.reset();
    }

    private static synchronized void updateLastActivity() {
        if (!startedPlaying) return;
        long timeSinceLastActivity = timeMin - session.lastActivity;
        if (timeSinceLastActivity > 120_000 /*2 Minutes*/) {
            session.breaks.add(timeSinceLastActivity);
            PrismarineLogger.queueLog(System.currentTimeMillis(), "finish_break " + timeSinceLastActivity);
        }
        session.lastActivity = timeMax;
    }

    public static void clearSession() throws IOException {
        Path potentialPath = SESSIONS_DIR.resolve(session.sessionStartTime + ".json");
        if (session.runsWithGold == 0) {
            Files.deleteIfExists(potentialPath);
        }
        Files.deleteIfExists(SESSION_FILE_PATH);
        session = new PlaySession();
        clearWatcher();
        startedPlaying = false;
    }

}
