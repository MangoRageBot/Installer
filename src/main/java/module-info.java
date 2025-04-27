module org.mangorage.installer {
    requires static com.google.gson;
    requires static jopt.simple;
    requires jdk.unsupported;

    uses org.mangorage.installer.Installer;
}