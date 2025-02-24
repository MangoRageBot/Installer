package org.mangorage.installer.core;

import java.util.List;

public record Packages(
        String destination,
        List<Dependency> packages
) {}
