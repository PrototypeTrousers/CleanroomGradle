package com.cleanroommc.gradle.api.named.dependency;

import com.cleanroommc.gradle.utils.Utilities;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.artifacts.transform.*;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;
import java.util.stream.Collectors;

public abstract class DependencyDeobfuscationTransform
        implements TransformAction<DependencyDeobfuscationTransform.Parameters> {

    public interface Parameters extends TransformParameters {

        @InputFile
        @PathSensitive(PathSensitivity.NONE)
        RegularFileProperty getFieldsCsv();

        @InputFile
        @PathSensitive(PathSensitivity.NONE)
        RegularFileProperty getMethodsCsv();

        @InputFile
        @PathSensitive(PathSensitivity.RELATIVE)
        ConfigurableFileCollection getFilesToDeobf();

        @Input
        SetProperty<String> getModulesToDeobf();
    }

    @InputArtifact
    @PathSensitive(PathSensitivity.NONE)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @InputArtifactDependencies
    @CompileClasspath
    public abstract FileCollection getDependencies();

    @Override
    public void transform(TransformOutputs outputs) {
        try {
            runTransform(outputs);
        } catch (Exception e) {
            throw new VariantTransformConfigurationException(
                    "Error encountered when deobfuscating " + getInputArtifact().get().getAsFile(),
                    e);
        }
    }

    private static boolean mustRunDeobf(File inputFile, Set<File> filesToDeobf, Set<String> modulesToDeobf) {
        boolean mustRunDeobf = filesToDeobf.stream().anyMatch(inputFile::equals);

        if (!mustRunDeobf) {
            final Path inputPath = inputFile.toPath();
            final String moduleSpec = Utilities.getModuleSpecFromCachePath(inputPath);
            if (moduleSpec == null) {
                for (final String foundSpec : modulesToDeobf) {
                    final List<String> parts = Arrays.stream(foundSpec.split(":")).filter(StringUtils::isNotBlank)
                            .collect(Collectors.toList());
                    final String foundJar = StringUtils.join(parts, "-") + ".jar";
                    if (inputPath.endsWith(foundJar)) {
                        mustRunDeobf = true;
                    }
                }
            } else {
                mustRunDeobf = modulesToDeobf.contains(moduleSpec);
            }
        }

        return mustRunDeobf;
    }

    private void runTransform(TransformOutputs outputs) throws IOException {
        final FileCollection dependencies = getDependencies();
        final Parameters parameters = getParameters();
        final File inputLocation = getInputArtifact().get().getAsFile();

        final Set<File> filesToDeobf = parameters.getFilesToDeobf().getFiles();
        final Set<String> modulesToDeobf = parameters.getModulesToDeobf().getOrElse(Collections.emptySet());

        final boolean runDeobf = mustRunDeobf(inputLocation, filesToDeobf, modulesToDeobf);

        if (!runDeobf) {
            outputs.file(inputLocation);
            return;
        }

        final String outFileName = StringUtils.removeEnd(inputLocation.getName(), ".jar") + "-deobf.jar";
        final String tempCopyName = StringUtils.removeEnd(inputLocation.getName(), ".jar") + "-rfg_tmp_copy.jar";
        final File outFile = outputs.file(outFileName);
        final File outputDir = outFile.getParentFile();
        final File outFileTemp = new File(outputDir, tempCopyName);
        Logging.getLogger(DependencyDeobfuscationTransform.class)
                .info("Deobfuscating {} to {}", inputLocation, outFile);

        final File fieldsCsv = parameters.getFieldsCsv().get().getAsFile();
        final File methodsCsv = parameters.getMethodsCsv().get().getAsFile();

        final Utilities.MappingsSet mappings = Utilities.loadMappingCsvs(methodsCsv, fieldsCsv, null, null, null);
        final Map<String, String> combined = mappings.getCombinedMappings();

        if (outFile.isFile()) {
            FileUtils.delete(outFile);
        }

        try (final InputStream is = FileUtils.openInputStream(inputLocation);
             final BufferedInputStream bis = new BufferedInputStream(is);
             final JarInputStream jis = new JarInputStream(bis, false);
             final OutputStream os = FileUtils.openOutputStream(outFileTemp, false);
             final BufferedOutputStream bos = new BufferedOutputStream(os);
             final JarOutputStream jos = makeTransformerJarOutputStream(jis, bos)) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (StringUtils.endsWithIgnoreCase(entry.getName(), ".dsa")
                        || StringUtils.endsWithIgnoreCase(entry.getName(), ".rsa")
                        || StringUtils.endsWithIgnoreCase(entry.getName(), ".sf")
                        || StringUtils.containsIgnoreCase(entry.getName(), "meta-inf/sig-")) {
                    continue;
                }
                jos.putNextEntry(new JarEntry(entry.getName()));
                if (StringUtils.endsWithIgnoreCase(entry.getName(), ".class")) {
                    byte[] data = IOUtils.toByteArray(jis);
                    IOUtils.write(Utilities.simpleRemapClass(data, combined), jos);
                } else if (StringUtils.endsWith(entry.getName(), "META-INF/MANIFEST.MF")) {
                    // This if will only trigger if the manifest is not one of the first 2 jar entries
                    Manifest mf = new Manifest(CloseShieldInputStream.wrap(jis));
                    transformManifest(mf);
                    mf.write(jos);
                } else {
                    IOUtils.copy(jis, jos);
                }
                jos.closeEntry();
            }
        }

        Files.move(outFileTemp.toPath(), outFile.toPath());
    }

    private static JarOutputStream makeTransformerJarOutputStream(JarInputStream jis, OutputStream os)
            throws IOException {
        if (jis.getManifest() != null) {
            final Manifest mf = jis.getManifest();
            transformManifest(mf);
            return new JarOutputStream(os, mf);
        } else {
            return new JarOutputStream(os);
        }
    }

    private static void transformManifest(Manifest mf) {
        final List<String> entriesToRemove = new ArrayList<>();
        for (Map.Entry<String, Attributes> mfEntry : mf.getEntries().entrySet()) {
            final Attributes attrs = mfEntry.getValue();
            final Object[] keys = attrs.keySet().toArray(new Object[0]);
            for (Object key : keys) {
                if (StringUtils.endsWith(key.toString(), "-Digest")) {
                    attrs.remove(key);
                }
            }
            if (attrs.isEmpty()
                    || (attrs.size() == 1 && attrs.keySet().iterator().next().toString().equals("Name"))) {
                attrs.clear();
                entriesToRemove.add(mfEntry.getKey());
            }
        }
        entriesToRemove.forEach(mf.getEntries()::remove);
    }
}