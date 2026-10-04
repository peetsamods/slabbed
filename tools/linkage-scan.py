#!/usr/bin/env python3
"""Linkage scan: does every Minecraft class/member a release jar references exist on a target version?

A ranged jar declares several Minecraft versions but is compiled against one. The JVM links
lazily, so a reference to a member that exists only on the build version crashes only when that
code runs; the GameTest suite cannot promise to run every path. This scan resolves every
CONSTANT_Class / Fieldref / Methodref / InterfaceMethodref whose owner is a net/minecraft class,
and every mixin refmap entry, against the target version's intermediary names and class
hierarchy, and fails on anything unresolved.

Usage:
    python3 tools/linkage-scan.py <release-jar> <intermediary-tiny|-> <official-game-jar> [--allow-class PREFIX ...]
                                  [--lib <jar> ...]

  intermediary-tiny: mappings/<version>.tiny from the FabricMC intermediary repository (v1), or Loom's
                     cached intermediary-v2.tiny for the version, or "-" for a
                     game version that ships under the names the mod was compiled against (Minecraft
                     26.1 and newer carry Mojang names in the shipped jar, so no mapping is needed)
  official-game-jar: the shipped client jar for the version (Loom keeps one under its cache)
  --lib:             a library jar whose classes are also in scope (identity names): references to
                     net/fabricmc/fabric/api/ owners are then resolved against it too, and a Fabric API
                     bundle's nested META-INF/jars/*.jar are read as well. Give the Fabric API build the
                     target version actually runs with, so an API seam (a renamed interface member)
                     fails here like a game seam does.
  --allow-class:     a class (internal name prefix) that is deliberately isolated and only ever
                     loaded on a version where its references resolve, e.g. a nested class that
                     holds the newest-version branch of a version-switched helper. Each such class
                     must be justified in RELEASE_ALLOWLIST.md.

Exit code 0 only when nothing outside the allowed classes is unresolved.
"""
import json, struct, sys, zipfile, re

def parse_class(data):
    """Return (this_name, super_name, interfaces, cp_refs) where cp_refs is a list of
    ('class', name) / ('field'|'method'|'imethod', owner, name, desc) / ('string', value)."""
    pos = 8
    count = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
    cp = [None]*count; i = 1
    while i < count:
        tag = data[pos]; pos += 1
        if tag == 1:
            ln = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
            cp[i] = ('utf8', data[pos:pos+ln].decode('utf-8', 'replace')); pos += ln
        elif tag in (3,4): cp[i] = ('num',); pos += 4
        elif tag in (5,6): cp[i] = ('num',); pos += 8; i += 1
        elif tag == 7: cp[i] = ('class', struct.unpack('>H', data[pos:pos+2])[0]); pos += 2
        elif tag == 8: cp[i] = ('string', struct.unpack('>H', data[pos:pos+2])[0]); pos += 2
        elif tag in (9,10,11):
            a,b = struct.unpack('>HH', data[pos:pos+4]); pos += 4
            cp[i] = ({9:'field',10:'method',11:'imethod'}[tag], a, b)
        elif tag == 12:
            a,b = struct.unpack('>HH', data[pos:pos+4]); pos += 4; cp[i] = ('nat', a, b)
        elif tag == 15: pos += 3; cp[i] = ('mh',)
        elif tag == 16: pos += 2; cp[i] = ('mt',)
        elif tag in (17,18): pos += 4; cp[i] = ('dyn',)
        elif tag in (19,20): pos += 2; cp[i] = ('mod',)
        else: raise ValueError('bad cp tag %d' % tag)
        i += 1
    def utf(idx): return cp[idx][1]
    def cls(idx): return utf(cp[idx][1])
    pos += 2  # access
    this_name = cls(struct.unpack('>H', data[pos:pos+2])[0]); pos += 2
    sup_idx = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
    super_name = cls(sup_idx) if sup_idx else None
    n = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
    ifaces = [cls(struct.unpack('>H', data[pos+2*k:pos+2*k+2])[0]) for k in range(n)]
    pos += 2*n
    members = []
    for table in (0, 1):
        cnt = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
        for _ in range(cnt):
            acc, ni, di, ac = struct.unpack('>HHHH', data[pos:pos+8]); pos += 8
            members.append(('field' if table == 0 else 'method', utf(ni), utf(di)))
            for _ in range(ac):
                an, al = struct.unpack('>HI', data[pos:pos+6]); pos += 6 + al
    refs = []
    for e in cp:
        if not e: continue
        if e[0] == 'class': refs.append(('class', utf(e[1])))
        elif e[0] in ('field','method','imethod'):
            owner = cls(e[1]); nat = cp[e[2]]; refs.append((e[0], owner, utf(nat[1]), utf(nat[2])))
        elif e[0] == 'string': refs.append(('string', utf(e[1])))
    return this_name, super_name, ifaces, refs, members

