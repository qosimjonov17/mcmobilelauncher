#!/usr/bin/env python3
"""Java runtime'larni APK ichiga joylash uchun tayyorlaydi.

AngelAuraMC/angelauramc-openjdk-build release'laridagi har bir arxitektura uchun
toʻliq JRE arxivlarini yuklab oladi va launcher kutadigan "binpack" koʻrinishiga keltiradi:

    <dest>/version             - runtime versiyasi (oʻzgarsa, launcher qayta ochadi)
    <dest>/universal.tar.xz    - barcha arxitekturalarda bir xil boʻlgan fayllar
    <dest>/bin-<arch>.tar.xz   - shu arxitekturaga xos fayllar

Shu tufayli oʻyin ishga tushganda Java GitHub'dan yuklab olinmaydi.

Foydalanish: bundle_jre.py <java_versiya> <dest_papka> <arch> [<arch> ...]
"""
import hashlib
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.request

URL = ("https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/"
       "download_jre{v}/jre{v}-android-{a}.tar.xz")


def download(url, path):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=120) as r, open(path, "wb") as f:
                shutil.copyfileobj(r, f)
            return
        except Exception as e:  # noqa: BLE001 - tarmoq xatolarida qayta urinamiz
            print(f"  {url} yuklab olinmadi ({e}), qayta urinish {attempt + 1}/4")
    raise SystemExit(f"Yuklab boʻlmadi: {url}")


def entry_key(root, rel):
    path = os.path.join(root, rel)
    if os.path.islink(path):
        return "link:" + os.readlink(path)
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def list_entries(root):
    entries = {}
    for dirpath, dirnames, filenames in os.walk(root):
        for name in filenames + [d for d in dirnames if os.path.islink(os.path.join(dirpath, d))]:
            rel = os.path.relpath(os.path.join(dirpath, name), root)
            entries[rel] = entry_key(root, rel)
    return entries


def make_tar_xz(root, files, out):
    list_file = out + ".list"
    with open(list_file, "w") as f:
        for rel in sorted(files):
            f.write("./" + rel + "\n")
    with open(out, "wb") as dst:
        tar = subprocess.Popen(["tar", "-C", root, "--owner=0", "--group=0", "-cf", "-",
                                "--no-recursion", "-T", list_file], stdout=subprocess.PIPE)
        subprocess.run(["xz", "-T0", "-6", "-c"], stdin=tar.stdout, stdout=dst, check=True)
        if tar.wait() != 0:
            raise SystemExit("tar xatosi: " + out)
    os.remove(list_file)


def main():
    if len(sys.argv) < 4:
        raise SystemExit(__doc__)
    version, dest, archs = sys.argv[1], sys.argv[2], sys.argv[3:]
    os.makedirs(dest, exist_ok=True)
    work = tempfile.mkdtemp(prefix=f"jre{version}-")
    digest = hashlib.sha256()
    entries = {}
    for arch in archs:
        archive = os.path.join(work, f"{arch}.tar.xz")
        print(f"Java {version} ({arch}) yuklab olinmoqda...")
        download(URL.format(v=version, a=arch), archive)
        with open(archive, "rb") as f:
            digest.update(f.read())
        root = os.path.join(work, arch)
        os.makedirs(root)
        subprocess.run(["tar", "-xJf", archive, "-C", root], check=True)
        os.remove(archive)
        entries[arch] = list_entries(root)

    first = archs[0]
    common = {rel for rel, key in entries[first].items()
              if all(entries[a].get(rel) == key for a in archs[1:])}
    make_tar_xz(os.path.join(work, first), common, os.path.join(dest, "universal.tar.xz"))
    for arch in archs:
        specific = set(entries[arch]) - common
        make_tar_xz(os.path.join(work, arch), specific, os.path.join(dest, f"bin-{arch}.tar.xz"))

    with open(os.path.join(dest, "version"), "w") as f:
        f.write(f"jre{version}-{digest.hexdigest()[:16]}")
    shutil.rmtree(work)
    for name in sorted(os.listdir(dest)):
        print(f"  {name}: {os.path.getsize(os.path.join(dest, name)) // 1048576} MB")


if __name__ == "__main__":
    main()
