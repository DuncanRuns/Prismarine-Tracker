package xyz.duncanruns.prismarinetracker;

import xyz.duncanruns.jingle.util.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ResetTracker {
    private final Path instancePath;
    private final Path counterPath;
    private long lastMTime = -1;
    private int lastCount;

    public ResetTracker(Path instancePath, AtumCounterType type) {
        this.instancePath = instancePath;
        this.counterPath = instancePath.resolve("config").resolve("mcsr").resolve("atum").resolve(type.fileName);
    }

    public int countNewResets() {
        if (!Files.exists(this.counterPath)) return 0;
        try {
            long mTime = Files.getLastModifiedTime(this.counterPath).toMillis();
            if (mTime == lastMTime) return 0;
            boolean shouldOutput = lastMTime != -1;
            lastMTime = mTime;
            int count = Integer.parseInt(FileUtil.readString(this.counterPath));
            int newResets = Math.max(0, count - lastCount);
            lastCount = count;
            if (shouldOutput) return newResets;
        } catch (IOException | NumberFormatException ignored) {
        }
        return 0;
    }

    public boolean isOf(Path instancePath) {
        return this.instancePath.equals(instancePath);
    }

    public enum AtumCounterType {
        BENCHMARK("benchmark-resets.txt"),
        DEMO("demo-attempts.txt"),
        RSG("rsg-attempts.txt"),
        SSG("ssg-attempts.txt");

        private final String fileName;

        AtumCounterType(String fileName) {
            this.fileName = fileName;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Path instancePath = Paths.get("M:\\MC Instances\\Speedrun 1.15\\.minecraft");
        ResetTracker tracker = new ResetTracker(instancePath, AtumCounterType.RSG);
        while (true) {
            int newResets = tracker.countNewResets();
            if (newResets > 0) {
                System.out.println("New Resets: " + newResets);
            }
            Thread.sleep(100);
        }
    }
}
