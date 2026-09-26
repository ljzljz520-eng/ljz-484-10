package com.lawcase;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * 极简 JSON 工具（仅依赖 JDK）：序列化 + 解析。
 * 支持对象(Map)、数组(List)、字符串、数字、布尔、null。
 */
public final class Json {
    private Json() {}

    public static String stringify(Object o) {
        StringBuilder sb = new StringBuilder();
        write(o, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object o, StringBuilder sb) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String) {
            writeString((String) o, sb);
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(e.getKey(), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (o instanceof Collection) {
            sb.append('[');
            boolean first = true;
            for (Object item : (Collection<Object>) o) {
                if (!first) sb.append(',');
                first = false;
                write(item, sb);
            }
            sb.append(']');
        } else {
            writeString(String.valueOf(o), sb);
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.readValue();
        p.skipWs();
        if (!p.eof()) throw p.error("JSON 末尾存在多余内容");
        return v;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) { this.s = s == null ? "" : s; }

        boolean eof() { return pos >= s.length(); }

        RuntimeException error(String msg) {
            return new RuntimeException(msg + "（位置 " + pos + "）");
        }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else break;
            }
        }

        Object readValue() {
            skipWs();
            if (eof()) throw error("JSON 内容不完整");
            char c = s.charAt(pos);
            switch (c) {
                case '{': return readObject();
                case '[': return readArray();
                case '"': return readString();
                case 't': expect("true");  return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null");  return null;
                default:  return readNumber();
            }
        }

        private void expect(String word) {
            if (!s.startsWith(word, pos)) throw error("无法解析 '" + word + "'");
            pos += word.length();
        }

        private Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWs();
            if (!eof() && s.charAt(pos) == '}') { pos++; return map; }
            while (true) {
                skipWs();
                if (eof() || s.charAt(pos) != '"') throw error("对象键必须是字符串");
                String key = readString();
                skipWs();
                if (eof() || s.charAt(pos) != ':') throw error("对象键后缺少 ':'");
                pos++;
                Object val = readValue();
                map.put(key, val);
                skipWs();
                if (eof()) throw error("对象未闭合");
                char c = s.charAt(pos);
                if (c == ',') { pos++; continue; }
                if (c == '}') { pos++; return map; }
                throw error("对象元素之间缺少 ','");
            }
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWs();
            if (!eof() && s.charAt(pos) == ']') { pos++; return list; }
            while (true) {
                list.add(readValue());
                skipWs();
                if (eof()) throw error("数组未闭合");
                char c = s.charAt(pos);
                if (c == ',') { pos++; continue; }
                if (c == ']') { pos++; return list; }
                throw error("数组元素之间缺少 ','");
            }
        }

        private String readString() {
            StringBuilder sb = new StringBuilder();
            pos++; // opening quote
            while (true) {
                if (eof()) throw error("字符串未闭合");
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (eof()) throw error("转义字符不完整");
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > s.length()) throw error("unicode 转义不完整");
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default: throw error("非法转义字符 \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Object readNumber() {
            int start = pos;
            if (!eof() && s.charAt(pos) == '-') pos++;
            boolean isDouble = false;
            while (!eof()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') pos++;
                else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { isDouble = true; pos++; }
                else break;
            }
            if (start == pos) throw error("无法解析的值");
            String num = s.substring(start, pos);
            try {
                return isDouble ? (Object) Double.parseDouble(num) : (Object) Long.parseLong(num);
            } catch (NumberFormatException ex) {
                throw error("数字格式错误: " + num);
            }
        }
    }
}
