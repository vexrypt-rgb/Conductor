"""Assemble a production Fabric classpath for a Minecraft version from the local Gradle caches.
Usage: prod_classpath.py <mc> <loader_version> <out_file> [<server_dir>]
  -> writes the classpath (one path per line); prints missing. With <server_dir> (a vanilla server that has run once,
     so its bundler has unpacked versions/ and libraries/), it is a dedicated-server classpath instead of a client one; before 1.18 server.jar itself is used."""
import json, sys, glob, os, zipfile
mc, loader_ver, out = sys.argv[1], sys.argv[2], sys.argv[3]
server_dir = sys.argv[4] if len(sys.argv) > 4 else None
G = '/root/.gradle/caches/modules-2/files-2.1'
U = '/root/.gradle/caches/unimined'
def find(group, artifact, version, classifier=None):
    name = f"{artifact}-{version}" + (f"-{classifier}" if classifier else "") + ".jar"
    hits = glob.glob(f"{G}/{group}/{artifact}/{version}/*/{name}") + glob.glob(f"{U}/**/{name}", recursive=True)
    return hits[0] if hits else None
cp, missing = [], []
loader = find('net.fabricmc', 'fabric-loader', loader_ver)
cp.append(loader)
inst = json.loads(zipfile.ZipFile(loader).read('fabric-installer.json'))
taken = set()
for lib in inst['libraries']['common'] + inst['libraries'].get('client', []):
    g, a, v = lib['name'].split(':')[:3]
    taken.add((g, a, None))
    p = find(g, a, v)
    (cp.append(p) if p else missing.append(lib['name']))
inter = glob.glob(f"{G}/net.fabricmc/intermediary/{mc}/*/intermediary-{mc}-v2.jar")
(cp.append(inter[0]) if inter else missing.append('intermediary'))
v = json.load(open(f"{U}/net/minecraft/minecraft/{mc}/version.json"))
if server_dir:
    # the bundler's own list: libraries/... and versions/<mc>/server-<mc>.jar, relative to the server folder.
    # Servers before 1.18 are not bundlers: server.jar is the game with its libraries inside, used as it is.
    server_jar = zipfile.ZipFile(os.path.join(server_dir, 'server.jar'))
    joined = (server_jar.read('META-INF/classpath-joined').decode().strip()
              if 'META-INF/classpath-joined' in server_jar.namelist() else 'server.jar')
    for rel in joined.split(';'):
        parts = rel.split('/')
        if parts[0] == 'libraries' and len(parts) >= 5 and ('.'.join(parts[1:-3]), parts[-3], None) in taken:
            continue  # the loader's copy wins (the 1.21.4 server bundles asm 9.6)
        p = os.path.join(server_dir, rel)
        (cp.append(p) if os.path.isfile(p) else missing.append(rel))
    v['libraries'] = []
def allowed(lib):
    rules = lib.get('rules')
    if not rules: return True
    ok = False
    for r in rules:
        osname = r.get('os', {}).get('name')
        if osname is None or osname == 'linux':
            ok = r['action'] == 'allow'
    return ok
for lib in v['libraries']:
    if not allowed(lib): continue
    parts = lib['name'].split(':')
    g, a, ver = parts[:3]
    cls = parts[3] if len(parts) > 3 else None
    if (g, a, cls) in taken: continue  # the loader's copy wins, as in the launcher (MC 1.21.4 lists asm 9.6 too)
    p = find(g, a, ver, cls)
    if p: cp.append(p)
    else: missing.append(lib['name'])
    native = lib.get('natives', {}).get('linux')  # versions before 1.19 list natives as a classifier of the library
    if native:
        p = find(g, a, ver, native)
        (cp.append(p) if p else missing.append(f"{lib['name']}:{native}"))
if not server_dir:
    cp.append(f"{U}/net/minecraft/minecraft/{mc}/minecraft-{mc}-client.jar")
seen=[]; [seen.append(x) for x in cp if x and x not in seen]
open(out,'w').write('\n'.join(seen))
print(len(seen), 'entries;', 'missing:', missing)
print('assetIndex', v['assetIndex']['id'])
