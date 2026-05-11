#!/usr/bin/env python3
"""
add_anchors.py — Adds //@ anchor: and //@ description: to Venus notebook cells
that are missing them.  Processes local-sures/, examples/, and tutorials/.

Rules:
  - MARKDOWN cells: skipped
  - PIPELINE cells: skip anchor (already have //@ pipeline:), add description if missing
  - CODE cells with existing //@ anchor:: add //@ description: only if missing
  - CODE cells without anchor: infer anchor + description from source, inject both
  - Anchor names are kebab-case, unique within the notebook
"""

import json, re, os, sys
from pathlib import Path

# ── Name derivation helpers ───────────────────────────────────────────────────

def to_kebab(text):
    """Convert any string to a clean kebab-case identifier."""
    text = re.sub(r'[<>/\\()\[\]{},;=]', ' ', text)
    text = re.sub(r'(?<=[a-z])(?=[A-Z])', '-', text)       # camelCase split
    text = re.sub(r'[^a-z0-9]+', '-', text.lower())
    text = text.strip('-')
    return text[:40] if text else 'cell'

def strip_annotations(src):
    """Remove existing //@ lines from source for analysis."""
    return '\n'.join(l for l in src.split('\n') if not re.match(r'\s*//@', l))

def infer_anchor_and_desc(source, mode, idx):
    """Return (anchor_name, description) inferred from cell source."""
    clean = strip_annotations(source).strip()
    lines = clean.split('\n')

    # ── 1. Dash-style header comment: // --- Doing Something ---
    for line in lines[:6]:
        m = re.match(r'//+\s*[-—=*]+\s*(.+?)\s*[-—=*]+\s*$', line.strip())
        if m and len(m.group(1)) > 2:
            desc_text = m.group(1).strip()
            return to_kebab(desc_text), desc_text

    # ── 2. First plain comment line that isn't very short
    for line in lines[:4]:
        m = re.match(r'//+\s+(.{8,})', line.strip())
        if m:
            raw = m.group(1).strip()
            if not raw.startswith('@') and not raw.startswith('import'):
                return to_kebab(raw[:40]), raw[:80]

    # ── 3. Function / method definition (all languages)
    func_pat = re.search(
        r'(?:public\s+|private\s+|protected\s+|static\s+|async\s+|inline\s+)?'
        r'(?:void|int|String|double|float|bool|boolean|auto|List|Map|var|const|let|function)\s+'
        r'(\w{2,})\s*\(',
        clean)
    if func_pat:
        name = func_pat.group(1)
        if name not in ('if','for','while','switch','catch','new','return','console','System','Math'):
            return to_kebab(name), f'Defines the {name} function'

    # ── 4. Class / struct / record / enum
    type_pat = re.search(r'\b(?:class|struct|record|enum(?:\s+class)?)\s+(\w+)', clean)
    if type_pat:
        name = type_pat.group(1)
        return to_kebab(name), f'Defines {name} type'

    # ── 5. First meaningful variable / const assignment
    var_pat = re.search(
        r'(?:var|int|long|double|float|bool|String|List|Map|auto|val|let|const)\s+'
        r'(\w{2,})\s*=',
        clean)
    if var_pat:
        name = var_pat.group(1)
        if len(name) > 2:
            desc = f'Declares {name} variable'
            return to_kebab(name), desc

    # ── 6. JS require / import
    req = re.search(r'require\s*\(\s*[\'"]([^\'"]+)[\'"]\s*\)', clean)
    if req:
        pkg = req.group(1).split('/')[-1]
        return f'import-{to_kebab(pkg)}', f'Imports {req.group(1)} package'

    # ── 7. cout / console.log / print patterns → infer intent from nearby text
    if re.search(r'cout\s*<<|printf\s*\(|System\.out\.print', clean):
        snippet = re.search(r'["\']([A-Za-z][^"\']{4,30})["\']', clean)
        if snippet:
            return to_kebab(snippet.group(1)[:30]), f'Prints {snippet.group(1)[:60]}'

    # ── 8. Notebook-name prefix + index
    return f'step-{idx+1:02d}', f'Step {idx+1}'

