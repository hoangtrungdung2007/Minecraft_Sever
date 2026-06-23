# -*- coding: utf-8 -*-
"""
Script tu dong tai Chunky va Spark vao thu muc plugins/
Chay: python download_plugins.py

Yeu cau: Python 3.8+
"""

import urllib.request
import urllib.error
import os
import sys
from pathlib import Path

# Fix encoding on Windows
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

PLUGINS_DIR = Path("plugins")
PLUGINS_DIR.mkdir(exist_ok=True)

DOWNLOADS = [
    {
        "name": "Chunky 1.4.40",
        "filename": "Chunky-Bukkit-1.4.40.jar",
        "urls": [
            "https://cdn.modrinth.com/data/fALzjamp/versions/P3y2MXnd/Chunky-Bukkit-1.4.40.jar",
            "https://hangarcdn.papermc.io/plugins/pop4959/Chunky/versions/1.4.40/PAPER/Chunky-Bukkit-1.4.40.jar",
        ],
        "expected_size": 296244,
        "note": "Pre-generate world, giam lag"
    },
    {
        "name": "Spark 1.10.173",
        "filename": "spark-1.10.173-bukkit.jar",
        "urls": [
            "https://ci.lucko.me/job/spark/lastSuccessfulBuild/artifact/spark-bukkit/build/libs/spark-1.10.173-bukkit.jar",
        ],
        "expected_size": None,
        "note": "Monitor CPU/RAM/TPS"
    },
    {
        "name": "Geyser 2.10.1 (Bedrock Support)",
        "filename": "Geyser-Spigot.jar",
        "urls": [
            "https://download.geysermc.org/v2/projects/geyser/versions/2.10.1/builds/1172/downloads/spigot",
        ],
        "expected_size": None,
        "note": "Cho phep Bedrock/Mobile ket noi Java server"
    },
    {
        "name": "Floodgate 2.2.5 (Bedrock Auth)",
        "filename": "floodgate-spigot.jar",
        "urls": [
            "https://download.geysermc.org/v2/projects/floodgate/versions/2.2.5/builds/134/downloads/spigot",
        ],
        "expected_size": None,
        "note": "Xac thuc Bedrock player khong can tai khoan Java"
    },
]



def download_file(url, filepath):
    """Tai file voi progress bar."""
    print("  -> Dang tai tu: " + url)
    
    try:
        req = urllib.request.Request(
            url,
            headers={
                "User-Agent": "Mozilla/5.0 (Minecraft-Plugin-Downloader/1.0)",
                "Accept": "*/*",
            }
        )
        
        with urllib.request.urlopen(req, timeout=90) as response:
            total = int(response.headers.get("Content-Length", 0))
            downloaded = 0
            chunk_size = 16384
            
            with open(filepath, "wb") as f:
                while True:
                    chunk = response.read(chunk_size)
                    if not chunk:
                        break
                    f.write(chunk)
                    downloaded += len(chunk)
                    
                    if total > 0:
                        percent = downloaded / total * 100
                        bar = "#" * int(percent // 5) + "." * (20 - int(percent // 5))
                        print(f"\r  [{bar}] {percent:.1f}% ({downloaded//1024}/{total//1024} KB)    ", end="", flush=True)
            
            print()  # newline
        
        actual_size = filepath.stat().st_size
        if actual_size < 1000:
            print("  [LOI] File rong hoac qua nho!")
            filepath.unlink()
            return False
        
        print(f"  [OK] Tai thanh cong: {actual_size//1024} KB")
        return True
        
    except urllib.error.HTTPError as e:
        print(f"\n  [LOI] HTTP {e.code}: {e.reason}")
    except urllib.error.URLError as e:
        print(f"\n  [LOI] Khong ket duoc mang: {e.reason}")
    except Exception as e:
        print(f"\n  [LOI] {type(e).__name__}: {e}")
    
    if filepath.exists():
        filepath.unlink()
    return False


def main():
    print("=" * 60)
    print("  Minecraft Plugin Downloader for Paper 1.21.4")
    print("  Chunky 1.4.40 + Spark 1.10.173")
    print("=" * 60)
    print()
    
    success_count = 0
    
    for plugin in DOWNLOADS:
        filepath = PLUGINS_DIR / plugin["filename"]
        
        print(f"[PLUGIN] {plugin['name']} - {plugin['note']}")
        
        # Kiem tra neu da co
        if filepath.exists() and filepath.stat().st_size > 10000:
            print(f"  [OK] Da co san: {filepath.stat().st_size//1024} KB - Bo qua")
            success_count += 1
            print()
            continue
        
        # Thu tung URL
        downloaded = False
        for url in plugin["urls"]:
            if download_file(url, filepath):
                downloaded = True
                break
            else:
                if len(plugin["urls"]) > 1:
                    print("  -> Thu URL tiep theo...")
        
        if downloaded:
            success_count += 1
        else:
            print(f"  [THAT BAI] Khong the tai {plugin['name']}!")
            print(f"  -> Tai thu cong tai: {plugin['urls'][0]}")
            print(f"  -> Dat file vao: plugins\\{plugin['filename']}")
        
        print()
    
    print("=" * 60)
    print(f"  Ket qua: {success_count}/{len(DOWNLOADS)} plugin tai thanh cong")
    print()
    
    if success_count == len(DOWNLOADS):
        print("  [THANH CONG] Tat ca plugin san sang!")
        print("  -> Khoi dong server bang run.bat")
    else:
        print("  [CANH BAO] Mot so plugin chua tai duoc.")
        print("  -> Tai thu cong va dat vao thu muc plugins/")
        print()
        print("  Link tai thu cong:")
        for p in DOWNLOADS:
            fp = PLUGINS_DIR / p["filename"]
            if not fp.exists() or fp.stat().st_size < 10000:
                print(f"  {p['name']}: {p['urls'][0]}")
    
    print("=" * 60)
    
    try:
        input("\nNhan Enter de thoat...")
    except EOFError:
        pass


if __name__ == "__main__":
    main()
