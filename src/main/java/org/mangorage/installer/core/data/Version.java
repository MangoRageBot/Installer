package org.mangorage.installer.core.data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Version implements Comparable<Version> {

    public static List<Version> parseMetadata(String metadata) {
        return Version.parseVersions(metadata)
                .stream()
                .filter(a -> a.matches("\\d+\\.\\d+\\.\\d+")) // Only x.y.z...
                .map(Version::of)
                .toList();
    }

    public static Version of(String version) {
        String[] param = version.split("\\.");
        if (param.length != 3) throw new IllegalStateException("Version needs to be major.minor.patch");
        return new Version(Integer.parseInt(param[0]), Integer.parseInt(param[1]), Integer.parseInt(param[2]));
    }

    public static List<String> parseVersions(String input) {
        List<String> matchingVersions = new ArrayList<>();

        Pattern regexPattern = Pattern.compile("<version>(.*?)<\\/version>");
        Matcher matcher = regexPattern.matcher(input);

        while (matcher.find()) {
            matchingVersions.add(matcher.group(1)); // Capture only the version part
        }

        return matchingVersions;
    }

    public static Version getLatestVersion(List<Version> versions, String versionPattern) {
        Pattern pattern = Pattern.compile(versionPattern.replace(".", "\\.").replace("+", ".*"));
        return versions.stream()
                .filter(version -> {
                    Matcher matcher = pattern.matcher(version.toString());
                    return matcher.matches();
                })
                .max(Version::compareTo)
                .orElse(null);
    }

    private final int major;
    private final int minor;
    private final int patch;
    private final String original;

    public Version(int major, int minor, int patch) {
        this(major, minor, patch, "%s.%s.%s".formatted(major, minor, patch));
    }

    public Version(int major, int minor, int patch, String original) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.original = original;
    }

    public String getOriginal() {
        return original;
    }

    public long getValue() {
        return (major * 10000L) + (minor * 100L) + patch;
    }

    @Override
    public String toString() {
        return "%s.%s.%s".formatted(major, minor, patch);
    }

    @Override
    public int compareTo(Version o) {
        return Long.compare(getValue(), o.getValue());
    }
}
