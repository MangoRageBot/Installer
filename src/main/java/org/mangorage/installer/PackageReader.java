package org.mangorage.installer;

import org.mangorage.installer.api.Maven;
import org.mangorage.installer.api.Package;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class PackageReader {
    private static final Package EMPTY = new Package("EXAMPLE_PACKAGE", "", Maven.EMPTY);
    public static Package getPackage() {
        File packageUrl = new File("installer/package.txt");
        if (!packageUrl.exists()) {
            System.out.println(packageUrl.getAbsolutePath());
            // Handle creating package.json
            return EMPTY;
        } else {
            try {
                HashMap<String, String> MAP = new HashMap<>();
                List<String> lines = Util.readLinesFromInputStream(packageUrl.toURI().toURL().openStream());
                lines.forEach(a -> {
                    var res = a.split("=");
                    if (res.length == 2) {
                        MAP.put(res[0], res[1]);
                    }
                });
                var a=1;
                return new Package(
                        MAP.get("packageName"),
                        MAP.get("packageDest"),
                        new Maven(
                                MAP.get("repository"),
                                MAP.get("groupId"),
                                MAP.get("artifactId"),
                                MAP.get("version"),
                                MAP.get("jar")
                        )
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
    }
}
