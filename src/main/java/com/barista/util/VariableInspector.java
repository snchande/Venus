package com.barista.util;

import com.barista.model.ExecutionResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared helpers for adding "variable inspection" (the right-side slide-out
 * Variables panel) to subprocess-based executors (Node, TypeScript, C#, F#,
 * C++, full Java).
 *
 * <p>JShell has built-in introspection (see {@code ShellSession}); the other
 * languages execute as one-shot subprocesses. To recover their state we
 *
 * <ol>
 *   <li>parse the user's cell source for top-level declarations,</li>
 *   <li>append a small, language-specific "trailer" snippet that emits the
 *       variables back through stdout using a sentinel marker, and</li>
 *   <li>after the subprocess exits, strip the sentinel block out of stdout
 *       and parse it into {@link ExecutionResult.Variable} objects.</li>
 * </ol>
 *
 * <p>The marker format keeps each variable on a single line so it survives
 * line-buffered stdout capture:
 * <pre>
 *   __BARISTA_VARS_BEGIN__
 *   __BARISTA_VAR__nametypevalue-with-newlines-as-\n
 *   __BARISTA_VARS_END__
 * </pre>
 *
 * <p>SOH () is used as the field separator because it never legitimately
 * appears in identifiers, type names, or stdout content.
 */
public final class VariableInspector {

    private VariableInspector() {}

    public static final String BEGIN_MARKER = "__BARISTA_VARS_BEGIN__";
    public static final String END_MARKER   = "__BARISTA_VARS_END__";
    public static final String VAR_PREFIX   = "__BARISTA_VAR__";
    public static final char   SEP          = '';

    /** Result of stripping the sentinel block out of a subprocess' stdout. */
    public static final class ParsedOutput {
        public final String cleanedStdout;
        public final List<ExecutionResult.Variable> variables;
        public ParsedOutput(String cleanedStdout, List<ExecutionResult.Variable> variables) {
            this.cleanedStdout = cleanedStdout;
            this.variables = variables;
        }
    }

    /**
     * Strip the sentinel block out of the captured stdout and return the
     * cleaned text + the parsed variables. If no sentinel is present (the
     * cell crashed before the trailer ran, or didn't declare anything) the
     * original stdout is returned and the variable list is empty.
     */
    public static ParsedOutput parseDumpFromOutput(String stdout) {
        List<ExecutionResult.Variable> vars = new ArrayList<>();
        if (stdout == null || stdout.isEmpty()) return new ParsedOutput(stdout, vars);

        int begin = stdout.lastIndexOf(BEGIN_MARKER);
        if (begin < 0) return new ParsedOutput(stdout, vars);
        int end = stdout.indexOf(END_MARKER, begin);
        if (end < 0) return new ParsedOutput(stdout, vars);

        String block = stdout.substring(begin, end + END_MARKER.length());
        for (String line : block.split("\\r?\\n", -1)) {
            if (!line.startsWith(VAR_PREFIX + SEP)) continue;
            String[] parts = line.substring(VAR_PREFIX.length() + 1).split(String.valueOf(SEP), -1);
            if (parts.length < 3) continue;
            String name  = parts[0];
            String type  = parts[1];
            String value = parts[2].replace("\\n", "\n").replace("\\r", "\r");
            vars.add(new ExecutionResult.Variable(name, type, value));
        }

        // Remove the sentinel block (plus the leading newline if any) so users
        // don't see the marker noise in their output panel.
        String head = stdout.substring(0, begin);
        String tail = stdout.substring(end + END_MARKER.length());
        if (head.endsWith("\n")) head = head.substring(0, head.length() - 1);
        if (tail.startsWith("\n")) tail = tail.substring(1);
        String cleaned = head + (head.isEmpty() || tail.isEmpty() ? "" : "\n") + tail;
        return new ParsedOutput(cleaned, vars);
    }

    // ════════════════════════════════════════════════════════════════════
    // Per-language top-level declaration extractors
    // ════════════════════════════════════════════════════════════════════

