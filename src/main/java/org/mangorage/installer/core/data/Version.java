package org.mangorage.installer.core.data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Version implements Comparable<Version> {

    public static List<Version> parseMetadata(String metadata) {
        return Version.parseVersions(metadata).stream().map(a -> {
            if (a.contains("c"))
                return Version.of(Version.transformVersion(a), a);
            return Version.of(a);
        }).toList();
    }

    public static Version parseVersion(String version) {
        if (version.contains("c"))
            return Version.of(Version.transformVersion(version), version);
        return Version.of(version);
    }

    public static Version of(String version) {
        String[] param = version.split("\\.");
        if (param.length != 3) throw new IllegalStateException("Version needs to be major.minor.patch");
        return new Version(Integer.parseInt(param[0]), Integer.parseInt(param[1]), Integer.parseInt(param[2]));
    }

    public static Version of(String version, String original) {
        String[] param = version.split("\\.");
        if (param.length != 3) throw new IllegalStateException("Version needs to be major.minor.patch");
        return new Version(Integer.parseInt(param[0]), Integer.parseInt(param[1]), Integer.parseInt(param[2]), original);
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

    public static List<String> parseVersions(String input) {
        List<String> matchingVersions = new ArrayList<>();

        // Define the pattern
        Pattern regexPattern = Pattern.compile("<version>(.*?)<\\/version>");

        // Create a matcher with the input string
        Matcher matcher = regexPattern.matcher(input);

        // Find all matches in the input string
        while (matcher.find()) {
            matchingVersions.add(matcher.group(1)); // Capture only the version part
        }

        return matchingVersions;
    }

    public static String transformVersion(String version) {
        // Define the pattern
        Pattern regexPattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+)-c(\\d+)");

        // Create a matcher with the input version
        Matcher matcher = regexPattern.matcher(version);

        // Check if the pattern matches
        if (matcher.matches()) {
            // Combine the matched groups, removing leading zeros from the third part
            String mainPart = matcher.group(1);
            String thirdPart = matcher.group(2).replaceFirst("^0+(?!$)", "");
            return String.format("%s%s", mainPart.substring(0, mainPart.length() - 1), thirdPart);
        } else {
            // Return the original version if no match
            return version;
        }
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
