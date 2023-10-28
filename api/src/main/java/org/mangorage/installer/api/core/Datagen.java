package org.mangorage.installer.api.core;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.mangorage.installer.api.Dependency;
import org.mangorage.installer.api.DependencyList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

// Create's a file in the directory given
// A simple dependencies.json file
// Can also create an ivySettings xml file (TODO)
public class Datagen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static void main(String[] args) {
        // args -> [0] = directory
        // args -> [1+] = dependencies

        if (args.length < 2)
            throw new IllegalArgumentException("Not enough arguments!");

        Path path = Paths.get(args[0]);
        File dir = path.toFile();

        if (!dir.isDirectory())
            throw new IllegalArgumentException("Must supply a directory, not a file!");

        System.out.println("Generating dependencies.json file... to %s".formatted(dir.getAbsolutePath()));

        saveObjectToFile(new DependencyList(
                Arrays.stream(Arrays.copyOfRange(args, 1, args.length))
                .map(dep -> dep.split(":"))
                .map(dep -> new Dependency(dep[0], dep[1], dep[2]))
                .toList()
        ), dir.getAbsolutePath(), "dependencies.json");
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
}
