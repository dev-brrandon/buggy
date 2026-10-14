package de.legitserver.assistant.integrations.conversations;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny bounded codec for the Foundation's flat string metadata map. */
final class FlatJsonMapCodec {
    private FlatJsonMapCodec() {}

    static String encode(Map<String, String> values) {
        if (values == null || values.isEmpty()) return "{}";
        StringBuilder result = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) result.append(',');
            first = false;
            result.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
        }
        return result.append('}').toString();
    }

    static Map<String, String> decode(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return Map.of();
        Parser parser = new Parser(json);
        return Map.copyOf(parser.parseObject());
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static final class Parser {
        private final String text;
        private int pos;

        private Parser(String text) {
            this.text = text;
        }

        Map<String, String> parseObject() {
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            skipWhitespace();
            expect('{');
            skipWhitespace();
            if (peek('}')) {
                pos++;
                finish();
                return result;
            }
            while (true) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                String value = parseString();
                result.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    pos++;
                    finish();
                    return result;
                }
                expect(',');
            }
        }

        private String parseString() {
            skipWhitespace();
            expect('"');
            StringBuilder out = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (c == '"') return out.toString();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (pos >= text.length()) throw new IllegalArgumentException("Invalid JSON escape");
                char escaped = text.charAt(pos++);
                switch (escaped) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> out.append(parseUnicode());
                    default -> throw new IllegalArgumentException("Invalid JSON escape: " + escaped);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private char parseUnicode() {
            if (pos + 4 > text.length()) throw new IllegalArgumentException("Invalid unicode escape");
            String hex = text.substring(pos, pos + 4);
            pos += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("Invalid unicode escape", failure);
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (pos >= text.length() || text.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at position " + pos);
            }
            pos++;
            skipWhitespace();
        }

        private boolean peek(char c) {
            return pos < text.length() && text.charAt(pos) == c;
        }

        private void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
        }

        private void finish() {
            skipWhitespace();
            if (pos != text.length()) throw new IllegalArgumentException("Trailing JSON data");
        }
    }
}
