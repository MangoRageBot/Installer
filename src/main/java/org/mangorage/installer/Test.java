package org.mangorage.installer;

import org.mangorage.installer.core.Version;

import java.util.ArrayList;
import java.util.List;

public class Test {
    public static void main(String[] args) {
        String input = """
<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>io.github.realmangorage</groupId>
  <artifactId>mangobot</artifactId>
  <versioning>
    <latest>1.0.0-c342</latest>
    <release>1.0.0-c342</release>
    <versions>
      <version>1.0.0-c322</version>
      <version>1.0.0-c323</version>
      <version>1.0.0-c324</version>
      <version>1.0.0-c325</version>
      <version>1.0.0-c327</version>
      <version>1.0.0-c332</version>
      <version>1.0.0-c333</version>
      <version>1.0.0-c334</version>
      <version>1.0.0-c335</version>
      <version>1.0.0-c342</version>
      <version>1.0.343</version>
    </versions>
    <lastUpdated>20231228001020</lastUpdated>
  </versioning>
</metadata>

        """;

        var matchingVersions = Version.parseMetadata(input);

        matchingVersions.forEach(System.out::println);

        System.out.println(Version.getLatestVersion(matchingVersions, "1.0.342").getOriginal());

    }
}
