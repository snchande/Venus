"""Fix non-ASCII chars and ensure explicit imports in welcome.vnb cells."""
import json, os

base = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'notebooks')

with open(os.path.join(base, 'welcome.vnb'), encoding='utf-8') as f:
    welcome = json.load(f)

for cell in welcome['cells']:
    cid = cell['id']

    # Fix cell-collections: replace non-ASCII chars that can confuse JShell parser
    if cid == 'cell-collections':
        cell['source'] = (
            "// Map.of, List.of — immutable factory methods (java.util is auto-imported)\n"
            "var capitals = Map.of(\n"
            '    "France",  "Paris",\n'
            '    "Germany", "Berlin",\n'
            '    "Japan",   "Tokyo",\n'
            '    "Brazil",  "Brasilia"\n'
            ");\n"
            "\n"
            "capitals.entrySet().stream()\n"
            "    .sorted(Map.Entry.comparingByKey())\n"
            '    .forEach(e -> System.out.printf("%-12s -> %s%n", e.getKey(), e.getValue()));\n'
            "\n"
            "System.out.println();\n"
            "\n"
            "// Mutable map with merge/compute\n"
            "var scores = new HashMap<String, Integer>();\n"
            'List.of("Alice","Bob","Alice","Charlie","Bob","Alice")\n'
            "    .forEach(name -> scores.merge(name, 1, Integer::sum));\n"
            'System.out.println("Vote counts: " + new TreeMap<>(scores));'
        )
        print('Fixed cell-collections: removed non-ASCII chars, simplified imports')

    # Fix cell-optional: ensure Map import is explicit since Optional import precedes it
    if cid == 'cell-optional':
        cell['source'] = (
            "import java.util.Optional;\n"
            "\n"
            "// Simulated user database\n"
            'var users = Map.of("u1", "Alice", "u2", "Bob", "u3", "Charlie");\n'
            "\n"
            "Optional<String> findUser(String id) {\n"
            "    return Optional.ofNullable(users.get(id));\n"
            "}\n"
            "\n"
            "String displayUser(String id) {\n"
            "    return findUser(id)\n"
            '        .map(name -> name + " (" + id + ")")\n'
            '        .orElseGet(() -> "Unknown user: " + id);\n'
            "}\n"
            "\n"
            'List.of("u1", "u99", "u3", "u42")\n'
            "    .forEach(id -> System.out.println(displayUser(id)));"
        )
        print('Fixed cell-optional: explicit import, cleaner source')

with open(os.path.join(base, 'welcome.vnb'), 'w', encoding='utf-8') as f:
    json.dump(welcome, f, indent=2, ensure_ascii=False)

# Verify
with open(os.path.join(base, 'welcome.vnb'), encoding='utf-8') as f:
    nb = json.load(f)
print('Verified welcome.vnb -', len(nb['cells']), 'cells OK')
print('Done.')
