package dev.hendrikhoemberg.dmhelper.support;

import java.util.List;
import java.util.SortedMap;

public record PackageShapeProfile(
        String generatedFrom,
        int formatVersion,
        List<String> sectionKinds,
        List<String> transitionKinds,
        SortedMap<String, Integer> counts,
        SortedMap<String, Integer> populatedFields) {
}
