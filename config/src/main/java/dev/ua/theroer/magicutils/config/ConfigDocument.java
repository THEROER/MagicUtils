package dev.ua.theroer.magicutils.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal config document helper backed by Jackson.
 */
class ConfigDocument extends ConfigSectionView {
    private static final ObjectMapper SCALAR_MAPPER = createScalarMapper();
    final Map<String, Object> data;
    private final Map<String, List<String>> comments;
    List<String> header;

    ConfigDocument(Map<String, Object> backing, List<String> header) {
        super(backing);
        this.data = backing;
        this.header = header;
        this.comments = new LinkedHashMap<>();
    }

    static ConfigDocument empty() {
        return new ConfigDocument(new LinkedHashMap<>(), null);
    }

    private static ObjectMapper createScalarMapper() {
        ObjectMapper mapper = JsonMapper.builder().build();
        mapper.findAndRegisterModules();
        return mapper;
    }

    static ConfigDocument load(File file) throws IOException {
        if (file == null || !file.exists()) {
            return empty();
        }
        ConfigFormat format = ConfigFormat.fromFile(file);
        Map<String, Object> map = format.read(file);
        return new ConfigDocument(map, null);
    }

    static void ensureParentDirectory(File file) throws IOException {
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("Failed to create directory " + parent);
        }
    }

    void addComments(Map<String, List<String>> map, String prefix) {
        if (map == null || map.isEmpty()) return;
        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            String key = e.getKey();
            String full = prefix.isEmpty() ? key : prefix + key;
            comments.put(full, e.getValue());
        }
    }

    void setHeader(List<String> lines) {
        this.header = lines;
    }

    void set(String path, Object value, List<String> comment) {
        applyPath(data, path, value);
        if (comment != null && !comment.isEmpty()) {
            comments.put(path, comment);
        }
    }

    boolean contains(String path) {
        return navigate(data, path).found;
    }

    ConfigSectionView getConfigurationSection(String path) {
        NavigateResult res = navigate(data, path);
        if (res.found && res.value instanceof Map) {
            return new ConfigSectionView(castMap((Map<?, ?>) res.value));
        }
        return null;
    }

    Object get(String path) {
        NavigateResult res = navigate(data, path);
        return res.found ? res.value : null;
    }

    void save(File file) throws IOException {
        ConfigFormat.fromFile(file).write(file, data, header, comments);
    }

    private static void applyPath(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object child = current.get(part);
            if (!(child instanceof Map)) {
                child = new LinkedHashMap<String, Object>();
                current.put(part, child);
            }
            current = castMap((Map<?, ?>) child);
        }
        current.put(parts[parts.length - 1], value);
    }

    private static NavigateResult navigate(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length; i++) {
            Object value = current.get(parts[i]);
            if (i == parts.length - 1) {
                return new NavigateResult(true, value);
            }
            if (!(value instanceof Map)) {
                return new NavigateResult(false, null);
            }
            current = castMap((Map<?, ?>) value);
        }
        return new NavigateResult(false, null);
    }

    static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    private record NavigateResult(boolean found, Object value) {
    }

    static void writeYamlWithComments(File file,
                                              Map<String, Object> map,
                                              List<String> header,
                                              Map<String, List<String>> comments) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            if (header != null && !header.isEmpty()) {
                for (String line : header) {
                    writer.write("# " + line + System.lineSeparator());
                }
            }
            writeWithComments(writer, map, comments, "", 0);
        }
    }

    static void writeJsonWithComments(File file,
                                              Map<String, Object> map,
                                              List<String> header,
                                              Map<String, List<String>> comments) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            if (header != null && !header.isEmpty()) {
                for (String line : header) {
                    writer.write("// " + line + System.lineSeparator());
                }
            }
            writeJsonObject(writer, map, comments, "", 0);
            writer.write(System.lineSeparator());
        }
    }

    static void writeTomlWithComments(File file,
                                              Map<String, Object> map,
                                              List<String> header,
                                              Map<String, List<String>> comments) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            if (header != null && !header.isEmpty()) {
                for (String line : header) {
                    writer.write("# " + line + System.lineSeparator());
                }
            }
            writeTomlTableBody(writer, map, comments, "");
        }
    }

    private static void writeWithComments(Writer writer,
                                          Map<String, Object> map,
                                          Map<String, List<String>> comments,
                                          String pathPrefix,
                                          int indentLevel) throws IOException {
        String indent = "  ".repeat(indentLevel);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String currentPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
            List<String> commentLines = comments != null ? comments.get(currentPath) : null;
            if (commentLines != null) {
                for (String line : commentLines) {
                    writer.write(indent + "# " + line + System.lineSeparator());
                }
            }
            Object value = entry.getValue();
            if (value instanceof Map) {
                writer.write(indent + key + ":" + System.lineSeparator());
                writeWithComments(writer, castMap((Map<?, ?>) value), comments, currentPath, indentLevel + 1);
            } else if (value instanceof List) {
                writer.write(indent + key + ":" + System.lineSeparator());
                writeList(writer, (List<?>) value, comments, currentPath, indentLevel + 1);
            } else if (value instanceof String str && str.contains("\n")) {
                writer.write(indent + key + ": |" + System.lineSeparator());
                writeMultilineValue(writer, str, indentLevel + 1);
            } else {
                writer.write(indent + key + ": " + formatScalar(value) + System.lineSeparator());
            }
        }
    }

    private static void writeJsonObject(Writer writer,
                                        Map<String, Object> map,
                                        Map<String, List<String>> comments,
                                        String pathPrefix,
                                        int indentLevel) throws IOException {
        writer.write("{");
        if (map.isEmpty()) {
            writer.write("}");
            return;
        }
        writer.write(System.lineSeparator());
        int index = 0;
        int size = map.size();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String currentPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
            List<String> commentLines = comments != null ? comments.get(currentPath) : null;
            writeCommentLines(writer, commentLines, "// ", indentLevel + 1);
            String indent = "  ".repeat(indentLevel + 1);
            writer.write(indent + formatJsonKey(key) + ": ");
            writeJsonValue(writer, entry.getValue(), comments, currentPath, indentLevel + 1);
            if (index < size - 1) {
                writer.write(",");
            }
            writer.write(System.lineSeparator());
            index++;
        }
        writer.write("  ".repeat(indentLevel) + "}");
    }

    private static void writeJsonArray(Writer writer,
                                       List<?> list,
                                       Map<String, List<String>> comments,
                                       String pathPrefix,
                                       int indentLevel) throws IOException {
        writer.write("[");
        if (list.isEmpty()) {
            writer.write("]");
            return;
        }
        writer.write(System.lineSeparator());
        for (int i = 0; i < list.size(); i++) {
            Object elem = list.get(i);
            String currentPath = pathPrefix + "[" + i + "]";
            List<String> commentLines = comments != null ? comments.get(currentPath) : null;
            writeCommentLines(writer, commentLines, "// ", indentLevel + 1);
            String indent = "  ".repeat(indentLevel + 1);
            writer.write(indent);
            writeJsonValue(writer, elem, comments, currentPath, indentLevel + 1);
            if (i < list.size() - 1) {
                writer.write(",");
            }
            writer.write(System.lineSeparator());
        }
        writer.write("  ".repeat(indentLevel) + "]");
    }

    private static void writeJsonValue(Writer writer,
                                       Object value,
                                       Map<String, List<String>> comments,
                                       String pathPrefix,
                                       int indentLevel) throws IOException {
        if (value instanceof Map) {
            writeJsonObject(writer, castMap((Map<?, ?>) value), comments, pathPrefix, indentLevel);
        } else if (value instanceof List) {
            writeJsonArray(writer, (List<?>) value, comments, pathPrefix, indentLevel);
        } else {
            writer.write(formatScalar(value));
        }
    }

    private static void writeTomlTable(Writer writer,
                                       Map<String, Object> map,
                                       Map<String, List<String>> comments,
                                       String pathPrefix) throws IOException {
        if (!pathPrefix.isEmpty()) {
            List<String> commentLines = comments != null ? comments.get(pathPrefix) : null;
            writeCommentLines(writer, commentLines, "# ", 0);
            writer.write("[" + formatTomlPath(pathPrefix) + "]" + System.lineSeparator());
        }
        writeTomlTableBody(writer, map, comments, pathPrefix);
    }

    private static void writeTomlArrayTable(Writer writer,
                                            List<?> list,
                                            Map<String, List<String>> comments,
                                            String pathPrefix) throws IOException {
        if (list.isEmpty()) {
            return;
        }
        boolean first = true;
        for (int i = 0; i < list.size(); i++) {
            Object elem = list.get(i);
            if (!(elem instanceof Map)) {
                continue;
            }
            if (first) {
                List<String> commentLines = comments != null ? comments.get(pathPrefix) : null;
                writeCommentLines(writer, commentLines, "# ", 0);
            }
            writer.write("[[" + formatTomlPath(pathPrefix) + "]]" + System.lineSeparator());
            writeTomlTableBody(writer, castMap((Map<?, ?>) elem), comments, pathPrefix);
            if (i < list.size() - 1) {
                writer.write(System.lineSeparator());
            }
            first = false;
        }
    }

    private static void writeTomlTableBody(Writer writer,
                                           Map<String, Object> map,
                                           Map<String, List<String>> comments,
                                           String pathPrefix) throws IOException {
        Map<String, Map<String, Object>> tables = new LinkedHashMap<>();
        Map<String, List<?>> arrayTables = new LinkedHashMap<>();
        boolean wroteValue = false;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map) {
                tables.put(key, castMap((Map<?, ?>) value));
                continue;
            }
            if (value instanceof List<?> list && isListOfMaps(list)) {
                arrayTables.put(key, list);
                continue;
            }
            String formatted = formatTomlValue(value);
            if (formatted == null) {
                continue;
            }
            String currentPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
            List<String> commentLines = comments != null ? comments.get(currentPath) : null;
            writeCommentLines(writer, commentLines, "# ", 0);
            writer.write(formatTomlKey(key) + " = " + formatted + System.lineSeparator());
            wroteValue = true;
        }

        boolean wroteBlock = wroteValue;
        for (Map.Entry<String, Map<String, Object>> entry : tables.entrySet()) {
            if (wroteBlock) {
                writer.write(System.lineSeparator());
            }
            String tablePath = pathPrefix.isEmpty() ? entry.getKey() : pathPrefix + "." + entry.getKey();
            writeTomlTable(writer, entry.getValue(), comments, tablePath);
            wroteBlock = true;
        }
        for (Map.Entry<String, List<?>> entry : arrayTables.entrySet()) {
            if (wroteBlock) {
                writer.write(System.lineSeparator());
            }
            String tablePath = pathPrefix.isEmpty() ? entry.getKey() : pathPrefix + "." + entry.getKey();
            writeTomlArrayTable(writer, entry.getValue(), comments, tablePath);
            wroteBlock = true;
        }
    }

    private static void writeList(Writer writer, List<?> list,
                                  Map<String, List<String>> comments,
                                  String pathPrefix,
                                  int indentLevel) throws IOException {
        String indent = "  ".repeat(indentLevel);
        for (int i = 0; i < list.size(); i++) {
            Object elem = list.get(i);
            String currentPath = pathPrefix + "[" + i + "]";
            List<String> commentLines = comments != null ? comments.get(currentPath) : null;
            if (commentLines != null) {
                for (String line : commentLines) {
                    writer.write(indent + "# " + line + System.lineSeparator());
                }
            }
            if (elem instanceof Map) {
                writer.write(indent + "- " + System.lineSeparator());
                writeWithComments(writer, castMap((Map<?, ?>) elem), comments, currentPath, indentLevel + 1);
            } else if (elem instanceof List) {
                writer.write(indent + "- " + System.lineSeparator());
                writeList(writer, (List<?>) elem, comments, currentPath, indentLevel + 1);
            } else if (elem instanceof String str && str.contains("\n")) {
                writer.write(indent + "- |" + System.lineSeparator());
                writeMultilineValue(writer, str, indentLevel + 2);
            } else {
                writer.write(indent + "- " + formatScalar(elem) + System.lineSeparator());
            }
        }
    }

    private static void writeMultilineValue(Writer writer, String value, int indentLevel) throws IOException {
        String indent = "  ".repeat(indentLevel);
        String[] lines = value.split("\\r?\\n", -1);
        for (String line : lines) {
            writer.write(indent + line + System.lineSeparator());
        }
    }

    private static void writeCommentLines(Writer writer,
                                          List<String> lines,
                                          String prefix,
                                          int indentLevel) throws IOException {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        String indent = "  ".repeat(indentLevel);
        for (String line : lines) {
            writer.write(indent + prefix + line + System.lineSeparator());
        }
    }

    private static boolean isListOfMaps(List<?> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (Object item : list) {
            if (!(item instanceof Map)) {
                return false;
            }
        }
        return true;
    }

    private static String formatTomlValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return formatTomlInlineTable(castMap(map));
        }
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) {
                String formatted = formatTomlValue(item);
                if (formatted != null) {
                    parts.add(formatted);
                }
            }
            return "[" + String.join(", ", parts) + "]";
        }
        return formatScalar(value);
    }

    private static String formatTomlInlineTable(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String formatted = formatTomlValue(entry.getValue());
            if (formatted != null) {
                parts.add(formatTomlKey(entry.getKey()) + " = " + formatted);
            }
        }
        return "{" + String.join(", ", parts) + "}";
    }

    private static String formatJsonKey(String key) {
        if (key == null) {
            return "\"\"";
        }
        return formatScalar(key);
    }

    private static String formatTomlKey(String key) {
        if (key == null) {
            return "\"\"";
        }
        return formatScalar(key);
    }

    private static String formatTomlPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String[] parts = path.split("\\.");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append('.');
            }
            builder.append(formatTomlKey(parts[i]));
        }
        return builder.toString();
    }

    private static String formatScalar(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return SCALAR_MAPPER.writeValueAsString(value).trim();
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
