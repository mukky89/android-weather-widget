"""Import the MIT licensed name-day facts; no remote code is executed."""
import json
import pathlib
import re
import unicodedata
import urllib.request

base = 'https://raw.githubusercontent.com/peterknezek/name-day-calendar/main/'
source = urllib.request.urlopen(base + 'data/countries/SK.yaml').read().decode('utf-8')
days = {}
key = None
for line in source.splitlines():
    match = re.fullmatch(r'\s{6}(\d{2}-\d{2}):', line)
    if match:
        key = match[1]
        days[key] = []
    match = re.match(r'\s+- name: (.+)', line)
    if match and key:
        days[key].append(unicodedata.normalize('NFC', match[1].strip()))
target = pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/assets'
target.mkdir(parents=True, exist_ok=True)
(target / 'namedays-sk.json').write_text(json.dumps(days, ensure_ascii=False), encoding='utf-8')
(target / 'namedays-LICENSE.txt').write_bytes(urllib.request.urlopen(base + 'LICENSE').read())
(target / 'namedays-source.txt').write_text('https://github.com/peterknezek/name-day-calendar\nSource: data/countries/SK.yaml\nMIT license; Unicode normalized to NFC.\n', encoding='utf-8')
assert len(days) >= 350
print('Name-day dates:', len(days))