def load_tiny(path):
    cls_map = {}; methods = {}; fields = {}
    if path == '-':
        return cls_map, methods, fields, (lambda d: d)
    lines = open(path, encoding='utf-8').read().splitlines()
    if lines and lines[0].startswith('tiny\t2\t'):
        # Tiny v2 (Loom's cached intermediary-v2.tiny): class rows `c <official> <intermediary>`, then
        # indented member rows `m <desc> <official> <intermediary>` / `f <desc> <official> <intermediary>`
        # under their class. Normalised to the v1 row shape the rest of this loader reads.
        cols = lines[0].split('\t'); oi, ii = cols.index('official') - 3, cols.index('intermediary') - 3
        v1 = ['v1\tofficial\tintermediary']; cur = None
        for ln in lines[1:]:
            if not ln or ln.startswith('\t\t'): continue
            p = ln.split('\t')
            if p[0] == 'c':
                cur = p[1 + oi]; v1.append('CLASS\t' + p[1 + oi] + '\t' + p[1 + ii])
            elif p[0] == '' and len(p) > 2 and p[1] in ('m', 'f') and cur is not None:
                kind = 'METHOD' if p[1] == 'm' else 'FIELD'
                v1.append(kind + '\t' + cur + '\t' + p[2] + '\t' + p[3 + oi] + '\t' + p[3 + ii])
        lines = v1
    for ln in lines[1:]:
        p = ln.split('\t')
        if p[0] == 'CLASS': cls_map[p[1]] = p[2]
    def remap_desc(d):
        return re.sub(r'L([^;]+);', lambda m: 'L' + cls_map.get(m.group(1), m.group(1)) + ';', d)
    for ln in lines[1:]:
        p = ln.split('\t')
        if p[0] == 'METHOD':
            owner = cls_map.get(p[1], p[1]); methods.setdefault(owner, set()).add((p[4], remap_desc(p[2])))
        elif p[0] == 'FIELD':
            owner = cls_map.get(p[1], p[1]); fields.setdefault(owner, set()).add((p[4], remap_desc(p[2])))
    return cls_map, methods, fields, remap_desc

literal = {}
jdk_cache = {}
jdk_supers = {}
def jdk_members(cls):
    """(name, desc) pairs of a JDK class, read once through `javap -s -p` on the running JDK."""
    if cls in jdk_cache: return jdk_cache[cls]
    import subprocess
    members = set(); name = None
    try:
        out = subprocess.run(['javap', '-s', '-p', cls.replace('/', '.')], capture_output=True, text=True, timeout=60).stdout
    except Exception:
        out = ''
    for ln in out.splitlines():
        ln = ln.strip()
        if ln.startswith('descriptor:') and name is not None:
            members.add((name, ln.split(':', 1)[1].strip())); name = None
        elif ln.endswith(';') and not ln.startswith(('Compiled', 'public class', 'public interface', 'public final class',
                                                     'public abstract class', 'class ', 'interface ', 'final class', 'abstract class')):
            sig = ln[:-1].split(' throws ')[0]
            if '(' in sig:
                head = sig.split('(')[0].split()
                name = head[-1] if head else None
                if name and '<' in name and name.endswith('>'): name = None
                if name and name.endswith(cls.rsplit('/', 1)[-1].rsplit('$', 1)[-1]) and ' ' not in sig.split('(')[0].strip().replace(name, '', 1).strip():
                    name = '<init>'  # constructor: javap prints the simple class name
            else:
                name = sig.split()[-1]
    jdk_cache[cls] = members
    supers = []
    for ln in out.splitlines():
        if ln.startswith(('public ', 'final ', 'abstract ', 'class ', 'interface ')) and ('extends' in ln or 'implements' in ln):
            import re as _re
            for tok in _re.findall(r'[A-Za-z_$][\w$.]*', ln.split('{')[0]):
                if '.' in tok and tok != cls.replace('/', '.'):
                    supers.append(tok.split('<')[0].replace('.', '/'))
    jdk_supers[cls] = supers
    return members

def load_hierarchy(game_jar, cls_map, remap_desc, sup=None, nested=True):
    """official-named game jar -> {intermediary class: [intermediary supertypes]} and set of all classes."""
    import io
    sup = {} if sup is None else sup
    with zipfile.ZipFile(game_jar) as z:
        for n in z.namelist():
            if nested and n.startswith('META-INF/jars/') and n.endswith('.jar'):
                load_hierarchy(io.BytesIO(z.read(n)), cls_map, remap_desc, sup, nested=False)
                continue
            if not n.endswith('.class'): continue
            try: this, s, ifs, _, members = parse_class(z.read(n))
            except Exception: continue
            ti = cls_map.get(this, this)
            parents = [cls_map.get(x, x) for x in ([s] if s else []) + ifs]
            sup[ti] = parents
            for kind, name, desc in members:
                literal.setdefault((kind, ti), set()).add((name, remap_desc(desc)))
    return sup

