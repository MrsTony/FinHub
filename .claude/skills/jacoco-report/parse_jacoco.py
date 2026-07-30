#!/usr/bin/env python3
"""Parse JaCoCo jacoco.xml and print coverage summary.

Usage: python parse_jacoco.py [path/to/jacoco.xml]
Default path: target/site/jacoco/jacoco.xml

Output (English labels to avoid GBK terminal garble):
  1. Overview        - LINE/BRANCH/CLASS/METHOD totals
  2. By Package      - LINE coverage per package, descending
  3. DDD Layer Gap   - aggregate LINE per DDD layer vs target
  4. Uncovered Top10 - classes by missed LINE, descending
"""
import sys
import xml.etree.ElementTree as ET

XML_PATH = sys.argv[1] if len(sys.argv) > 1 else 'target/site/jacoco/jacoco.xml'

# DDD layer targets: package prefix -> (layer name, target%)
# Order matters: more specific prefixes first.
LAYER_TARGETS = [
    ('com/finhub/fundflow/domain',                 ('domain',         100)),
    ('com/finhub/fundflow/application',            ('application',     80)),
    ('com/finhub/fundflow/acl',                    ('acl',             80)),
    ('com/finhub/fundflow/infrastructure/adapter', ('acl-adapter',     80)),
    ('com/finhub/fundflow/infrastructure',         ('infrastructure',  70)),
    ('com/finhub/infra',                           ('infrastructure',  70)),
    ('com/finhub/ai',                              ('ai',             100)),
    ('com/finhub/query',                           ('query',           80)),
    ('com/finhub/knowledge',                       ('knowledge',       80)),
]


def pct(m, c):
    t = m + c
    return c / t * 100 if t else 0.0


def layer_of(pkg):
    for prefix, (name, target) in LAYER_TARGETS:
        if pkg.startswith(prefix):
            return name, target
    return 'other', None


root = ET.parse(XML_PATH).getroot()

print("=== Overview ===")
for c in root.findall('counter'):
    m, cov = int(c.get('missed')), int(c.get('covered'))
    print(f"  {c.get('type'):12s} {pct(m, cov):5.1f}%  missed={m} covered={cov}")

print("=== By Package (LINE, desc) ===")
pkgs = []
for p in root.findall('package'):
    c = p.find('counter[@type="LINE"]')
    if c is not None:
        m, cov = int(c.get('missed')), int(c.get('covered'))
        pkgs.append((p.get('name'), pct(m, cov), m, cov))
for name, p, m, cov in sorted(pkgs, key=lambda x: -x[1]):
    print(f"  {p:5.1f}%  {name}  (missed={m}, covered={cov})")

print("=== DDD Layer Gap (LINE) ===")
layer_stats = {}
for name, p, m, cov in pkgs:
    lname, target = layer_of(name)
    s = layer_stats.setdefault(lname, {'missed': 0, 'covered': 0, 'target': target})
    s['missed'] += m
    s['covered'] += cov
for lname, s in layer_stats.items():
    p = pct(s['missed'], s['covered'])
    target = s['target']
    status = '' if target is None else ('OK' if p >= target else f'BELOW(target {target}%)')
    print(f"  {p:5.1f}%  {lname:16s} {status}")

print("=== Uncovered Classes Top 10 (LINE missed, desc) ===")
cls = []
for p in root.findall('package'):
    for cl in p.findall('class'):
        c = cl.find('counter[@type="LINE"]')
        if c is not None:
            m, cov = int(c.get('missed')), int(c.get('covered'))
            cls.append((cl.get('name'), m, cov))
for name, m, cov in sorted(cls, key=lambda x: -x[1])[:10]:
    print(f"  missed={m:4d} covered={cov:4d}  {name}")

print("\nHTML report: target/site/jacoco/index.html")
