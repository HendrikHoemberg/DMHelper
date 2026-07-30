package dev.hendrikhoemberg.dmhelper.common.web;

public final class InGameDateFormatter {
    private InGameDateFormatter() {}

    /** Real month name -> "12. Hammer 1491", placeholder -> "12.1.1491" */
    public static String format(int year, int month, int day, String[] monthNames) {
        String name = monthNames != null && month < monthNames.length
                ? monthNames[month] : null;
        if (name != null && !name.isBlank() && !name.matches("^\\d+\\.?\\s?") && !name.startsWith("Month "))
            return String.format("%d. %s %d", day, name, year);
        return String.format("%d.%d.%d", day, month + 1, year);
    }
}
