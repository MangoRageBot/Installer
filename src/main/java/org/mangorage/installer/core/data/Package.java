package org.mangorage.installer.core.data;

public record Package(
        String url,
        String group,
        String artifact,
        String version,
        String target,
        String destination,
        boolean checkUpdate
) {
    public static String fix(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static String fixDot(String value) {
        return value.replaceAll("\\.", "/");
    }

    public Maven getMaven() {
        return new Maven(
                url,
                group,
                artifact
        );
    }

    public String getDownloadURL() {
        return "%s/%s/%s/%s/%s".formatted(fix(url), fixDot(group), artifact, version, target);
    }

    public String getDestination(String destinationPath) {
        return this.destination == null ? destinationPath : this.destination;
    }
}