    /**
     * Find names declared at the top level of a JavaScript source.
     * Matches {@code let|const|var <name>}, {@code function <name>},
     * {@code class <name>}. Only matches at top-level scope (depth 0).
     */
    public static List<String> parseJsDeclarations(String source) {
        return parseCurlyTopLevelDecls(source, JS_DECL_PATTERN, /*tsExtractType*/ false).keySet().stream().toList();
    }

    /**
     * Find top-level declarations in a TypeScript source. Returns a map of
     * {@code name -> declaredType} (the type is {@code ""} when no annotation
     * was given — Node's runtime {@code typeof} will fill it in).
     */
    public static Map<String, String> parseTsDeclarations(String source) {
        return parseCurlyTopLevelDecls(source, JS_DECL_PATTERN, /*tsExtractType*/ true);
    }

    /** Find top-level {@code var x = ...} / {@code int x = ...} declarations in C# source. */
    public static List<String> parseCSharpDeclarations(String source) {
        return parseCurlyTopLevelDecls(source, CSHARP_DECL_PATTERN, false).keySet().stream().toList();
    }

    /** Find {@code let x = ...} bindings at column 0 in F# source. */
    public static List<String> parseFSharpDeclarations(String source) {
        List<String> names = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (source == null) return names;
        // F# is indentation-sensitive; top-level lets are at column 0.
        for (String raw : stripFsComments(source).split("\\r?\\n", -1)) {
            if (raw.startsWith(" ") || raw.startsWith("\t")) continue;
            Matcher m = FSHARP_LET_PATTERN.matcher(raw);
            if (m.find()) {
                String name = m.group(1);
                if (isReservedKeyword(name)) continue;
                if (seen.add(name)) names.add(name);
            }
        }
        return names;
    }

