package me.earth.headlessmc.launcher.command;

import lombok.CustomLog;
import lombok.val;
import me.earth.headlessmc.api.command.CommandException;
import me.earth.headlessmc.launcher.Launcher;
import me.earth.headlessmc.launcher.LauncherProperties;
import me.earth.headlessmc.launcher.files.FileUtil;
import me.earth.headlessmc.launcher.java.Java;
import me.earth.headlessmc.launcher.util.IOUtil;
import me.earth.headlessmc.launcher.version.Version;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@CustomLog
public class FabricCommand extends AbstractVersionCommand {
    private static final String URL = "https://maven.fabricmc.net/" +
        "net/fabricmc/fabric-installer/0.11.0/fabric-installer-0.11.0.jar";

    public FabricCommand(Launcher ctx) {
        super(ctx, "fabric", "Downloads Fabric for the specified version.");
        args.put("<version>", "The version to download.");
    }

    @Override
    public void execute(Version ver, String... args) throws CommandException {
        ctx.log("Installing Fabric for version " + ver.getName() + "...");
        val tempFiles = ctx.getFileManager().createRelative(UUID.randomUUID()
                                                                .toString());
        val jar = tempFiles.create("fabric-installer.jar");
        val url = ctx.getConfig().get(LauncherProperties.FABRIC_URL, URL);

        try {
            downloadInstaller(url, jar);
            install(ver, jar);
        } finally {
            try {
                log.debug("Deleting: " + jar.getAbsolutePath());
                FileUtil.delete(tempFiles.getBase());
            } catch (IOException e) {
                log.error(
                    "Failed to delete: " + jar.getAbsolutePath() + " : "
                        + e.getMessage());
            }
        }

        ctx.log("Installed Fabric for: " + ver.getName() + " successfully!");
    }

    private void downloadInstaller(String url, File jar)
        throws CommandException {
        try {
            IOUtil.download(url, jar.getAbsolutePath());
        } catch (IOException e) {
            throw new CommandException(
                "Couldn't download Fabric installer from " + url + " to "
                    + jar.getAbsolutePath() + " : " + e.getMessage());
        }
    }

    private void install(Version version, File jarFile)
        throws CommandException {
        val java = ctx.getJavaService().findBestVersion(17);
        val command = getCommand(version, java, jarFile);

        try {
            log.debug("Launching Fabric-Installer for command: " + command);
            val process = new ProcessBuilder()
                .directory(ctx.getMcFiles().getBase())
                .command(command)
                .inheritIO()
                .start();

            int exitCode = process.waitFor();
            log.debug("Fabric-Installer quit with exit-code: " + exitCode);
            if (exitCode != 0) {
                throw new CommandException(
                    "Fabric installer exit code: " + exitCode
                        + ". Failed to install fabric for version "
                        + version.getName());
            }

            ctx.getVersionService().refresh();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread got interrupted!");
            throw new IllegalStateException("Thread got interrupted!");
        } catch (IOException e) {
            val message = "Fabric installation failed: " + e.getMessage();
            log.error(message);
            throw new CommandException(message);
        }
    }

    protected List<String> getCommand(Version version, Java java, File jar) {
        val command = new ArrayList<String>();
        command.add(java.getExecutable());
        command.add("-jar");
        command.add(jar.getAbsolutePath());
        command.add("client");
        command.add("-noprofile");
        command.add("-mcversion");
        command.add(version.getName());
        return command;
    }

}
