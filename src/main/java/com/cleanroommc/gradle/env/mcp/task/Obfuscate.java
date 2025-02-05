package com.cleanroommc.gradle.env.mcp.task;

import com.cleanroommc.gradle.api.named.task.JarTransformer;
import com.cleanroommc.gradle.api.named.task.type.MavenJarExec;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import java.io.*;

public abstract class Obfuscate extends MavenJarExec implements JarTransformer {

    @OutputFile
    public abstract RegularFileProperty getObfuscatedJar();

    @InputFile
    public abstract RegularFileProperty getSrgMappingFile();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getExtraSrgFiles();

    @InputFile
    public abstract RegularFileProperty getDeobfuscatedJar();

    public Obfuscate() {
        super("obfuscate", "net.minecraftforge:ForgeAutoRenamingTool:1.1.0");
        this.getMainClass().set("net.minecraftforge.fart.Main");
        this.args("--input", getDeobfuscatedJar(),
                "--output", getObfuscatedJar(),
                "--src-fix");
        this.setup(false);
    }

    @Override
    protected void beforeExec() {
        if (getExtraSrgFiles().isEmpty()) {
            this.args("--map", getSrgMappingFile());
            return;
        }

        File srgMappingFile = getSrgMappingFile().get().getAsFile();
        final File merged = new File(srgMappingFile.getParentFile(), srgMappingFile.getName() + ".extrasmerged");

        BufferedWriter writer;
        BufferedReader reader;
        try {
            writer = new BufferedWriter(new FileWriter(merged));

            reader = new BufferedReader(new FileReader(srgMappingFile));
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }

            for (File f : getExtraSrgFiles().getFiles()) {
                if (f.exists()) {
                    reader = new BufferedReader(new FileReader(f));
                    while ((line = reader.readLine()) != null) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.args("--map", merged);

        super.beforeExec();
    }

}
