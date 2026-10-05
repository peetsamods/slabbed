#!/usr/bin/env python3
"""Mixin target scan: do the classes and members this source tree's mixins name exist on a game jar?

A mixin names its targets as strings: the class in `@Mixin(X.class)` or `@Mixin(targets = "...")`,
the method in `method = "name"` or `method = "name(desc)desc"`, and the instruction in
`target = "Lowner;name(desc)ret"` or `"Lowner;field:Ldesc;"`. None of those are constant-pool
references, so the linkage scan cannot see them and the JVM only complains when the mixin is applied
(with `defaultRequire: 1` that is a crash before the title screen). This scan resolves every such
string against a game jar's classes and member tables, under the names the game ships with
(Minecraft 26.1 and newer; for intermediary-remapped versions use the refmap check in
linkage-scan.py instead).

Usage:
    python3 tools/mixin-target-scan.py <game-jar> <source-dir> [<source-dir> ...] [--config <mixins.json> ...]
                                       [--exclude <glob> ...] [--skip MixinSimpleName ...]

  --config:  a mixin config; when given, only mixins registered in one of the configs are checked
             (an unregistered mixin class is inert and may deliberately name a target that is gone).
  --exclude: a path glob (relative to a source dir) or file-name glob to leave out, for sources the
             build itself excludes.

  --skip: a mixin class (simple name) that is deliberately withheld on this game version by the
          mixin config plugin and therefore need not resolve here; each must be justified in the
          plugin's own documentation.

Exit code 0 only when every target of every non-skipped mixin resolves. Method names without a
descriptor resolve when any method of that name exists on the owner or a supertype.
"""
import os, re, sys, zipfile, struct, fnmatch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from importlib.machinery import SourceFileLoader
linkage = SourceFileLoader('linkage_scan', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'linkage-scan.py')).load_module()

def load_game(jar):
    sup = {}; members = {}
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            if not n.endswith('.class'): continue
            try: this, s, ifs, _, mem = linkage.parse_class(z.read(n))
            except Exception: continue
            sup[this] = ([s] if s else []) + ifs
            for kind, name, desc in mem:
                members.setdefault(this, set()).add((kind, name, desc))
    return sup, members

def has_member(sup, members, owner, name, desc, kind):
    seen = set(); stack = [owner]
    while stack:
        c = stack.pop()
        if c in seen: continue
        seen.add(c)
        for k, n, d in members.get(c, ()):
            if n == name and (kind is None or k == kind) and (desc is None or d == desc):
                return True
        stack.extend(sup.get(c, []))
    return False

IMPORT_RE = re.compile(r'^import\s+([\w.]+)\s*;', re.M)
MIXIN_RE = re.compile(r'@Mixin\s*\(\s*(?:value\s*=\s*)?(\{[^}]*\}|[\w.]+\.class|targets\s*=\s*(?:\{[^}]*\}|"[^"]+"))', re.S)
METHOD_RE = re.compile(r'\bmethod\s*=\s*(\{[^}]*\}|"(?:[^"\\]|\\.)*"(?:\s*\+\s*"(?:[^"\\]|\\.)*")*)', re.S)
TARGET_RE = re.compile(r'\btarget\s*=\s*("(?:[^"\\]|\\.)*"(?:\s*\+\s*"(?:[^"\\]|\\.)*")*)', re.S)
ACCESSOR_RE = re.compile(r'@(Accessor|Invoker)\s*\(\s*(?:value\s*=\s*)?"([^"]+)"')
STRINGS_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')

def joined(expr):
    return ''.join(STRINGS_RE.findall(expr))

def array_elements(expr):
    """Elements of a `{ "a" + "b", "c" }` annotation array, each with its string pieces joined."""
    out = []; cur = []; depth = 0; i = 0
    body = expr.strip()[1:-1]
    for tok in re.finditer(r'"(?:[^"\\]|\\.)*"|,|[^",]+', body):
        t = tok.group(0)
        if t == ',':
            if cur: out.append(''.join(cur)); cur = []
        elif t.startswith('"'):
            cur.append(t[1:-1])
    if cur: out.append(''.join(cur))
    return out

def resolve_class(simple, imports, pkg_hint='net.minecraft'):
    if '.' in simple: return simple.replace('.', '/')
    for imp in imports:
        if imp.endswith('.' + simple): return imp.replace('.', '/')
        if imp.split('.')[-1] == simple.split('.')[0]: return (imp + simple[len(simple.split('.')[0]):]).replace('.', '/')
    return None

