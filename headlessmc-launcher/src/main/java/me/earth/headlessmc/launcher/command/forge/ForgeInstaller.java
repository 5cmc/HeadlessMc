package me.earth.headlessmc.launcher.command.forge;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.var;
import me.earth.headlessmc.launcher.Launcher;
import me.earth.headlessmc.launcher.files.FileManager;
import me.earth.headlessmc.launcher.instrumentation.ResourceExtractor;
import me.earth.headlessmc.launcher.java.Java;
import me.earth.headlessmc.launcher.util.IOUtil;
import me.earth.headlessmc.launcher.util.JsonUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@CustomLog
@RequiredArgsConstructor
public class ForgeInstaller {
    private static final String FORGE_CLI = "forge-cli.jar";
    private static final String BASE_URL =
        "https://maven.minecraftforge.net/net/minecraftforge/forge/";

    private final Launcher launcher;

    public void install(ForgeVersion version, FileManager fileManager)
        throws IOException {
        val cli = new ResourceExtractor(fileManager, FORGE_CLI).extract();
        val fileName = "forge-" + version.getFullName() + "-installer.jar";
        val url = BASE_URL + version.getFullName() + "/" + fileName;
        log.debug("Downloading Installer from " + url);
        val installer = fileManager.create(fileName);
        downloadInstaller(version, installer, fileName);

        val java = launcher.getJavaService().findBestVersion(8);
        val mc = launcher.getMcFiles();
        val command = getCommand(java, mc.getBase(), cli, installer);

        ensureJsonExists("launcher_profiles.json", mc);
        ensureJsonExists("launcher_profiles_microsoft_store.json", mc);

        val process = new ProcessBuilder()
            .directory(fileManager.getBase())
            .command(command)
            .inheritIO()
            .start();

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Failed to install Forge "
                                          + version.getFullName()
                                          + ", exitCode: " + exitCode);
            } else {
                launcher.log("Forge " + version.getFullName()
                                 + " installed successfully!");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread has been interrupted!");
            throw new IllegalStateException(e);
        }
    }

    protected List<String> getCommand(Java java, File mc, File cli, File fml) {
        val command = new ArrayList<String>();
        command.add(java.getExecutable());
        command.add("-jar");
        command.add(cli.getAbsolutePath());
        command.add("--installer");
        command.add(fml.getAbsolutePath());
        command.add("--target");
        command.add(mc.getAbsolutePath());
        return command;
    }

    protected void downloadInstaller(ForgeVersion v, File file, String name)
        throws IOException {
        var url = BASE_URL + v.getFullName() + "/" + name;
        log.debug("Downloading Installer from " + url);
        try {
            IOUtil.download(url, file.getAbsolutePath());
        } catch (IOException e) {
            log.debug("Failed to download Forge from " + url + ": "
                          + e.getMessage());
            url = BASE_URL + v.getFullName() + "-" + v.getVersion()
                + "/forge-" + v.getFullName() + "-" + v.getVersion()
                + "-installer.jar";
            log.debug("Downloading from forge from " + url);
            IOUtil.download(url, file.getAbsolutePath());
        }
    }

    private void ensureJsonExists(String name, FileManager mcFiles)
        throws IOException {
        val file = mcFiles.create(name);
        try {
            if (!JsonUtil.fromFile(file).isJsonObject()) {
                throw new IOException("Not a JsonObject!");
            }
        } catch (IOException ioException) {
            log.debug("Writing in " + name);
            Files.write(file.toPath(),
                        "{\"profiles\": {}}".getBytes(StandardCharsets.UTF_8));
        }
    }

}
