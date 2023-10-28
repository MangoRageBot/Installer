/*
 * Copyright (c) 2023. MangoRage
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.mangorage.installer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.mangorage.installer.api.Dependency;
import org.mangorage.installer.api.Maven;
import org.mangorage.installer.api.Version;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class Util {
    private static final String DATA_DIR = "installer/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();




    public static File downloadTo(Maven maven, String version, File dest) {
        String URL = "%s/%s/%s/%s/%s-%s%s".formatted(
                maven.repository(),
                maven.groupId()
                        .replace(
                                ".",
                                "/"
                        ),
                maven.artifactId(),
                version,
                maven.artifactId(),
                version,
                maven.jar()
        );

        try {
            FileUtils.copyURLToFile(new URL(URL), dest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return dest;
    }

    public static String downloadMetadata(Maven maven) {
        String url = maven.repository() + "/" + maven.groupId().replace(".", "/") + "/" + maven.artifactId() + "/maven-metadata.xml";
        try {
            BufferedInputStream in = new BufferedInputStream(new URL(url).openStream());
            return IOUtils.toString(new InputStreamReader(in));
        } catch (IOException e) {
            // handle exception
        }
        return null;
    }

    public static String parseLatestVersion(String metadata) {
        String[] lines = metadata.split("\n");
        for (String line : lines) {
            if (line.contains("<latest>")) {
                return line.substring(line.indexOf("<latest>") + 8, line.indexOf("</latest>"));
            }
        }
        return null;
    }

    public static ModuleRevisionId getMRI(Dependency dependency) {
        return ModuleRevisionId.newInstance(dependency.groupId(), dependency.artifactId(), dependency.version());
    }

    public static void saveVersion(String version) {
        saveObjectToFile(new Version(version), DATA_DIR, "version.json");
    }

    public static Version getVersion() {
        File file = new File("%s/version.json".formatted(DATA_DIR));
        if (!file.getParentFile().exists())
            file.getParentFile().mkdirs();
        if (!file.exists())
            return null;
        return loadJsonToObject("%s/version.json".formatted(DATA_DIR), Version.class);
    }

    public static void saveObjectToFile(Object object, String directory, String fileName) {
        try {
            String jsonData = GSON.toJson(object);

            File dirs = new File(directory);
            if (!dirs.exists() && !dirs.mkdirs()) return;
            Files.writeString(Path.of("%s/%s".formatted(directory, fileName)), jsonData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void deleteFile(String directory, String fileName) {
        try {
            Files.delete(Path.of("%s/%s".formatted(directory, fileName)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T loadJsonToObject(String file, Class<T> cls) {
        try {
            return GSON.fromJson(Files.readString(Path.of(file)), cls);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T loadJsonToObject(File file, Class<T> cls) {
        try {
            return GSON.fromJson(Files.readString(file.toPath()), cls);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
