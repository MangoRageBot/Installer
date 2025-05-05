module org.mangorage.installer {
    requires static com.google.gson;
    requires static jopt.simple;
    requires jdk.unsupported;
    requires java.xml;

    uses org.mangorage.installer.Installer;
}