"""Scan notebook cells for common missing imports."""
import json, os, sys

base = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'notebooks')
files = ['welcome.vnb', 'java-101.vnb', 'java-202.vnb', 'java-302.vnb']

# Classes that need explicit imports in JShell (not in auto-imported packages)
# JShell auto-imports: java.lang.*, java.util.*, java.io.*, java.math.*, java.net.*
needs_import = {
    'IntStream':    'java.util.stream.IntStream',
    'LongStream':   'java.util.stream.LongStream',
    'DoubleStream': 'java.util.stream.DoubleStream',
    'Stream':       'java.util.stream.Stream',
    'Collectors':   'java.util.stream.Collectors',
    'Collector':    'java.util.stream.Collector',
    'LocalDate':    'java.time.LocalDate',
    'LocalTime':    'java.time.LocalTime',
    'LocalDateTime':'java.time.LocalDateTime',
    'Duration':     'java.time.Duration',
    'Instant':      'java.time.Instant',
    'DateTimeFormatter': 'java.time.format.DateTimeFormatter',
}

for fname in files:
    path = os.path.join(base, fname)
    if not os.path.exists(path):
        continue
    with open(path, encoding='utf-8') as f:
        nb = json.load(f)
    for cell in nb['cells']:
        if cell.get('type') != 'CODE':
            continue
        src = cell['source']
        for cls, full in needs_import.items():
            pkg = full.rsplit('.', 1)[0]
            wildcard = pkg + '.*'
            already = ('import ' + full) in src or ('import ' + wildcard) in src
            if cls in src and not already:
                print('%s / %s: %s not imported (need: import %s;)' % (fname, cell['id'], cls, full))

print('Scan complete.')
