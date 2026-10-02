"""Installation/navigation checks on an isolated emulator, never a user's game session."""
import subprocess, time, pathlib, xml.etree.ElementTree as ET, re
out=pathlib.Path('android-smoke');out.mkdir(exist_ok=True)
def adb(*args, check=True):
    return subprocess.run(['adb',*args],check=check,capture_output=True).stdout
pkg='com.fillipe.idledexcompanion'
# Previous code payload, with its invalid legacy signature repaired using the original key.
old=adb('install','releases/idledex-companion-v2.2.1.apk',check=False).decode()
(out/'previous-install.txt').write_text(old)
assert 'Success' in old,old
result=adb('install','-r','releases/idledex-companion-v2.3.0.apk').decode()
assert 'Success' in result,result
(out/'new-install.txt').write_text(result)
adb('logcat','-c')
adb('shell','am','start','-W','-n',pkg+'/com.fillipe.webmapper.CompanionHomeActivity')
time.sleep(3)
def screen(name):
    (out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
    adb('shell','uiautomator','dump','/sdcard/window.xml')
    data=adb('shell','cat','/sdcard/window.xml').decode()
    (out/(name+'.xml')).write_text(data)
    return ET.fromstring(data)
def tap(label,tree):
    nodes=[n for n in tree.iter('node') if n.get('text')==label]
    assert len(nodes)==1,(label,len(nodes))
    x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[0].get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(1)
home=screen('01-home')
assert any('Seu jogo.' in n.get('text','') for n in home.iter('node'))
tap('Minha Box',home)
report=screen('02-box')
assert any('Sua coleção, explicada.'==n.get('text') for n in report.iter('node'))
tap('Proteção',report)
rules=screen('03-rules')
assert any('Sua proteção'==n.get('text') for n in rules.iter('node'))
adb('shell','input','keyevent','4');time.sleep(1)
adb('shell','input','keyevent','4');time.sleep(1)
home=screen('04-home-return')
assert any('Seu jogo.' in n.get('text','') for n in home.iter('node'))
logs=adb('logcat','-d').decode(errors='replace');(out/'logcat.txt').write_text(logs)
assert 'FATAL EXCEPTION' not in logs,'Application crashed'
(out/'result.txt').write_text('PASS: signed APK installation, launcher, Box tab, protection dialog, back navigation, no crash. Previous APK: '+old.strip())
print((out/'result.txt').read_text())
