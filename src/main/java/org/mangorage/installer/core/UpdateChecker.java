package org.mangorage.installer.core;

import org.mangorage.installer.core.data.Packages;
import org.mangorage.installer.core.data.Util;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;

public final class UpdateChecker extends TimerTask {
    public static void startChecker(final Packages packages, final int freq) {
        LogUtil.println("Starting Update Checker... Checks every " + freq + "ms");
        new UpdateChecker(packages, freq);
    }

    private final Map<String, String> lastUpdated = new HashMap<>();

    private final Packages packages;
    private final Timer timer = new Timer();

    UpdateChecker(final Packages packages, final int freq) {
        this.packages = packages;
        Executors.newSingleThreadExecutor().execute(() -> start(freq));
    }

    void start(final int freq) {
        LogUtil.println("Started Update Checker.");
        packages
                .packages()
                .stream()
                .forEach(dependency -> {
                    final var metadata = Util.downloadMetadata(dependency.getMaven());
                    final var lastUpdatedTime = Util.getLastUpdated(metadata);
                    lastUpdated.put(dependency.target(), lastUpdatedTime);
                });

        timer.scheduleAtFixedRate(this, freq, freq);
    }

    @Override
    public void run() {
        packages
                .packages()
                .stream()
                .forEach(dependency -> {
                    final var metadata = Util.downloadMetadata(dependency.getMaven());
                    final var lastUpdatedTime = Util.getLastUpdated(metadata);
                    final var lastCheckedUpdatedTime = lastUpdated.get(dependency.target());
                    if (lastCheckedUpdatedTime != null) {
                        if (!lastCheckedUpdatedTime.matches(lastUpdatedTime)) {
                            LogUtil.println("Exiting... Found update for " + dependency.target());
                            System.exit(0);
                        }
                    }
                });
    }
}
