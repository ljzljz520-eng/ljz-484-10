package legalcase.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析/序列化工具（零三方依赖）。
 * 支持：对象(保持插入序)、数组、字符串、数字(统一存 Double)、true/false/null。
 */
public final class Json {

    private Json() {}

    // ---------- 序列化 ----------

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    public static String pretty(Object value) {
        StringBuilder sb = new StringBuilder();
        writePretty(sb, value, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Boolean b) {
            sb.append(b.booleanValue());
        } else if (value instanceof Double d) {
            sb.append(numToString(d));
        } else if (value instanceof Integer i) {
            sb.append(i.intValue());
        } else if (value instanceof Long l) {
            sb.append(l.longValue());
        } else if (value instanceof Number n) {
            sb.append(n.toString());
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                write(sb, item);
            }
            sb.append(']');
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    private static void writePretty(StringBuilder sb, Object value, int indent) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) { sb.append("{}"); return; }
            sb.append("{\n");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(",\n");
                first = false;
                pad(sb, indent + 1);
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(": ");
                writePretty(sb, e.getValue(), indent + 1);
            }
            sb.append('\n');
            pad(sb, indent);
            sb.append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) { sb.append("[]"); return; }
            sb.append("[\n");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",\n");
                first = false;
                pad(sb, indent + 1);
                writePretty(sb, item, indent + 1);
            }
            sb.append('\n');
            pad(sb, indent);
            sb.append(']');
        } else {
            write(sb, value);
        }
    }

    private static void pad(StringBuilder sb, int level) {
        sb.append("  ".repeat(level));
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** Double 去掉无意义的 .0，与 JSON 数值字面量保持一致。 */
    static String numToString(Double d) {
        if (d.isNaN() || d.isInfinite()) return "null";
        if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) {
            return Long.toString(d.longValue());
        }
        return d.toString();
    }

    // ---------- 解析 ----------

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.readValue();
        p.skipWs();
        if (!p.eof()) throw new IllegalArgumentException("JSON 尾部存在多余字符，位置 " + p.pos);
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new IllegalArgumentException("期望 JSON 对象");
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) { this.s = s; }

        boolean eof() { return pos >= s.length(); }

        char peek() { return s.charAt(pos); }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else break;
            }
        }

        Object readValue() {
            skipWs();
            if (eof()) throw new IllegalArgumentException("意外结束的 JSON");
            char c = peek();
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't', 'f' -> readBoolean();
                case 'n' -> readNull();
                default -> readNumber();
            };
        }

        Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWs();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                expect(':');
                Object val = readValue();
                map.put(key, val);
                skipWs();
                char c = next();
                if (c == '}') break;
                if (c != ',') throw new IllegalArgumentException("期望 ',' 或 '}'，位置 " + (pos - 1));
            }
            return map;
        }

        List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWs();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                Object val = readValue();
                list.add(val);
                skipWs();
                char c = next();
                if (c == ']') break;
                if (c != ',') throw new IllegalArgumentException("期望 ',' 或 ']'，位置 " + (pos - 1));
            }
            return list;
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (eof()) throw new IllegalArgumentException("未闭合的字符串");
                char c = next();
                if (c == '"') break;
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (pos + 4 > s.length()) throw new IllegalArgumentException("非法 \\u 转义");
                            int code = Integer.parseInt(s.substring(pos, pos + 4), 16);
                            pos += 4;
                            sb.append((char) code);
                        }
                        default -> throw new IllegalArgumentException("非法转义字符 \\" + e);
                    }
                } else if (c < 0x20) {
                    throw new IllegalArgumentException("字符串中存在未转义控制字符");
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Object readNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (!eof()) {
                char c = peek();
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') pos++;
                else break;
            }
            String num = s.substring(start, pos);
            if (num.isEmpty() || num.equals("-")) throw new IllegalArgumentException("非法数字，位置 " + start);
            return Double.parseDouble(num);
        }

        Boolean readBoolean() {
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw new IllegalArgumentException("非法布尔值，位置 " + pos);
        }

        Object readNull() {
            if (s.startsWith("null", pos)) { pos += 4; return null; }
            throw new IllegalArgumentException("非法字面量，位置 " + pos);
        }

        char next() {
            if (eof()) throw new IllegalArgumentException("意外结束的 JSON");
            return s.charAt(pos++);
        }

        void expect(char c) {
            char actual = next();
            if (actual != c) throw new IllegalArgumentException("期望 '" + c + "'，实际 '" + actual + "'，位置 " + (pos - 1));
        }
    }
}