def main():
    args = [a for a in sys.argv[1:]]
    allowed = []; libs = []
    while '--allow-class' in args:
        i = args.index('--allow-class'); allowed.append(args[i+1]); del args[i:i+2]
    while '--lib' in args:
        i = args.index('--lib'); libs.append(args[i+1]); del args[i:i+2]
    mod_jar, tiny, game_jar = args[:3]
    cls_map, methods, fields, remap_desc = load_tiny(tiny)
    sup = load_hierarchy(game_jar, cls_map, remap_desc)
    for lib in libs:
        load_hierarchy(lib, {}, lambda d: d, sup)
    all_classes = set(sup.keys()) | set(cls_map.values())
    # The game jar also carries Mojang's own client libraries (com/mojang/blaze3d, math, ...); every
    # package it ships under com/mojang/ is in scope too, so a renamed enum constant there fails here.
    mojang_pkgs = sorted({'/'.join(c.split('/')[:3]) + '/' for c in sup if c.startswith('com/mojang/') and c.count('/') >= 3})
    scope = ('net/minecraft/',) + tuple(mojang_pkgs) + (('net/fabricmc/fabric/api/',) if libs else ())
    def member_exists(owner, name, desc, kind):
        if 'net/fabricmc/' in desc: return True  # Fabric interface injection
        seen = set(); stack = [owner]
        while stack:
            c = stack.pop()
            if c in seen: continue
            seen.add(c)
            if c.startswith(('java/', 'javax/', 'jdk/')):
                if (name, desc) in jdk_members(c): return True
                continue  # a JDK supertype that lacks the member proves nothing; keep walking
            if not c.startswith(scope):
                if c in sup: pass  # a lib class we loaded: keep walking its members below
                else: return True  # third-party library supertype: out of scope
            table = fields if kind == 'field' else methods
            if (name, desc) in table.get(c, ()): return True
            if (name, desc) in literal.get((kind, c), ()): return True
            if kind != 'field' and name in ('ordinal','values','valueOf','name','compareTo','getClass','hashCode','equals','toString','clone'): return True
            if c in sup: stack.extend(sup[c])
            elif c.startswith(('java/', 'javax/', 'jdk/')):
                jdk_members(c)
                stack.extend(jdk_supers.get(c, []))
        return False
    problems = []
    checked = 0
    with zipfile.ZipFile(mod_jar) as z:
        names = z.namelist()
        for n in names:
            if not n.endswith('.class'): continue
            this, s, ifs, refs, _m = parse_class(z.read(n))
            for r in refs:
                if r[0] == 'class':
                    c = r[1].lstrip('[')
                    if c.startswith('L'): c = c[1:-1]
                    if c.startswith(scope) and c not in all_classes:
                        problems.append(f'{n}: class {c}'); checked += 1
                    elif c.startswith(scope): checked += 1
                elif r[0] in ('field','method','imethod'):
                    owner = r[1]
                    if not owner.startswith(scope): continue
                    checked += 1
                    if owner not in all_classes:
                        problems.append(f'{n}: {r[0]} owner missing {owner}.{r[2]}{r[3]}'); continue
                    if r[2] == '<init>':
                        # constructors are not in intermediary tiny; the game jar's own member table has them
                        if ('<init>', r[3]) not in literal.get(('method', owner), ()):
                            problems.append(f'{n}: constructor {owner}.<init>{r[3]}')
                        continue
                    if not member_exists(owner, r[2], r[3], 'field' if r[0]=='field' else 'method'):
                        problems.append(f'{n}: {r[0]} {owner}.{r[2]}{r[3]}')
        # refmap
        for n in names:
            if n.endswith('refmap.json'):
                ref = json.loads(z.read(n))
                for mixin, entries in ref.get('mappings', {}).items():
                    for key, val in entries.items():
                        m = re.match(r'^(?:L([^;]+);)?([^(]+)(\(.*)?$', val)
                        if not m: continue
                        owner, name, desc = m.group(1), m.group(2), m.group(3)
                        if owner and owner not in all_classes:
                            problems.append(f'refmap {mixin}: {key} -> missing class {owner}'); continue
                        if owner and desc and not member_exists(owner, name, desc, 'field' if ':' in desc else 'method'):
                            # field refs look like Lowner;name:Ldesc;
                            fd = desc.split(':')[1] if ':' in desc else desc
                            if not member_exists(owner, name, fd, 'field' if ':' in desc else 'method'):
                                problems.append(f'refmap {mixin}: {key} -> {val}')
                        elif owner is None and desc:
                            problems.append(f'refmap {mixin}: {key} -> {val} (no owner; check manually)')
    print(f'checked {checked} Minecraft references in {mod_jar}')
    hard = []
    for p in sorted(set(problems)):
        cls = p.split(':')[0]
        if any(cls.startswith(a) for a in allowed):
            print('ISOLATED  ', p)
        else:
            print('UNRESOLVED', p); hard.append(p)
    print('unresolved:', len(hard), '(isolated, allowed:', len(set(problems)) - len(hard), ')')
    sys.exit(1 if hard else 0)

if __name__ == '__main__':
    main()
