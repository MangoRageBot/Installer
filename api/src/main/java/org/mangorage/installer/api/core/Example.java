package org.mangorage.installer.api.core;

import java.util.List;

public class Example {
    public static void main(String[] args) {
        Datagen.generateDependenciesJson("build/", List.of("org.mangorage:mangobotapi:1.0.0"));
    }
}
