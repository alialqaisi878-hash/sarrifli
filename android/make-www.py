# Copies the web app (repo root) into the APK's built-in copy, with local fonts instead of Google Fonts.
import os, re, shutil, sys
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
www = sys.argv[1] if len(sys.argv) > 1 else os.path.join(root, 'android/app/src/main/assets/www')
shutil.rmtree(www, ignore_errors=True); os.makedirs(www)
s = open(os.path.join(root, 'index.html'), encoding='utf-8').read()
s = re.sub(r'<link rel="preconnect"[^>]*>\s*', '', s)
s = re.sub(r'<link href="https://fonts.googleapis.com[^>]*>', '<link href="fonts/fonts.css" rel="stylesheet">', s)
s = s.replace('<link rel="manifest" href="manifest.webmanifest">', '')
open(os.path.join(www, 'index.html'), 'w', encoding='utf-8').write(s)
shutil.copy(os.path.join(root, 'icon-192.png'), www)
shutil.copytree(os.path.join(root, 'fb'), os.path.join(www, 'fb'))
shutil.copytree(os.path.join(root, 'android/fonts'), os.path.join(www, 'fonts'))