# ── Annotation injection ──────────────────────────────────────────────────────

def parse_annotation(source, key):
    """Extract value of //@ key: ... from source, or None."""
    m = re.search(rf'^//@ *{re.escape(key)}: *(.+)$', source, re.MULTILINE)
    return m.group(1).strip() if m else None

def set_annotation(source, key, value):
    """Insert or replace //@ key: value in source.
    Keeps it near the top, after any existing //@ lines."""
    pattern = rf'^//@ *{re.escape(key)}: *.*$'
    # Replace if exists
    if re.search(pattern, source, re.MULTILINE):
        return re.sub(pattern, f'//@ {key}: {value}', source, flags=re.MULTILINE)
    # Insert: find last existing //@ line, insert after it; else prepend
    ann_lines = [i for i, l in enumerate(source.split('\n')) if re.match(r'//@ *\w', l)]
    lines = source.split('\n')
    insert_at = (ann_lines[-1] + 1) if ann_lines else 0
    lines.insert(insert_at, f'//@ {key}: {value}')
    return '\n'.join(lines)

# ── Per-notebook processing ───────────────────────────────────────────────────

def process_notebook(path):
    with open(path, encoding='utf-8') as f:
        nb = json.load(f)

    changed = False
    used_anchors = set()
    code_idx = 0  # counter for CODE/PIPELINE cells only

    for cell in nb.get('cells', []):
        ctype = cell.get('type', 'CODE')

        if ctype == 'MARKDOWN':
            continue

        source = cell.get('source', '')

        existing_anchor = parse_annotation(source, 'anchor')
        existing_pipeline = parse_annotation(source, 'pipeline')
        existing_desc = parse_annotation(source, 'description')

        is_pipeline = ctype == 'PIPELINE' or existing_pipeline is not None

        # --- Ensure anchor (CODE cells only, not PIPELINE) ---
        if not is_pipeline and not existing_anchor:
            anchor_raw, desc_raw = infer_anchor_and_desc(source, cell.get('mode','jshell'), code_idx)

            # Deduplicate within this notebook
            anchor = anchor_raw
            suffix = 2
            while anchor in used_anchors:
                anchor = f'{anchor_raw}-{suffix}'
                suffix += 1
            used_anchors.add(anchor)

            source = set_annotation(source, 'anchor', anchor)
            cell['anchor'] = anchor
            existing_anchor = anchor

            if not existing_desc:
                source = set_annotation(source, 'description', desc_raw)
                cell['description'] = desc_raw

            cell['source'] = source
            changed = True

        elif existing_anchor:
            used_anchors.add(existing_anchor)
            # Add description if missing
            if not existing_desc:
                _, desc_raw = infer_anchor_and_desc(source, cell.get('mode','jshell'), code_idx)
                source = set_annotation(source, 'description', desc_raw)
                cell['description'] = desc_raw
                cell['source'] = source
                changed = True

        # --- Ensure description on PIPELINE cells ---
        if is_pipeline and not existing_desc:
            name = existing_pipeline or cell.get('anchor') or f'pipeline-{code_idx+1}'
            desc_raw = f'Runs the {name} pipeline'
            source = set_annotation(source, 'description', desc_raw)
            cell['source'] = source
            changed = True

        code_idx += 1

    if changed:
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(nb, f, indent=2, ensure_ascii=False)
        print(f'  + Updated: {path.name}')
    else:
        print(f'  . No changes: {path.name}')

    return changed

# ── Main ─────────────────────────────────────────────────────────────────────

def main():
    base = Path(__file__).parent.parent / 'notebooks'
    folders = [
        base / 'local-sures',
        base / 'examples',
        base / 'tutorials',
    ]

    total_files = 0
    total_changed = 0

    for folder in folders:
        if not folder.exists():
            print(f'Skipping (not found): {folder}')
            continue
        print(f'\n[{folder.name}/]')
        for nb_path in sorted(folder.glob('*.vnb')):
            total_files += 1
            if process_notebook(nb_path):
                total_changed += 1


    print(f'\nDone — {total_changed}/{total_files} notebooks updated.')

if __name__ == '__main__':
    main()
