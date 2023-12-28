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

package org.mangorage.installer.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Util {
    private static final String DATA_DIR = "installer/";

    public static File downloadTo(Maven maven, String version, String path) {
        String URL = "%s/%s/%s/%s/%s-%s.jar".formatted(
                maven.repository(),
                maven.groupId()
                        .replace(
                                ".",
                                "/"
                        ),
                maven.artifactId(),
                version,
                maven.artifactId(),
                version
        );

        installUrl(URL, path, false);
        return new File(path);
    }

    public static String downloadMetadata(Maven maven) {
        String url = maven.repository() + "/" + maven.groupId().replace(".", "/") + "/" + maven.artifactId() + "/maven-metadata.xml";
        System.out.println("Downloading Metadata from %s".formatted(url));
        try {
            return convertInputStreamToString(new URL(url).openStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String convertInputStreamToString(InputStream inputStream) {
        String result;
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            // Use Java 8+ streams to collect lines into a single String
            result = bufferedReader.lines().collect(Collectors.joining(System.lineSeparator()));
            inputStream.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    public static void deleteEverythingIfExists(Path path) {
        if (Files.exists(path)) {
            try {
                Files.walk(path).forEach(a -> {
                    if (a.toFile().isFile()) {
                        try {
                            Files.delete(a);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static String parseLatestVersion(String metadata, String versionRange) {
        var versions = Version.parseMetadata(metadata);
        var latest = Version.getLatestVersion(versions, versionRange);
        if (latest != null) {
            return latest.getOriginal();
        }
        return "NO VERSION FOUND";
    }

    public static void saveVersion(String version) {
        String data = """
                {
                  "version": "%s"
                }
                """.formatted(version);
        writeToFile("installer/version.json", data);
    }

    public static void writeToFile(String filePath, String content) {
        Path path = Paths.get(filePath);

        try {
            // Write the string to the file, creating it if it doesn't exist
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
            System.out.println("String successfully written to the file.");
        } catch (IOException e) {
            // Handle the exception if there is an error
            e.printStackTrace();
        }
    }

    public static List<String> readLinesFromInputStream(InputStream inputStream) {
        List<String> lines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                lines.add(line);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return lines;
    }

    public static void installUrl(String url, String destinationPath, boolean resolveName) {
        try {
            // Create a URL object
            URL urlObject = new URL(url);

            // Open a stream from the URL
            try (InputStream inputStream = urlObject.openStream()) {
                // Create the destination path
                String path = destinationPath;
                if (resolveName) {
                    Path file = Path.of(urlObject.toURI().getPath());
                    path = path + "/" + file.getFileName();
                }
                Path destination = Path.of(path);
                if (!Files.exists(destination.getParent())) Files.createDirectories(destination.getParent());
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);

                System.out.println("Installation complete. File saved to: " + destination);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

        } catch (IOException e) {
            System.out.println(url);
            throw new IllegalStateException(e);
        }
    }
}
