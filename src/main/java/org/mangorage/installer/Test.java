package org.mangorage.installer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.mangorage.installer.core.types.Installed;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

public class Test {
    public static void main(String[] args) throws FileNotFoundException {
        File file = new File("new/installed.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        var a = gson.fromJson(new FileReader(file), Installed.class);
        System.out.println(a);
    }
}
