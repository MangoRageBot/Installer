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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// Create's a file in the directory given
// A simple dependencies.json file
// Can also create an ivySettings xml file (TODO)
public class Datagen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static void generateDependenciesJson(String directory, List<String> dependencies) {
        // args -> [0] = directory
        // args -> [1+] = dependencies


        Path path = Paths.get(directory);
        File dir = path.toFile();

        if (!dir.isDirectory())
            throw new IllegalArgumentException("Must supply a directory, not a file!");

        System.out.println("Generating dependencies.json file... to %s".formatted(dir.getAbsolutePath()));

        saveObjectToFile(new DependencyList(
                dependencies.stream()
                .map(dep -> dep.split(":"))
                .map(dep -> new Dependency(dep[0], dep[1], dep[2]))
                .toList()
        ), dir.getAbsolutePath(), "dependencies.json");
    }

    public static void generateIvySettingsXml(String directory, List<String> repositories) {

        Path path = Paths.get(directory);
        File dir = path.toFile();

        if (!dir.isDirectory())
            throw new IllegalArgumentException("Must supply a directory, not a file!");

        System.out.println("Generating ivySettings.xml file... to %s".formatted(dir.getAbsolutePath()));

        StringBuilder builder = new StringBuilder();
        AtomicInteger counter = new AtomicInteger(0);

        builder.append("<ivysettings>\n");
        builder.append("\t<settings defaultResolver=\"chain-1\"/>\n");
        builder.append("\t<resolvers>\n");
        builder.append("\t\t<ibiblio name=\"central\" m2compatible=\"true\"/>\n");
        repositories.forEach(repo -> {
            builder.append("\t\t<ibiblio name=\"repo-%s\" m2compatible=\"true\" root=\"%s\"/>\n".formatted(counter.getAndIncrement(), repo));
        });
        builder.append("\t\t<filesystem name=\"custom\" checkmodified=\"true\">\n");
        builder.append("\t\t\t<ivy pattern=\"${custom.base.dir}/ivy/[artifact]-[revision].ivy\"/>\n");
        builder.append("\t\t\t<artifact pattern=\"${custom.base.dir}/[artifact]-[revision].[ext]\"/>\n");
        builder.append("\t\t</filesystem>\n");
        builder.append("\t\t<chain name=\"chain-1\">\n");
        builder.append("\t\t\t<resolver ref=\"central\"/>\n");
        for (int i = 0; i < counter.get(); i++) {
            builder.append("\t\t\t<resolver ref=\"repo-%s\"/>\n".formatted(i));
        }
        builder.append("\t\t</chain>\n");
        builder.append("\t</resolvers>\n");
        builder.append("</ivysettings>\n");

        try {
            Files.writeString(Path.of("%s/ivysettings.xml".formatted(directory)), builder.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
