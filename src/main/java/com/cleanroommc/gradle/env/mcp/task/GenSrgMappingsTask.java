package com.cleanroommc.gradle.env.mcp.task;

import com.cleanroommc.gradle.env.mcp.MethodData;
import com.cleanroommc.gradle.env.mcp.SrgContainer;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.NamedCsvRecord;
import org.apache.commons.io.Charsets;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.util.*;

/**
 * Generates Deobf(Mcp)-Searge(Srg)-Obf(Notch) name mappings
 */
@CacheableTask
public abstract class GenSrgMappingsTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputSrg();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputExc();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getExtraExcs();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getExtraSrgs();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getMethodsCsv();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getFieldsCsv();

    @OutputFile
    public abstract RegularFileProperty getNotchToSrg();

    @OutputFile
    public abstract RegularFileProperty getNotchToMcp();

    @OutputFile
    public abstract RegularFileProperty getMcpToNotch();

    @OutputFile
    public abstract RegularFileProperty getSrgToMcp();

    @OutputFile
    public abstract RegularFileProperty getMcpToSrg();

    @OutputFile
    public abstract RegularFileProperty getSrgExc();

    @OutputFile
    public abstract RegularFileProperty getMcpExc();

    @TaskAction
    public void generateMappings() throws IOException {
        // SRG->MCP from the MCP csv files
        HashMap<String, String> methods = new HashMap<>(5000);
        HashMap<String, String> fields = new HashMap<>(5000);

        CsvReader<NamedCsvRecord> reader = CsvReader.builder().ofNamedCsvRecord(getMethodsCsv().get().getAsFile().getAbsolutePath());
        for (NamedCsvRecord namedCsvRecord : reader) {
            methods.put(namedCsvRecord.getField(0), namedCsvRecord.getField(1));
        }

        CsvReader<NamedCsvRecord> fieldReader = CsvReader.builder().ofNamedCsvRecord(getFieldsCsv().get().getAsFile().getAbsolutePath());
        for (NamedCsvRecord n : fieldReader) {
            fields.put(n.getField(0), n.getField(1));
        }

        SrgContainer inSrg = new SrgContainer().readSrg(getInputSrg().get().getAsFile());
        Map<String, String> excRemap = new HashMap<>(); // Was a bunch of commented out code in ForgeGradle
        // Write outputs
        writeOutSrgs(inSrg, methods, fields);
        writeOutExcs(excRemap, methods);
    }

    // Copied straight from ForgeGradle
    private void writeOutSrgs(SrgContainer inSrg, Map<String, String> methods, Map<String, String> fields)
            throws IOException {
        // ensure folders exist
        Files.createDirectories(getNotchToSrg().get().getAsFile().toPath().getParent());
        Files.createDirectories(getNotchToMcp().get().getAsFile().toPath().getParent());
        Files.createDirectories(getSrgToMcp().get().getAsFile().toPath().getParent());
        Files.createDirectories(getMcpToSrg().get().getAsFile().toPath().getParent());
        Files.createDirectories(getMcpToNotch().get().getAsFile().toPath().getParent());

        // create streams

        try (BufferedWriter notchToSrg = Files.newBufferedWriter(getNotchToSrg().get().getAsFile().toPath(), Charsets.UTF_8);
             BufferedWriter notchToMcp = Files.newBufferedWriter(getNotchToMcp().get().getAsFile().toPath(), Charsets.UTF_8);
             BufferedWriter srgToMcp = Files.newBufferedWriter(getSrgToMcp().get().getAsFile().toPath(), Charsets.UTF_8);
             BufferedWriter mcpToSrg = Files.newBufferedWriter(getMcpToSrg().get().getAsFile().toPath(), Charsets.UTF_8);
             BufferedWriter mcpToNotch = Files.newBufferedWriter(getMcpToNotch().get().getAsFile().toPath(), Charsets.UTF_8)) {
            String line, temp, mcpName;
            // packages
            for (Map.Entry<String, String> e : inSrg.packageMap.entrySet()) {
                line = "PK: " + e.getKey() + " " + e.getValue();

                // nobody cares about the packages.
                notchToSrg.write(line);
                notchToSrg.newLine();

                notchToMcp.write(line);
                notchToMcp.newLine();

                // No package changes from MCP to SRG names
                // srgToMcp.write(line);
                // srgToMcp.newLine();

                // No package changes from MCP to SRG names
                // mcpToSrg.write(line);
                // mcpToSrg.newLine();

                // reverse!
                mcpToNotch.write(String.format("PK: %s %s", e.getValue(), e.getKey()));
                mcpToNotch.newLine();
            }

            // classes
            for (Map.Entry<String, String> e : inSrg.classMap.entrySet()) {
                line = String.format("CL: %s %s", e.getKey(), e.getValue());

                // same...
                notchToSrg.write(line);
                notchToSrg.newLine();

                // SRG and MCP have the same class names
                notchToMcp.write(line);
                notchToMcp.newLine();

                line = String.format("CL: %s %s", e.getValue(), e.getValue());

                // deobf: same classes on both sides.
                srgToMcp.write(line);
                srgToMcp.newLine();

                // reobf: same classes on both sides.
                mcpToSrg.write(line);
                mcpToSrg.newLine();

                // output is notch
                mcpToNotch.write(line);
                mcpToNotch.newLine();
            }

            // fields
            for (Map.Entry<String, String> e : inSrg.fieldMap.entrySet()) {
                line = String.format("FD: %s %s", e.getKey(), e.getValue());

                // same...
                notchToSrg.write(line);
                notchToSrg.newLine();

                temp = e.getValue().substring(e.getValue().lastIndexOf('/') + 1);
                mcpName = e.getValue();
                if (fields.containsKey(temp)) mcpName = mcpName.replace(temp, fields.get(temp));

                // SRG and MCP have the same class names
                notchToMcp.write(String.format("FD: %s %s", e.getKey(), mcpName));
                notchToMcp.newLine();

                // srg name -> mcp name
                srgToMcp.write(String.format("FD: %s %s", e.getValue(), mcpName));
                srgToMcp.newLine();

                // mcp name -> srg name
                mcpToSrg.write(String.format("FD: %s %s", mcpName, e.getValue()));
                mcpToSrg.newLine();

                // output is notch
                mcpToNotch.write(String.format("FD: %s %s", mcpName, e.getKey()));
                mcpToNotch.newLine();
            }

            // methods
            for (Map.Entry<MethodData, MethodData> e : inSrg.methodMap.entrySet()) {
                line = String.format("MD: %s %s", e.getKey(), e.getValue());

                // same...
                notchToSrg.write(line);
                notchToSrg.newLine();

                temp = e.getValue().name.substring(e.getValue().name.lastIndexOf('/') + 1);
                mcpName = e.getValue().toString();
                if (methods.containsKey(temp)) mcpName = mcpName.replace(temp, methods.get(temp));

                // SRG and MCP have the same class names
                notchToMcp.write(String.format("MD: %s %s", e.getKey(), mcpName));
                notchToMcp.newLine();

                // srg name -> mcp name
                srgToMcp.write(String.format("MD: %s %s", e.getValue(), mcpName));
                srgToMcp.newLine();

                // mcp name -> srg name
                mcpToSrg.write(String.format("MD: %s %s", mcpName, e.getValue()));
                mcpToSrg.newLine();

                // output is notch
                mcpToNotch.write(String.format("MD: %s %s", mcpName, e.getKey()));
                mcpToNotch.newLine();
            }
        }
    }

    private void writeOutExcs(Map<String, String> excRemap, Map<String, String> methods) throws IOException {
        // ensure folders exist
        Files.createDirectories(getSrgExc().get().getAsFile().toPath().getParent());
        Files.createDirectories(getMcpExc().get().getAsFile().toPath().getParent());

        // create streams
        try (BufferedWriter srgOut = Files
                .newBufferedWriter(getSrgExc().get().getAsFile().toPath(), Charsets.UTF_8);
                BufferedWriter mcpOut = Files
                        .newBufferedWriter(getMcpExc().get().getAsFile().toPath(), Charsets.UTF_8)) {

            // read and write existing lines
            Set<File> excFiles = new HashSet<>(getExtraExcs().getFiles());
            excFiles.add(getInputExc().get().getAsFile());
            for (File f : excFiles) {
                List<String> lines = Files.readAllLines(f.toPath(), Charsets.UTF_8);

                for (String line : lines) {
                    // these are in MCP names
                    srgOut.write(line);
                    srgOut.newLine();

                    // remap SRG

                    // split line up
                    String[] split = line.split("=");
                    int sigIndex = split[0].indexOf('(');
                    int dotIndex = split[0].indexOf('.');

                    // not a method? wut?
                    if (sigIndex == -1 || dotIndex == -1) {
                        mcpOut.write(line);
                        mcpOut.newLine();
                        continue;
                    }

                    // get new name
                    String name = split[0].substring(dotIndex + 1, sigIndex);
                    if (excRemap.containsKey(name)) name = excRemap.get(name);

                    // write remapped line
                    mcpOut.write(
                            split[0].substring(0, dotIndex) + name + split[0].substring(sigIndex) + "=" + split[1]);
                    mcpOut.newLine();
                }
            }
        }
    }
}