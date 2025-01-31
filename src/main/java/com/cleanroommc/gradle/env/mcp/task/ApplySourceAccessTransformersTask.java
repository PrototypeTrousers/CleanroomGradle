package com.cleanroommc.gradle.env.mcp.task;

import com.cleanroommc.gradle.api.named.task.JarTransformer;
import com.cleanroommc.gradle.api.named.task.type.MavenJarExec;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@CacheableTask
public abstract class ApplySourceAccessTransformersTask extends MavenJarExec implements JarTransformer {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputJar();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getAccessTransformerFile();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getCompileClasspath();

    List<String> programArgs = new ArrayList<>();
    Logger logger = getLogger();

    public ApplySourceAccessTransformersTask() {
        super("ApplySourceAccessTransformers", "net.neoforged.jst:jst-cli-bundle:1.0.67");
        this.getMainClass().set("net.neoforged.jst.cli.Main");
        this.jvmArgs("-Xmx4g");
    }

    @Override
    public void exec() {
        this.args(getInputJar(), getOutputJar());
        this.args("--enable-accesstransformers");
        this.args("--access-transformer", getAccessTransformerFile());
        super.exec();
    }

    private File patchInvalidAccessTransformer(File atFile) {
        // Fix known invalid AT files shipped by Forge, specifically forge_at.cfg
        if (!atFile.getName().equals("forge_at.cfg")) {
            return atFile;
        }

        final File patched = new File(atFile.getParentFile(), atFile.getName() + ".patched");
        final String regex = ";\\)$";
        final String replacement = ";\\)V";
        try (BufferedReader reader = new BufferedReader(new FileReader(atFile));
                BufferedWriter writer = new BufferedWriter(new FileWriter(patched))) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line.replaceAll(regex, replacement));
                writer.newLine();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return patched;
    }
}