import os
import shutil
import urllib.request

print("Downloading Paper 1.21.11-69...")
urllib.request.urlretrieve('https://api.papermc.io/v2/projects/paper/versions/1.21.11/builds/69/downloads/paper-1.21.11-69.jar', 'paper-1.21.11-69.jar')
print("Downloaded.")

print("Backing up playerdata...")
if not os.path.exists('backup_playerdata'):
    os.makedirs('backup_playerdata')
if os.path.exists('world/playerdata'):
    if os.path.exists('backup_playerdata/playerdata'):
        shutil.rmtree('backup_playerdata/playerdata')
    shutil.copytree('world/playerdata', 'backup_playerdata/playerdata')

if os.path.exists('world/advancements'):
    if os.path.exists('backup_playerdata/advancements'):
        shutil.rmtree('backup_playerdata/advancements')
    shutil.copytree('world/advancements', 'backup_playerdata/advancements')

print("Removing old world...")
for folder in ['world', 'world_nether', 'world_the_end']:
    if os.path.exists(folder):
        shutil.rmtree(folder)

print("Updating run.bat...")
if os.path.exists('run.bat'):
    with open('run.bat', 'r', encoding='utf-8') as f:
        content = f.read()
    content = content.replace('paper-1.21.4-232.jar', 'paper-1.21.11-69.jar')
    with open('run.bat', 'w', encoding='utf-8') as f:
        f.write(content)

print("Done! You can now start the server.")