    /**
     * Find scalar/string declarations in a block of C++ statements (no enclosing
     * {@code int main()}). Used by the auto-wrap path of the C++ executor where
     * the user's code is just statements that we splice into a generated main.
     * Only emits decls at the top of the block (brace depth 0).
     */
    public static List<CppDecl> parseCppStatementDeclarations(String stmts) {
        List<CppDecl> decls = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (stmts == null) return decls;
        String stripped = stripCStyleComments(stmts);
        int depth = 0, parenDepth = 0;
        StringBuilder buf = new StringBuilder();
        boolean inString = false; char strQuote = 0;
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (inString) {
                buf.append(c);
                if (c == '\\' && i + 1 < stripped.length()) { buf.append(stripped.charAt(++i)); continue; }
                if (c == strQuote) inString = false;
                continue;
            }
            if (c == '"' || c == '\'') { inString = true; strQuote = c; buf.append(c); continue; }
            if (c == '{') depth++;
            else if (c == '}') depth = Math.max(0, depth - 1);
            else if (c == '(') parenDepth++;
            else if (c == ')') parenDepth = Math.max(0, parenDepth - 1);
            if (c == ';' && depth == 0 && parenDepth == 0) {
                String stmt = buf.toString().trim();
                buf.setLength(0);
                Matcher m = CPP_DECL_PATTERN.matcher(stmt);
                if (m.find()) {
                    String type = m.group(1).trim().replaceAll("\\s+", " ");
                    String name = m.group(2);
                    if (!isReservedKeyword(name) && seen.add(name)) decls.add(new CppDecl(name, type));
                }
                continue;
            }
            buf.append(c);
        }
        return decls;
    }

    /** Find top-level scalar/string declarations in C++ source. */
    public static List<CppDecl> parseCppDeclarations(String source) {
        List<CppDecl> decls = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (source == null) return decls;
        String stripped = stripCStyleComments(source);
        // Track brace depth so we only emit declarations from the body of main()
        // (the most common cell shape) — depth 1 inside the function.
        int depth = 0;
        StringBuilder buf = new StringBuilder();
        boolean inMain = false;
        // Detect "int main(...){" and start emitting once we're inside.
        Matcher mainStart = Pattern.compile("\\bint\\s+main\\s*\\([^)]*\\)\\s*\\{").matcher(stripped);
        int mainOpen = mainStart.find() ? mainStart.end() - 1 : -1;
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (c == '{') { depth++; if (i == mainOpen) inMain = true; }
            else if (c == '}') { depth--; if (depth == 0) inMain = false; }
            else if (c == ';' && inMain && depth == 1) {
                String stmt = buf.toString().trim();
                buf.setLength(0);
                Matcher m = CPP_DECL_PATTERN.matcher(stmt);
                if (m.find()) {
                    String type = m.group(1).trim().replaceAll("\\s+", " ");
                    String name = m.group(2);
                    if (!isReservedKeyword(name) && seen.add(name)) {
                        decls.add(new CppDecl(name, type));
                    }
                }
                continue;
            }
            else if (c == '\n') { /* leave \n in buf */ }
            if (inMain && depth >= 1) buf.append(c);
        }
        return decls;
    }

    /** Returned by the C++ parser — we need the declared type as well. */
    public record CppDecl(String name, String type) {}

    /**
     * Find local variables declared inside {@code main(...)} of a Java cell
     * (Java compile mode). Returns names in declaration order.
     */
    public static List<String> parseJavaMainLocals(String source) {
        List<String> names = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (source == null) return names;
        String stripped = stripCStyleComments(source);
        Matcher m = Pattern.compile(
                "\\bpublic\\s+static\\s+void\\s+main\\s*\\([^)]*\\)\\s*\\{").matcher(stripped);
        if (!m.find()) return names;
        int braceStart = m.end() - 1;
        // Walk balanced braces to find the matching close
        int depth = 0;
        int closeIdx = -1;
        for (int i = braceStart; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) { closeIdx = i; break; } }
        }
        if (closeIdx < 0) return names;
        String body = stripped.substring(braceStart + 1, closeIdx);
        // Match top-of-body and one-level deep declarations: type name [=..] ;
        // Conservative: only "primitive | String | var" — covers the common cell.
        Matcher d = JAVA_LOCAL_PATTERN.matcher(body);
        int depth2 = 0;
        int last = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') depth2++;
            else if (c == '}') depth2--;
            else if (c == ';' && depth2 == 0) {
                String stmt = body.substring(last, i + 1);
                Matcher s = JAVA_LOCAL_PATTERN.matcher(stmt);
                if (s.find()) {
                    String name = s.group(1);
                    if (!isReservedKeyword(name) && seen.add(name)) names.add(name);
                }
                last = i + 1;
            }
        }
        return names;
    }

    // ════════════════════════════════════════════════════════════════════
    // Per-language trailer builders
    // ════════════════════════════════════════════════════════════════════

    /** JS trailer that emits all declared names. Each var is wrapped in its own
     *  try/catch so a single bad reference doesn't kill the whole dump. */
    public static String buildJsTrailer(List<String> names) {
        if (names == null || names.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n;(function __barista_dump(){\n");
        sb.append("  const __SEP = String.fromCharCode(1);\n");
        sb.append("  const __safe = (v) => {\n");
        sb.append("    try {\n");
        sb.append("      if (v === null) return 'null';\n");
        sb.append("      if (v === undefined) return 'undefined';\n");
        sb.append("      if (typeof v === 'function') return '[function ' + (v.name||'') + ']';\n");
        sb.append("      if (typeof v === 'string') return v;\n");
        sb.append("      return JSON.stringify(v);\n");
        sb.append("    } catch (e) { try { return String(v); } catch { return '<unprintable>'; } }\n");
        sb.append("  };\n");
        sb.append("  const __typeOf = (v) => {\n");
        sb.append("    if (v === null) return 'null';\n");
        sb.append("    if (v === undefined) return 'undefined';\n");
        sb.append("    if (Array.isArray(v)) return 'Array(' + v.length + ')';\n");
        sb.append("    if (typeof v === 'object') return (v.constructor && v.constructor.name) || 'Object';\n");
        sb.append("    return typeof v;\n");
        sb.append("  };\n");
        sb.append("  const __emit = (n, v) => {\n");
        sb.append("    let s = __safe(v);\n");
        sb.append("    if (s && s.length > 500) s = s.slice(0,500) + '\\u2026';\n");
        sb.append("    s = s.replace(/\\\\/g,'\\\\\\\\').replace(/\\n/g,'\\\\n').replace(/\\r/g,'\\\\r');\n");
        sb.append("    process.stdout.write('").append(VAR_PREFIX).append("' + __SEP + n + __SEP + __typeOf(v) + __SEP + s + '\\n');\n");
        sb.append("  };\n");
        sb.append("  process.stdout.write('\\n").append(BEGIN_MARKER).append("\\n');\n");
        for (String n : names) {
            sb.append("  try { __emit(").append(jsString(n)).append(", ").append(n).append("); } catch(e) {}\n");
        }
        sb.append("  process.stdout.write('").append(END_MARKER).append("\\n');\n");
        sb.append("})();\n");
        return sb.toString();
    }

    /** TS trailer — same as JS but with explicit type annotations off (we use any). */
    public static String buildTsTrailer(Map<String, String> declared) {
        if (declared == null || declared.isEmpty()) return "";
        // Same runtime payload as JS, but emit the user's declared TS type when present.
        StringBuilder sb = new StringBuilder();
        sb.append("\n;(function __barista_dump(){\n");
        sb.append("  const __SEP = String.fromCharCode(1);\n");
        sb.append("  const __safe = (v: any) => {\n");
        sb.append("    try {\n");
        sb.append("      if (v === null) return 'null';\n");
        sb.append("      if (v === undefined) return 'undefined';\n");
        sb.append("      if (typeof v === 'function') return '[function ' + (v.name||'') + ']';\n");
        sb.append("      if (typeof v === 'string') return v;\n");
        sb.append("      return JSON.stringify(v);\n");
        sb.append("    } catch (e) { try { return String(v); } catch { return '<unprintable>'; } }\n");
        sb.append("  };\n");
        sb.append("  const __typeOf = (v: any) => {\n");
        sb.append("    if (v === null) return 'null';\n");
        sb.append("    if (v === undefined) return 'undefined';\n");
        sb.append("    if (Array.isArray(v)) return 'Array(' + v.length + ')';\n");
        sb.append("    if (typeof v === 'object') return (v.constructor && v.constructor.name) || 'Object';\n");
        sb.append("    return typeof v;\n");
        sb.append("  };\n");
        sb.append("  const __emit = (n: string, t: string, v: any) => {\n");
        sb.append("    let s = __safe(v);\n");
        sb.append("    if (s && s.length > 500) s = s.slice(0,500) + '\\u2026';\n");
        sb.append("    s = s.replace(/\\\\/g,'\\\\\\\\').replace(/\\n/g,'\\\\n').replace(/\\r/g,'\\\\r');\n");
        sb.append("    process.stdout.write('").append(VAR_PREFIX).append("' + __SEP + n + __SEP + (t || __typeOf(v)) + __SEP + s + '\\n');\n");
        sb.append("  };\n");
        sb.append("  process.stdout.write('\\n").append(BEGIN_MARKER).append("\\n');\n");
        for (Map.Entry<String, String> e : declared.entrySet()) {
            String name = e.getKey(), type = e.getValue();
            sb.append("  try { __emit(").append(jsString(name)).append(", ")
              .append(jsString(type == null ? "" : type)).append(", ").append(name).append("); } catch(e) {}\n");
        }
        sb.append("  process.stdout.write('").append(END_MARKER).append("\\n');\n");
        sb.append("})();\n");
        return sb.toString();
    }

    /** C# trailer — assumes the script is top-level statements (script style). */
    public static String buildCSharpTrailer(List<String> names) {
        if (names == null || names.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n// ── Arima variable dump (auto-injected) ──\n");
        sb.append("System.Console.Out.Flush();\n");
        sb.append("System.Console.WriteLine(\"\\n").append(BEGIN_MARKER).append("\");\n");
        sb.append("System.Action<string, object> __barista_emit = (n, v) => {\n");
        sb.append("  string t = v == null ? \"null\" : v.GetType().Name;\n");
        sb.append("  string s; try { s = v?.ToString() ?? \"null\"; } catch { s = \"<unprintable>\"; }\n");
        sb.append("  if (s.Length > 500) s = s.Substring(0,500) + \"\\u2026\";\n");
        sb.append("  s = s.Replace(\"\\\\\", \"\\\\\\\\\").Replace(\"\\n\", \"\\\\n\").Replace(\"\\r\", \"\\\\r\");\n");
        sb.append("  System.Console.WriteLine(\"").append(VAR_PREFIX).append("\\u0001\" + n + \"\\u0001\" + t + \"\\u0001\" + s);\n");
        sb.append("};\n");
        for (String n : names) {
            // Each emit wrapped in try/catch so a single late exception doesn't truncate the dump
            sb.append("try { __barista_emit(\"").append(escapeCs(n)).append("\", (object)").append(n).append("); } catch {}\n");
        }
        sb.append("System.Console.WriteLine(\"").append(END_MARKER).append("\");\n");
        return sb.toString();
    }

    /** F# trailer — emit a single block of let-bound printfn calls. */
    public static String buildFSharpTrailer(List<String> names) {
        if (names == null || names.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n// ── Arima variable dump (auto-injected) ──\n");
        sb.append("printfn \"\\n").append(BEGIN_MARKER).append("\"\n");
        sb.append("let __barista_emit (n: string) (v: obj) =\n");
        sb.append("    let t = if isNull v then \"null\" else v.GetType().Name\n");
        sb.append("    let s = if isNull v then \"null\" else (try v.ToString() with _ -> \"<unprintable>\")\n");
        sb.append("    let s2 = if s.Length > 500 then s.Substring(0,500) + \"\\u2026\" else s\n");
        sb.append("    let s3 = s2.Replace(\"\\\\\", \"\\\\\\\\\").Replace(\"\\n\", \"\\\\n\").Replace(\"\\r\", \"\\\\r\")\n");
        sb.append("    printfn \"").append(VAR_PREFIX).append("\\u0001%s\\u0001%s\\u0001%s\" n t s3\n");
        for (String n : names) {
            sb.append("try __barista_emit \"").append(escapeFs(n)).append("\" (box ").append(n).append(") with _ -> ()\n");
        }
        sb.append("printfn \"").append(END_MARKER).append("\"\n");
        return sb.toString();
    }

    /** C++ trailer — printfs each known scalar/string variable. */
    public static String buildCppTrailer(List<CppDecl> decls) {
        if (decls == null || decls.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n// ── Arima variable dump (auto-injected) ──\n");
        sb.append("    std::cout << \"\\n").append(BEGIN_MARKER).append("\\n\";\n");
        sb.append("    {\n");
        sb.append("        auto __barista_str_of = [](auto&& v) -> std::string {\n");
        sb.append("            std::ostringstream __ss; __ss << v; return __ss.str();\n");
        sb.append("        };\n");
        sb.append("        auto __barista_escape = [](std::string s) -> std::string {\n");
        sb.append("            std::string r; r.reserve(s.size());\n");
        sb.append("            for (char c : s) {\n");
        sb.append("                if (c == '\\\\') r += \"\\\\\\\\\";\n");
        sb.append("                else if (c == '\\n') r += \"\\\\n\";\n");
        sb.append("                else if (c == '\\r') r += \"\\\\r\";\n");
        sb.append("                else r += c;\n");
        sb.append("            }\n");
        sb.append("            return r;\n");
        sb.append("        };\n");
        for (CppDecl d : decls) {
            String t = d.type();
            String n = d.name();
            // Best-effort string-of conversion. Anything not ostreamable will not compile,
            // which is why we only emit decls for types we recognise (scalar/string).
            sb.append("        try { std::string __vs = __barista_str_of(").append(n).append(");\n");
            sb.append("              if (__vs.size() > 500) __vs = __vs.substr(0,500) + \"\\u2026\";\n");
            sb.append("              __vs = __barista_escape(__vs);\n");
            sb.append("              std::cout << \"").append(VAR_PREFIX).append("\\x01").append(escapeC(n))
              .append("\\x01").append(escapeC(t)).append("\\x01\" << __vs << \"\\n\";\n");
            sb.append("        } catch (...) {}\n");
        }
        sb.append("        std::cout << \"").append(END_MARKER).append("\\n\";\n");
        sb.append("    }\n");
        return sb.toString();
    }

    /** Java (full-compile mode) trailer — placed at the end of main(). */
    public static String buildJavaTrailer(List<String> names) {
        if (names == null || names.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n// ── Arima variable dump (auto-injected) ──\n");
        sb.append("System.out.flush();\n");
        sb.append("System.out.println(\"\\n").append(BEGIN_MARKER).append("\");\n");
        sb.append("java.util.function.BiConsumer<String, Object> __barista_emit = (n, v) -> {\n");
        sb.append("  String t = v == null ? \"null\" : v.getClass().getSimpleName();\n");
        sb.append("  String s; try { s = v == null ? \"null\" : String.valueOf(v); } catch (Throwable e) { s = \"<unprintable>\"; }\n");
        sb.append("  if (s.length() > 500) s = s.substring(0,500) + \"\\u2026\";\n");
        sb.append("  s = s.replace(\"\\\\\", \"\\\\\\\\\").replace(\"\\n\", \"\\\\n\").replace(\"\\r\", \"\\\\r\");\n");
        sb.append("  System.out.println(\"").append(VAR_PREFIX).append("\\u0001\" + n + \"\\u0001\" + t + \"\\u0001\" + s);\n");
        sb.append("};\n");
        for (String n : names) {
            // Box primitive locals so they fit the Object BiConsumer
            sb.append("try { __barista_emit(\"").append(escapeJava(n)).append("\", (Object)(").append(n).append(")); } catch (Throwable __ve) {}\n");
        }
        sb.append("System.out.println(\"").append(END_MARKER).append("\");\n");
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════════
    // Internals
    // ════════════════════════════════════════════════════════════════════

    /** {@code let|const|var <name>} or {@code function <name>} or {@code class <name>}. */
    private static final Pattern JS_DECL_PATTERN = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:let|const|var|function|class)\\s+(?:\\*\\s*)?([A-Za-z_$][A-Za-z0-9_$]*)" +
            "(?:\\s*:\\s*([^=,;\\n]+?))?(?=\\s*[=,;({\\n])"
    );

    /** C# top-level declaration: {@code var x =}, {@code int x =}, {@code string x =}, etc. */
    private static final Pattern CSHARP_DECL_PATTERN = Pattern.compile(
            "^\\s*(?:var|int|long|short|byte|sbyte|uint|ulong|ushort|float|double|decimal|" +
            "bool|char|string|object|dynamic|DateTime|TimeSpan|Guid|" +
            "[A-Z][A-Za-z0-9_<>\\[\\],\\s\\?]*?)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*[=;]"
    );

    /** F# {@code let [rec|mutable] <name> = ...} at column 0. */
    private static final Pattern FSHARP_LET_PATTERN = Pattern.compile(
            "^let\\s+(?:rec\\s+|mutable\\s+|inline\\s+)*([a-zA-Z_][a-zA-Z0-9_']*)\\s*[:=]");

    /** Java local: very conservative — primitives + String + var. */
    private static final Pattern JAVA_LOCAL_PATTERN = Pattern.compile(
            "(?:^|[\\s;{])(?:final\\s+)?(?:int|long|short|byte|float|double|char|boolean|String|var)\\s+" +
            "([A-Za-z_][A-Za-z0-9_]*)\\s*[=;]");

    /** C++ scalar/string declaration: {@code int x = ...}, {@code std::string s = ...}, {@code auto x = ...}. */
    private static final Pattern CPP_DECL_PATTERN = Pattern.compile(
            "(?:^|[\\s;{])((?:const\\s+|unsigned\\s+|signed\\s+|long\\s+|short\\s+|static\\s+)*" +
            "(?:auto|int|long|short|unsigned|signed|float|double|char|bool|size_t|ssize_t|" +
            "std::string|std::string_view|std::int(?:32|64|16|8)_t|std::uint(?:32|64|16|8)_t))\\s+" +
            "([A-Za-z_][A-Za-z0-9_]*)\\s*[=({;]");

    /**
     * Walk a curly-brace-delimited source and apply the per-line declaration
     * regex only when we're at brace-depth 0 (i.e. truly top level).
     * For TS, the regex's second capture group is the declared type.
     */
    private static Map<String, String> parseCurlyTopLevelDecls(String source, Pattern decl, boolean tsExtractType) {
        Map<String, String> out = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) return out;
        String stripped = stripCStyleComments(source);
        int depth = 0, parenDepth = 0;
        StringBuilder line = new StringBuilder();
        boolean inString = false;
        char strQuote = 0;
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (inString) {
                line.append(c);
                if (c == '\\' && i + 1 < stripped.length()) { line.append(stripped.charAt(++i)); continue; }
                if (c == strQuote) inString = false;
                continue;
            }
            if (c == '"' || c == '\'' || c == '`') { inString = true; strQuote = c; line.append(c); continue; }
            if (c == '{') depth++;
            else if (c == '}') depth = Math.max(0, depth - 1);
            else if (c == '(') parenDepth++;
            else if (c == ')') parenDepth = Math.max(0, parenDepth - 1);
            if (c == '\n') {
                if (depth == 0 && parenDepth == 0) matchTopLevelLine(line.toString(), decl, tsExtractType, out);
                line.setLength(0);
            } else {
                line.append(c);
            }
        }
        if (depth == 0 && parenDepth == 0 && line.length() > 0) {
            matchTopLevelLine(line.toString(), decl, tsExtractType, out);
        }
        return out;
    }

    private static void matchTopLevelLine(String line, Pattern decl, boolean tsExtractType, Map<String, String> out) {
        // A single top-level "let a = 1, b = 2;" splits into two declarations — we
        // handle this by repeatedly matching, advancing the cursor each time.
        Matcher m = decl.matcher(line);
        int start = 0;
        while (m.find(start)) {
            String name = m.group(1);
            String type = "";
            if (tsExtractType && m.groupCount() >= 2 && m.group(2) != null) {
                type = m.group(2).trim();
            }
            if (!isReservedKeyword(name) && !out.containsKey(name)) {
                out.put(name, type);
            }
            start = m.end();
            if (start >= line.length()) break;
        }
    }

    /** Strip /* …  comments and // … line comments without touching strings. */
    public static String stripCStyleComments(String src) {
        if (src == null) return "";
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        boolean inStr = false; char q = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (inStr) {
                out.append(c);
                if (c == '\\' && i + 1 < src.length()) { out.append(src.charAt(++i)); i++; continue; }
                if (c == q) inStr = false;
                i++; continue;
            }
            if (c == '"' || c == '\'' || c == '`') { inStr = true; q = c; out.append(c); i++; continue; }
            if (c == '/' && i + 1 < src.length()) {
                char n = src.charAt(i + 1);
                if (n == '/') { while (i < src.length() && src.charAt(i) != '\n') i++; continue; }
                if (n == '*') { i += 2; while (i + 1 < src.length() && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) i++; i += 2; continue; }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** Strip {@code //} and {@code (* ... *)} comments out of F# source. */
    private static String stripFsComments(String src) {
        if (src == null) return "";
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                while (i < src.length() && src.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '(' && i + 1 < src.length() && src.charAt(i + 1) == '*') {
                i += 2; while (i + 1 < src.length() && !(src.charAt(i) == '*' && src.charAt(i + 1) == ')')) i++; i += 2; continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean isReservedKeyword(String s) {
        return JAVA_KEYWORDS.contains(s) || JS_KEYWORDS.contains(s);
    }

    private static final Set<String> JAVA_KEYWORDS = new LinkedHashSet<>(Arrays.asList(
        "abstract","assert","boolean","break","byte","case","catch","char","class","const",
        "continue","default","do","double","else","enum","extends","final","finally","float",
        "for","goto","if","implements","import","instanceof","int","interface","long","native",
        "new","null","package","private","protected","public","return","short","static",
        "strictfp","super","switch","synchronized","this","throw","throws","transient","try",
        "void","volatile","while","var","yield","record","sealed","permits","non-sealed",
        "true","false"));
    private static final Set<String> JS_KEYWORDS = new LinkedHashSet<>(Arrays.asList(
        "if","else","for","while","do","switch","case","break","continue","return","function",
        "class","extends","super","new","this","throw","try","catch","finally","typeof",
        "instanceof","in","of","let","const","var","null","undefined","true","false","void",
        "delete","yield","async","await","import","export","default","from","as"));

    private static String jsString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
    private static String escapeCs(String s)   { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String escapeFs(String s)   { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String escapeC(String s)    { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String escapeJava(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
