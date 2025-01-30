package com.cleanroommc.gradle.utils;

import com.cleanroommc.gradle.env.mcp.task.Remap;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.apache.commons.io.IOUtils;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;

public class Utilities {

    public static MappingsSet loadMappingCsvs(File methodsCsv, File fieldsCsv, @Nullable File paramsCsv,
                                              @Nullable Collection<File> extraParamsCsvs, @Nullable String genericsFilename) {
        try {
            MappingsSet mappings = new MappingsSet();
            try (CSVReader methodReader = createCsvReader(methodsCsv)) {
                for (String[] csvLine : methodReader) {
                    // func_100012_b,setPotionDurationMax,0,Toggle the isPotionDurationMax field.
                    mappings.methodMappings.put(csvLine[0], new Mapping(csvLine[1], csvLine[3]));
                }
            }
            try (CSVReader fieldReader = createCsvReader(fieldsCsv)) {
                for (String[] csvLine : fieldReader) {
                    // field_100013_f,isPotionDurationMax,0,"True if potion effect duration is at maximum, false
                    // otherwise."
                    mappings.fieldMappings.put(csvLine[0], new Mapping(csvLine[1], csvLine[3]));
                }
            }
            if (paramsCsv != null) {
                try (CSVReader paramReader = createCsvReader(paramsCsv)) {
                    for (String[] csvLine : paramReader) {
                        // p_104055_1_,force,1
                        mappings.paramMappings.put(csvLine[0], csvLine[1]);
                    }
                }
            }
            if (extraParamsCsvs != null && !extraParamsCsvs.isEmpty()) {
                for (File extraParamsCsv : extraParamsCsvs) {
                    try (CSVReader paramReader = createCsvReader(extraParamsCsv)) {
                        for (String[] csvLine : paramReader) {
                            mappings.paramMappings.put(csvLine[0], csvLine[1]);
                        }
                    }
                }
            }
            if (StringUtils.isNotBlank(genericsFilename)) {
                URL genericsUrl = Remap.class.getResource(genericsFilename);
                URL genericPatchesUrl = Remap.class
                        .getResource(genericsFilename.replace("Fields", "Patches"));
                try (CSVReader genReader = createCsvReader(genericsUrl)) {
                    for (String[] genLine : genReader) {
                        // zipEntry, className, srg, mcp, param, type, suffix, comment
                        String srg = genLine[2];
                        int colon = srg.indexOf(':');
                        if (colon >= 0) {
                            srg = srg.substring(0, colon);
                        }
                        final String zipEntry = genLine[0];
                        final String param = genLine[4];
                        final String type = genLine[5];
                        final String suffix = genLine[6];
                        final String key = srg.equals("@init") ? (zipEntry + genLine[2]) : srg;
                        mappings.genericMappings.put(key, new GenericMapping(zipEntry, param, suffix, type));
                    }
                }
                try (CSVReader genReader = createCsvReader(genericPatchesUrl)) {
                    for (String[] genLine : genReader) {
                        // zipEntry, className, containsFilter, toReplace, replaceWith, reason
                        mappings.genericPatches.put(
                                genLine[0],
                                new GenericPatch(genLine[0], genLine[2], genLine[3], genLine[4]));
                    }
                }
            }
            return mappings;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param classBytes The .class bytes to remap
     * @param mappings   The combined mappings set to use for renaming items
     * @return A jar with names remapped using simple find-and-replace on SRG names in the given mappings (no
     * inheritance checks performed)
     */
    public static byte[] simpleRemapClass(byte[] classBytes, Map<String, String> mappings) {
        final ClassReader reader = new ClassReader(classBytes);
        final ClassWriter writer = new ClassWriter(0);
        final ClassRemapper remapper = new ClassRemapper(writer, new SimpleSrgRemapper(mappings));
        reader.accept(remapper, 0);
        return writer.toByteArray();
    }

    /**
     * @param path A path like
     *             {@code "/home/user/.gradle/caches/modules-2/files-2.1/com.github.GTNewHorizons/CodeChickenLib/1.1.6/51081c1c2d8d75ae26f64427849de2c0ba99144/CodeChickenLib-1.1.6-dev.jar"}
     * @return A dependency specifier like "com.github.GTNewHorizons:CodeChickenLib:1.1.6:dev" or null if not a
     * modules-2 path.
     */
    public static String getModuleSpecFromCachePath(Path path) {
        final String[] pathComponents = StreamSupport.stream(path.spliterator(), false).map(Path::toString)
                .toArray(String[]::new);
        // Example:
        // /home/user/.gradle/caches/modules-2/files-2.1/com.github.GTNewHorizons/CodeChickenLib/1.1.6/51081c1c2d8d75ae26f64427849de2c0ba99144/CodeChickenLib-1.1.6-dev.jar`
        // Try to find modules-2/files-2.1
        int modulesCacheIndex = -1;
        for (int i = 0; i < pathComponents.length - 6; i++) {
            if (pathComponents[i].equalsIgnoreCase("modules-2")
                    && pathComponents[i + 1].equalsIgnoreCase("files-2.1")) {
                modulesCacheIndex = i + 2;
                break;
            }
        }
        if (modulesCacheIndex == -1) {
            return null;
        }
        final String group = pathComponents[modulesCacheIndex];
        final String module = pathComponents[modulesCacheIndex + 1];
        final String version = pathComponents[modulesCacheIndex + 2];
        final String jarName = pathComponents[modulesCacheIndex + 4];
        final String classifier = StringUtils.removeStart(
                StringUtils.removeEndIgnoreCase(
                        StringUtils.removeStartIgnoreCase(jarName, module + "-" + version),
                        ".jar"),
                "-").trim();
        final String gmv = group + ":" + module + ":" + version;
        if (StringUtils.isEmpty(classifier)) {
            return gmv;
        } else {
            return gmv + ":" + classifier;
        }
    }

    public static CSVReader createCsvReader(File file) throws IOException {
        final CSVParser csvParser = new CSVParserBuilder().withEscapeChar(CSVParser.NULL_CHARACTER)
                .withStrictQuotes(false).build();
        return new CSVReaderBuilder(Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)).withSkipLines(1)
                .withCSVParser(csvParser).build();
    }

    public static CSVReader createCsvReader(URL url) throws IOException {
        final CSVParser csvParser = new CSVParserBuilder().withEscapeChar(CSVParser.NULL_CHARACTER)
                .withStrictQuotes(false).build();
        final String content = IOUtils.toString(url, StandardCharsets.UTF_8);
        return new CSVReaderBuilder(new StringReader(content)).withSkipLines(1).withCSVParser(csvParser).build();
    }

    public static final class Mapping {

        public final String name;
        public final String javadoc;

        public Mapping(String name, String javadoc) {
            if (name == null) {
                throw new IllegalArgumentException("Null mapping name passed");
            }
            if (javadoc == null) {
                javadoc = "";
            }
            this.name = name;
            this.javadoc = javadoc;
        }
    }

    public static final class GenericMapping {

        public final String zipEntry;
        public final String param;
        public final String suffix;
        public final String type;
        public int uses = 0;

        public GenericMapping(String zipEntry, String param, String suffix, String type) {
            this.zipEntry = zipEntry;
            this.param = Objects.requireNonNull(param);
            this.suffix = Objects.requireNonNull(suffix);
            this.type = type;
        }

        @Override
        public String toString() {
            return "GenericMapping{" + "zipEntry='"
                    + zipEntry
                    + '\''
                    + ", param='"
                    + param
                    + '\''
                    + ", suffix='"
                    + suffix
                    + '\''
                    + ", type='"
                    + type
                    + '\''
                    + '}';
        }
    }

    public static final class GenericPatch {

        public final String zipEntry;
        public final String containsFilter;
        public final String toReplace;
        public final String replaceWith;

        public GenericPatch(String zipEntry, String containsFilter, String toReplace, String replaceWith) {
            this.zipEntry = zipEntry;
            this.containsFilter = containsFilter;
            this.toReplace = toReplace;
            this.replaceWith = replaceWith;
        }

        @Override
        public String toString() {
            return "GenericPatch{" + "zipEntry='"
                    + zipEntry
                    + '\''
                    + ", containsFilter='"
                    + containsFilter
                    + '\''
                    + ", toReplace='"
                    + toReplace
                    + '\''
                    + ", replaceWith='"
                    + replaceWith
                    + '\''
                    + '}';
        }
    }

    public static class MappingsSet {

        public final Map<String, Mapping> methodMappings = new HashMap<>();
        public final Map<String, Mapping> fieldMappings = new HashMap<>();
        public final Map<String, String> paramMappings = new HashMap<>();
        // srg name -> mapping
        public final ListMultimap<String, GenericMapping> genericMappings = MultimapBuilder.hashKeys().arrayListValues()
                .build();
        // zip entry -> patch list
        public final ListMultimap<String, GenericPatch> genericPatches = MultimapBuilder.hashKeys()
                .arrayListValues().build();

        public String remapSimpleName(String name) {
            if (StringUtils.isBlank(name)) {
                return "";
            } else if (name.startsWith("field_")) {
                Mapping map = fieldMappings.get(name);
                return map == null ? name : map.name;
            } else if (name.startsWith("func_")) {
                Mapping map = methodMappings.get(name);
                return map == null ? name : map.name;
            } else if (name.startsWith("p_")) {
                return paramMappings.getOrDefault(name, name);
            } else {
                return name;
            }
        }

        /**
         * @return A combined map of method, field and param mappings.
         */
        public Map<String, String> getCombinedMappings() {
            Map<String, String> ret = new HashMap<>();
            methodMappings.forEach((k, m) -> ret.put(k, m.name));
            fieldMappings.forEach((k, m) -> ret.put(k, m.name));
            ret.putAll(paramMappings);
            return ret;
        }
    }

    public static String getMapStringOrBlank(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }
}
