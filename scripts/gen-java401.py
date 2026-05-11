"""Generate java-401.vnb — Pipelines, Modules & MCP tutorial notebook."""
import json, os

base = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'notebooks')

def cell(cid, ctype, source, anchor=None, depends_on=None, pipeline_steps=None):
    return {
        "id": cid, "type": ctype, "mode": "jshell",
        "source": source, "output": "", "returnValue": None,
        "executed": False, "executionCount": None, "error": "",
        "anchor": anchor,
        "dependsOn": depends_on or [],
        "pipelineSteps": pipeline_steps or []
    }

def md(cid, src):   return cell(cid, "MARKDOWN", src)
def code(cid, src, anchor=None, deps=None): return cell(cid, "CODE", src, anchor, deps)
def pipe(cid, src, steps): return cell(cid, "PIPELINE", src, anchor=None, pipeline_steps=steps)

NB = {
    "id": "java-401",
    "name": "Java 401 \u2014 Pipelines, Modules & MCP",
    "description": "Cross-notebook modules, namespace management, pipeline orchestration, and MCP server integration",
    "created": "2026-03-14T10:00:00",
    "modified": "2026-03-14T00:00:00",
    "cells": [

# ── Intro ──────────────────────────────────────────────────────────────────
md("j401-intro", """\
# Java 401 \u2014 Pipelines, Modules & MCP

This notebook covers Venus Notebooks' **advanced orchestration system**:

| Feature | Description |
|---------|-------------|
| **Cell Anchors** | Name cells for reuse: `//@ anchor: name` |
| **Dependencies** | Declare execution order: `//@ depends: a, b` |
| **Namespaces** | Package cell code like Java: `//@ namespace: com.venus.utils` |
| **Cross-notebook** | Import from any notebook: `//@ depends: notebook:java-101/methods` |
| **Pipelines** | Chain steps into a workflow: `//@ steps: a, b, c` |
| **MCP Server** | Expose Venus as an AI agent tool via `/api/mcp/sse` |

> **Prerequisites:** Java 202 \u00b7 **Level:** Advanced \u00b7 **Time:** ~40 min"""),

# ── Section 1: Anchors ─────────────────────────────────────────────────────
md("j401-h2-anchors", """\
## 1. Cell Anchors \u2014 Naming Your Cells

Add `//@ anchor: name` as the first line of any code cell to give it a **stable, semantic name**.
Anchors make cells reusable and referenceable by other cells and pipelines.

```
//@ anchor: math-utils
//@ description: Core math functions
```

The buttons **\u25b6 With Deps** and **Run Pipeline** use these names to build the execution graph."""),

code("j401-imports", """\
//@ anchor: imports
//@ description: Common imports for the 401 notebook
import java.util.*;
import java.util.stream.*;
import java.util.function.*;
import java.time.*;
System.out.println("[imports] Ready");""", anchor="imports"),

code("j401-math-utils", """\
//@ anchor: math-utils
//@ namespace: venus.utils.math
//@ depends: imports
//@ description: Reusable math utilities \u2014 prime checking, statistics, fibonacci

boolean isPrime(int n) {
    if (n < 2) return false;
    for (int i = 2; i * i <= n; i++) if (n % i == 0) return false;
    return true;
}

List<Integer> primesUpTo(int max) {
    return IntStream.rangeClosed(2, max).filter(n -> isPrime(n))
        .boxed().collect(Collectors.toList());
}

double mean(List<Integer> nums) {
    return nums.stream().mapToInt(Integer::intValue).average().orElse(0);
}

double stdDev(List<Integer> nums) {
    double m = mean(nums);
    return Math.sqrt(nums.stream().mapToDouble(n -> (n-m)*(n-m)).average().orElse(0));
}

long fib(int n) {
    if (n <= 1) return n;
    long a = 0, b = 1;
    for (int i = 2; i <= n; i++) { long t = a+b; a = b; b = t; }
    return b;
}

System.out.println("[math-utils] isPrime(17)=" + isPrime(17) + " mean(1..5)=" + mean(List.of(1,2,3,4,5)));""",
anchor="math-utils", deps=["imports"]),

code("j401-string-utils", """\
//@ anchor: string-utils
//@ namespace: venus.utils.strings
//@ depends: imports
//@ description: String manipulation utilities

String camelToSnake(String s) {
    return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
}

String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
}

List<String> tokenize(String text) {
    return Arrays.stream(text.split("[\\\\s,;.!?]+"))
        .filter(w -> !w.isBlank()).map(String::toLowerCase)
        .collect(Collectors.toList());
}

Map<String, Long> wordFrequency(String text) {
    return tokenize(text).stream()
        .collect(Collectors.groupingBy(w -> w, Collectors.counting()));
}

System.out.println("[string-utils] camelToSnake='" + camelToSnake("myVariableName") + "'");""",
anchor="string-utils", deps=["imports"]),

# ── Section 2: Dependency graph ────────────────────────────────────────────
md("j401-h2-deps", """\
## 2. Dependency Chains \u2014 Declaring Execution Order

`//@ depends: a, b, c` tells Venus to run cells `a`, `b`, and `c` (and **their** dependencies)
**before** running this cell. Click **\u25b6 With Deps** on the cell below \u2014 it will automatically
run `imports`, `math-utils`, and `string-utils` first.

Venus uses **Kahn's topological sort** to find the right execution order, and detects circular
dependencies before starting."""),

code("j401-analysis", """\
//@ anchor: analysis
//@ depends: math-utils, string-utils
//@ description: Combined analysis using both utility modules

// Use math-utils functions
var primes = primesUpTo(50);
System.out.println("Primes up to 50: " + primes);
System.out.printf("  count=%d  mean=%.1f  stdDev=%.2f%n",
    primes.size(), mean(primes), stdDev(primes));

// Use string-utils functions
var text = "The quick brown fox jumps over the lazy dog";
var freq = wordFrequency(text);
System.out.println("\\nWord frequencies (top 5):");
freq.entrySet().stream()
    .sorted(Map.Entry.<String,Long>comparingByValue().reversed())
    .limit(5)
    .forEach(e -> System.out.printf("  %-10s %d%n", e.getKey(), e.getValue()));

// Fibonacci sequence
System.out.print("\\nFibonacci(0..10): ");
IntStream.rangeClosed(0, 10).forEach(i -> System.out.print(fib(i) + " "));
System.out.println();""",
anchor="analysis", deps=["math-utils", "string-utils"]),

# ── Section 3: Namespace classes ───────────────────────────────────────────
md("j401-h2-namespace", """\
## 3. Namespaces \u2014 Avoiding Name Conflicts

When two notebooks both define `isPrime()`, you have a conflict. Venus solves this using the
**namespace class pattern** \u2014 identical to how Java packages work.

The `//@ namespace: com.example.utils` annotation signals that when this cell is **imported into
another notebook**, its code will be wrapped in a class named `VNS_com_example_utils`.

```java
//@ namespace: com.example.utils   <-- declares the namespace
boolean isPrime(int n) { ... }     <-- becomes a static method in the wrapper class
```

When imported, callers use: `VNS_com_example_utils.isPrime(17)`

This notebook defines two utility namespaces already:
- `venus.utils.math` \u2192 wraps to `VNS_venus_utils_math`
- `venus.utils.strings` \u2192 wraps to `VNS_venus_utils_strings`"""),

code("j401-namespace-demo", """\
//@ anchor: namespace-demo
//@ depends: imports
//@ description: Demonstrate namespace class pattern for safe cross-notebook reuse

// Define a namespace class manually (what Venus generates automatically when wrapping)
class VNS_venus_utils_math {
    static boolean isPrime(int n) {
        if (n < 2) return false;
        for (int i = 2; i * i <= n; i++) if (n % i == 0) return false;
        return true;
    }
    static List<Integer> primesUpTo(int max) {
        return IntStream.rangeClosed(2, max)
            .filter(n -> isPrime(n)).boxed().collect(Collectors.toList());
    }
    static double mean(List<Integer> nums) {
        return nums.stream().mapToInt(Integer::intValue).average().orElse(0);
    }
}

class VNS_venus_utils_strings {
    static String camelToSnake(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
    static List<String> tokenize(String text) {
        return Arrays.stream(text.split("[\\\\s,;.!?]+"))
            .filter(w -> !w.isBlank()).map(String::toLowerCase)
            .collect(Collectors.toList());
    }
}

// Use them via namespace \u2014 no ambiguity even if another notebook defines the same names
var primes = VNS_venus_utils_math.primesUpTo(30);
System.out.println("Primes via namespace class: " + primes);
System.out.println("mean = " + VNS_venus_utils_math.mean(primes));
System.out.println("camelToSnake: " + VNS_venus_utils_strings.camelToSnake("myFunctionName"));""",
anchor="namespace-demo", deps=["imports"]),

# ── Section 4: Cross-notebook references ───────────────────────────────────
md("j401-h2-crossref", """\
## 4. Cross-Notebook References

Reference a cell from **any other notebook** using:
```
//@ depends: notebook:NOTEBOOK-ID/ANCHOR-NAME
```

When Venus executes this cell:
1. Loads the referenced notebook from disk
2. Finds the cell with the matching anchor
3. Recursively resolves **that cell's** dependencies (within its notebook)
4. Executes the dependency chain in the current session
5. Then executes this cell

This means functions defined in `java-101`, `java-202`, or any utility notebook are instantly
available in your current notebook \u2014 without copy-pasting."""),

code("j401-cross-ref-demo", """\
//@ anchor: cross-ref-demo
//@ depends: notebook:java-101/j101-methods-jshell
//@ description: Import the prime/math functions from java-101 into this session

// After the cross-notebook dependency runs, the java-101 methods are in scope:
// isPrime(), circleArea(), repeat() are now available from java-101/j101-methods-jshell

System.out.println("Imported from java-101:");
System.out.println("isPrime(13) = " + isPrime(13));
System.out.printf("circleArea(3.0) = %.4f%n", circleArea(3.0));
System.out.println("repeat('ha', 4) = " + repeat("ha", 4));""",
anchor="cross-ref-demo", deps=["notebook:java-101/j101-methods-jshell"]),

# ── Section 5: Pipeline orchestration ─────────────────────────────────────
md("j401-h2-pipeline", """\
## 5. Pipeline Orchestration \u2014 Chaining Steps

A **PIPELINE cell** defines an ordered workflow of named steps. It's like an Ant build target
that chains other targets. Click **Run Pipeline** on the PIPELINE cell below.

```
//@ steps: step-a, step-b, step-c
```

Venus:
1. Resolves each step's full transitive dependency closure
2. Merges and deduplicates the graph
3. Topologically sorts everything
4. Executes once in the correct order \u2014 no duplicate runs

**Use case:** ETL pipeline, analysis workflow, test suite."""),

code("j401-data-load", """\
//@ anchor: data-load
//@ depends: imports
//@ description: Load and validate raw transaction data

record Transaction(String id, String category, double amount, LocalDate date) {}

var rawData = List.of(
    new Transaction("T001", "Food",      12.50, LocalDate.of(2026, 1, 5)),
    new Transaction("T002", "Transport",  8.00, LocalDate.of(2026, 1, 6)),
    new Transaction("T003", "Food",      45.00, LocalDate.of(2026, 1, 7)),
    new Transaction("T004", "Utilities", 89.50, LocalDate.of(2026, 1, 8)),
    new Transaction("T005", "Food",      22.30, LocalDate.of(2026, 1, 9)),
    new Transaction("T006", "Transport", 15.00, LocalDate.of(2026, 1,10)),
    new Transaction("T007", "Shopping", 130.00, LocalDate.of(2026, 1,11)),
    new Transaction("T008", "Utilities",  45.0, LocalDate.of(2026, 1,12))
);

System.out.println("[data-load] Loaded " + rawData.size() + " transactions");
rawData.forEach(t -> System.out.printf("  %s %-12s $%6.2f  %s%n",
    t.id(), t.category(), t.amount(), t.date()));""",
anchor="data-load", deps=["imports"]),

code("j401-data-transform", """\
//@ anchor: data-transform
//@ depends: data-load
//@ description: Enrich and transform transaction data

record EnrichedTx(Transaction tx, String quarter, boolean isLargeExpense) {}

var enriched = rawData.stream().map(t -> new EnrichedTx(
    t,
    "Q" + ((t.date().getMonthValue() - 1) / 3 + 1),
    t.amount() > 50.0
)).collect(Collectors.toList());

System.out.println("[data-transform] Enriched " + enriched.size() + " transactions");
enriched.stream().filter(e -> e.isLargeExpense())
    .forEach(e -> System.out.printf("  Large: %s $%.2f (%s)%n",
        e.tx().category(), e.tx().amount(), e.quarter()));""",
anchor="data-transform", deps=["data-load"]),

code("j401-data-aggregate", """\
//@ anchor: data-aggregate
//@ depends: data-transform
//@ description: Aggregate enriched data by category

var byCategory = enriched.stream().collect(
    Collectors.groupingBy(
        e -> e.tx().category(),
        Collectors.summarizingDouble(e -> e.tx().amount())
    )
);

System.out.println("[data-aggregate] Spending by category:");
byCategory.entrySet().stream()
    .sorted(Map.Entry.<String, DoubleSummaryStatistics>comparingByValue(
        Comparator.comparingDouble(DoubleSummaryStatistics::getSum)).reversed())
    .forEach(e -> System.out.printf("  %-12s count=%-2d total=$%.2f  avg=$%.2f%n",
        e.getKey(), (int)e.getValue().getCount(),
        e.getValue().getSum(), e.getValue().getAverage()));""",
anchor="data-aggregate", deps=["data-transform"]),

code("j401-data-report", """\
//@ anchor: data-report
//@ depends: data-aggregate, math-utils
//@ description: Generate a final summary report

double totalSpend = byCategory.values().stream()
    .mapToDouble(DoubleSummaryStatistics::getSum).sum();

System.out.println("\\n=== MONTHLY SPEND REPORT ===");
System.out.printf("Total transactions : %d%n", rawData.size());
System.out.printf("Total spend        : $%.2f%n", totalSpend);
System.out.printf("Average per tx     : $%.2f%n", totalSpend / rawData.size());
System.out.println("\\nTop category: " + byCategory.entrySet().stream()
    .max(Comparator.comparingDouble(e -> e.getValue().getSum()))
    .map(e -> e.getKey() + " ($" + String.format("%.2f", e.getValue().getSum()) + ")")
    .orElse("none"));

// Use cross-referenced math-utils
var amounts = rawData.stream().map(t -> (int)(t.amount() * 100))
    .collect(Collectors.toList());
System.out.printf("Amount std-dev     : $%.2f%n", stdDev(amounts) / 100.0);""",
anchor="data-report", deps=["data-aggregate", "math-utils"]),

# Pipeline cell
pipe("j401-etl-pipeline",
"""\
//@ steps: data-load, data-transform, data-aggregate, data-report
//@ description: Full ETL pipeline: load -> transform -> aggregate -> report

// Click "Run Pipeline" to execute all steps in dependency order.
// Venus will resolve the full graph, deduplicate, toposort, and run once each.""",
steps=["data-load", "data-transform", "data-aggregate", "data-report"]),

# ── Section 6: MCP Server ──────────────────────────────────────────────────
md("j401-h2-mcp", """\
## 6. MCP Server \u2014 AI Agent Integration

Venus exposes a **Model Context Protocol (MCP) server** at `/api/mcp/sse` so AI agents
(like Claude Desktop) can use Venus as a tool.

### Setup in Claude Desktop (`claude_desktop_config.json`):
```json
{
  "mcpServers": {
    "venus-notebooks": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-http"],
      "env": {
        "SERVER_URL": "http://localhost:8585/api/mcp/sse"
      }
    }
  }
}
```

Or use a direct HTTP MCP client pointing to `http://localhost:8585/api/mcp/sse`.

### Available MCP Tools:

| Tool | Description |
|------|-------------|
| `venus_execute_code` | Run Java/JShell code, get output back |
| `venus_list_notebooks` | List all notebooks |
| `venus_read_notebook` | Read all cells from a notebook |
| `venus_run_pipeline` | Execute a pipeline cell |
| `venus_search_cells` | Search cells by anchor or content |
| `venus_load_module` | Load a named module into a session |

### Example agent conversation:
> **Agent:** "Analyze the spending data in java-401 and tell me the top expense category"
> **Agent calls:** `venus_run_pipeline(notebookId="java-401", cellId="j401-etl-pipeline")`
> **Venus returns:** The full pipeline output
> **Agent:** "The top category is Shopping ($130.00)"
"""),

code("j401-mcp-demo", """\
//@ anchor: mcp-demo
//@ depends: imports
//@ description: Demonstrate MCP server connectivity via HTTP

// Test the MCP server's HTTP endpoint directly from JShell
import java.net.http.*;
import java.net.URI;

var client = HttpClient.newHttpClient();

// List available tools (JSON-RPC 2.0 request)
var listToolsBody = "{\\"jsonrpc\\":\\"2.0\\",\\"id\\":1,\\"method\\":\\"tools/list\\",\\"params\\":{}}";

var request = HttpRequest.newBuilder()
    .uri(URI.create("http://localhost:8585/api/mcp/messages"))
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(listToolsBody))
    .build();

var response = client.send(request, HttpResponse.BodyHandlers.ofString());
System.out.println("MCP tools/list response (status " + response.statusCode() + "):");

// Pretty-print just the tool names from the response
var body = response.body();
// Extract tool names with simple string scanning
int idx = 0;
while ((idx = body.indexOf("\\"name\\":", idx)) != -1) {
    int start = body.indexOf("\\"", idx + 7) + 1;
    int end = body.indexOf("\\"", start);
    if (start > 0 && end > start) {
        String name = body.substring(start, end);
        if (name.startsWith("venus_")) System.out.println("  Tool: " + name);
    }
    idx = end + 1;
}""",
anchor="mcp-demo", deps=["imports"]),

code("j401-mcp-execute", """\
//@ anchor: mcp-execute
//@ depends: mcp-demo
//@ description: Execute code via MCP tool call

import java.net.http.*;
import java.net.URI;

var client = HttpClient.newHttpClient();

// Call venus_execute_code tool (JSON-RPC 2.0)
var toolCallBody = "{\\"jsonrpc\\":\\"2.0\\",\\"id\\":2,\\"method\\":\\"tools/call\\"," +
    "\\"params\\":{\\"name\\":\\"venus_execute_code\\"," +
    "\\"arguments\\":{\\"code\\":\\"var result = IntStream.rangeClosed(1,10).sum(); System.out.println(result);\\",\\"session\\":\\"mcp-agent-session\\"}}}";


var request = HttpRequest.newBuilder()
    .uri(URI.create("http://localhost:8585/api/mcp/messages"))
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(toolCallBody))
    .build();

var response = client.send(request, HttpResponse.BodyHandlers.ofString());
System.out.println("MCP tool call result:");
// Extract text content from response
var body = response.body();
int textIdx = body.indexOf("\\"text\\":");
if (textIdx >= 0) {
    int start = body.indexOf("\\"", textIdx + 7) + 1;
    int end = body.lastIndexOf("\\"");
    if (start > 0 && end > start)
        System.out.println("  Output: " + body.substring(start, end).replace("\\\\n", "\\n"));
}""",
anchor="mcp-execute", deps=["mcp-demo"]),

# ── Footer ────────────────────────────────────────────────────────────────
md("j401-footer", """\
---

## Summary

You've learned Venus Notebooks' full orchestration system:

| Concept | Syntax | Purpose |
|---------|--------|---------|
| **Anchor** | `//@ anchor: name` | Name a cell for reuse |
| **Depends** | `//@ depends: a, b` | Declare prerequisites |
| **Namespace** | `//@ namespace: com.x.y` | Package scope for exports |
| **Cross-notebook** | `//@ depends: notebook:id/anchor` | Import from any notebook |
| **Pipeline** | `//@ steps: a, b, c` | Ordered workflow |
| **MCP** | `GET /api/mcp/sse` | AI agent tool interface |

### Quick Reference Card:
```java
//@ anchor: my-utils          \u2190 names this cell
//@ namespace: com.mine.utils  \u2190 wraps as VNS_com_mine_utils when imported
//@ depends: imports, base     \u2190 runs these first (same notebook)
//@ depends: notebook:java-101/methods  \u2190 imports from another notebook
//@ steps: load, transform, report      \u2190 (PIPELINE cell only)
//@ description: What this cell does    \u2190 documentation
//@ on-error: continue                  \u2190 pipeline error handling (stop/continue)
```""")

    ],
    "metadata": {},
    "sessionId": None,
    "filename": "java-401.vnb"
}

path = os.path.join(base, 'java-401.vnb')
with open(path, 'w', encoding='utf-8') as f:
    json.dump(NB, f, indent=2, ensure_ascii=True)

with open(path, encoding='utf-8') as f:
    nb = json.load(f)
print('Wrote java-401.vnb \u2014', len(nb['cells']), 'cells')
print('Done.')
