package com.cleanroommc.gradle.api.patch;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.TextMappingsReader;

import java.io.Reader;

public class CsvMappingsReader extends TextMappingsReader {
    public CsvMappingsReader(final Reader reader, boolean reverse) {
        super(reader, mappings -> new Processor(mappings, reverse));
    }

    public static class Processor extends TextMappingsReader.Processor {
        String dummyClassName = "any";
        private final boolean reverse;

        public Processor(final MappingSet mappings, boolean reverse) {
            super(MappingSet.create());
            this.reverse = reverse;
        }

        @Override
        public void accept(String s) {
            if (!s.startsWith("p_")) return;
            final String[] split = s.split(",");
            final String obf = split[0];
            final String deobf = split[1];
            if (reverse) {
                this.mappings.getOrCreateClassMapping("any").getOrCreateMethodMapping()
                        .setDeobfuscatedName(obf);
            } else {
                this.mappings.getOrCreateClassMapping(obf)
                        .setDeobfuscatedName(deobf);
            }
        }
    }
}