def main():
    args = sys.argv[1:]
    skip = set(); excludes = []
    while '--skip' in args:
        i = args.index('--skip'); skip.add(args[i+1]); del args[i:i+2]
    while '--exclude' in args:
        i = args.index('--exclude'); excludes.append(args[i+1]); del args[i:i+2]
    registered = None
    while '--config' in args:
        import json
        i = args.index('--config'); cfg = json.load(open(args[i+1])); del args[i:i+2]
        registered = registered or set()
        pkg = cfg.get('package', '')
        for key in ('mixins', 'client', 'server'):
            for name in cfg.get(key, []): registered.add(pkg + '.' + name)
    game_jar, dirs = args[0], args[1:]
    sup, members = load_game(game_jar)
    problems = []; checked = 0; skipped = []
    for d in dirs:
        for root, _, files in os.walk(d):
            for f in files:
                if not f.endswith('.java'): continue
                path = os.path.join(root, f)
                rel = os.path.relpath(path, d).replace(os.sep, '/')
                if any(fnmatch.fnmatch(rel, e) or fnmatch.fnmatch(f, e) for e in excludes): continue
                src = open(path, encoding='utf-8').read()
                src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
                src = re.sub(r'//[^\n]*', '', src)
                m = MIXIN_RE.search(src)
                if not m: continue
                if registered is not None:
                    pm = re.search(r'^package\s+([\w.]+)\s*;', src, re.M)
                    fq = (pm.group(1) + '.' if pm else '') + f[:-5]
                    if fq not in registered: continue
                if f[:-5] in skip:
                    skipped.append(f[:-5]); continue
                imports = IMPORT_RE.findall(src)
                spec = m.group(1)
                owners = []
                if spec.startswith('targets'):
                    owners = [s.replace('.', '/') for s in STRINGS_RE.findall(spec)]
                else:
                    for cls in re.findall(r'([\w.]+)\.class', spec):
                        r = resolve_class(cls, imports)
                        owners.append(r if r else cls.replace('.', '/'))
                for o in owners:
                    checked += 1
                    if o not in sup and o.startswith('net/minecraft/'):
                        problems.append(f'{f}: target class {o}')
                mc_owners = [o for o in owners if o.startswith('net/minecraft/') and o in sup]
                for mm in METHOD_RE.finditer(src):
                    for name in array_elements(mm.group(1)) if mm.group(1).startswith('{') else [joined(mm.group(1))]:
                        name = name.strip()
                        if not name or name.startswith('<') and name not in ('<init>', '<clinit>'): continue
                        if name.startswith('L') and ';' in name:  # fully-qualified target form
                            owner, rest = name[1:].split(';', 1); name = rest
                            own = [owner]
                        else:
                            own = mc_owners
                        desc = None
                        if '(' in name:
                            name, desc = name[:name.index('(')], name[name.index('('):]
                        if name in ('<init>', '<clinit>'): desc = desc  # keep
                        if not own: continue
                        checked += 1
                        if not any(has_member(sup, members, o, name, desc, 'method') for o in own):
                            problems.append(f'{f}: method {name}{desc or ""} on {"/".join(x.rsplit("/",1)[-1] for x in own)}')
                for tm in TARGET_RE.finditer(src):
                    t = joined(tm.group(1)).strip()
                    r = re.match(r'^L([^;]+);([^(:]+)(\(.*)?$', t)
                    if not r: continue
                    owner, name, desc = r.group(1), r.group(2), r.group(3)
                    if not owner.startswith('net/minecraft/'): continue
                    checked += 1
                    if owner not in sup:
                        problems.append(f'{f}: target owner {owner}'); continue
                    if ':' in name:
                        fname, fdesc = name.split(':', 1)
                        ok = has_member(sup, members, owner, fname, fdesc if fdesc else None, 'field')
                        if not ok: problems.append(f'{f}: target field {owner}.{name}')
                        continue
                    if desc and desc.endswith(';') and ':' in desc:  # field form Lowner;name:Ldesc;
                        pass
                    if not has_member(sup, members, owner, name, desc, 'method'):
                        problems.append(f'{f}: target {owner}.{name}{desc or ""}')
                for am in ACCESSOR_RE.finditer(src):
                    name = am.group(2); checked += 1
                    kind = 'field' if am.group(1) == 'Accessor' else 'method'
                    if not any(has_member(sup, members, o, name, None, kind) for o in mc_owners):
                        problems.append(f'{f}: @{am.group(1)} {name} on {"/".join(x.rsplit("/",1)[-1] for x in mc_owners)}')
    print(f'checked {checked} mixin targets against {game_jar}')
    for p in sorted(set(problems)): print('UNRESOLVED', p)
    if skipped: print('skipped (withheld on this version):', ', '.join(sorted(skipped)))
    print('unresolved:', len(set(problems)))
    sys.exit(1 if problems else 0)

if __name__ == '__main__':
    main()
