"""P17 safe visual patch: full illustration visibility + raised 3D-style buttons.
Preserves all P16 navigation, Google auth, group roles, reader and data.
"""
from pathlib import Path
root=Path("app/src/main/java/com/ab/khatma/secure")
def fix(path,old,new):
    p=Path(path)
    content=p.read_text(encoding="utf-8")
    assert content.count(old)==1, "Unsafe P17 patch anchor: "+str(path)+" "+old[:40]+" hits="+str(content.count(old))
    p.write_text(content.replace(old,new,1), encoding="utf-8")

ui=root/"KhatmaVisualHome.kt"
# Ensure images are always displayed in full, not center-cropped.
fix(ui, 'contentScale=ContentScale.Crop, modifier=Modifier.fillMaxSize())',
        'contentScale=ContentScale.Fit, modifier=Modifier.fillMaxSize().padding(5.dp))')
fix(ui, '''                contentScale=ContentScale.Crop,
                modifier=Modifier.size(64.dp).clip(RoundedCornerShape(15.dp)))''',
        '''                contentScale=ContentScale.Fit,
                modifier=Modifier.size(64.dp).clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFFFFFAF0)).padding(3.dp))''')
fix(ui, '''                contentScale=ContentScale.Crop,
                modifier=Modifier.size(height*0.70f).clip(RoundedCornerShape(15.dp)))''',
        '''                contentScale=ContentScale.Fit,
                modifier=Modifier.size(height*0.69f).clip(RoundedCornerShape(17.dp))
                    .background(Color(0xFFFFFAF1)).padding(3.dp))''')
# Large greeting card gains tangible lift, without cropping its mosque artwork.
fix(ui, '''    Card(modifier=Modifier.fillMaxWidth().height(height),
        shape=RoundedCornerShape(24.dp),
        colors=CardDefaults.cardColors(containerColor=emerald)) {''',
       '''    Card(modifier=Modifier.fillMaxWidth().height(height),
        shape=RoundedCornerShape(25.dp),
        elevation=CardDefaults.cardElevation(defaultElevation=6.dp),
        colors=CardDefaults.cardColors(containerColor=emerald)) {''')
# Quran card stays functional, gains soft 3D elevation and readable spacing.
fix(ui, '''    OutlinedCard(onClick=onRead,modifier=Modifier.fillMaxWidth().height(height),
        shape=RoundedCornerShape(23.dp),border=BorderStroke(1.dp,Color(0xFFD9E7DE)),
        colors=CardDefaults.outlinedCardColors(containerColor=Color.White)) {''',
       '''    Card(onClick=onRead,modifier=Modifier.fillMaxWidth().height(height),
        shape=RoundedCornerShape(23.dp),border=BorderStroke(1.dp,Color(0xFFD9E7DE)),
        elevation=CardDefaults.cardElevation(defaultElevation=4.dp,pressedElevation=1.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White)) {''')
# Full-clickable buttons retain their onClick handlers and disabled policy.
fix(ui, '''    OutlinedCard(onClick=onClick,enabled=enabled,
        modifier=modifier.height(height),shape=RoundedCornerShape(22.dp),
        border=BorderStroke(1.dp,rim),
        colors=CardDefaults.outlinedCardColors(containerColor=Color.White,
            disabledContainerColor=Color(0xFFFCFAF4))) {''',
       '''    Card(onClick=onClick,enabled=enabled,
        modifier=modifier.height(height),shape=RoundedCornerShape(23.dp),
        border=BorderStroke(1.dp,rim),
        elevation=CardDefaults.cardElevation(defaultElevation=7.dp,
            pressedElevation=2.dp, disabledElevation=1.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White,
            disabledContainerColor=Color(0xFFFCFAF4))) {''')
# Raised surfaces and light gold gradient highlight: visual only.
fix(ui, '''        Row(Modifier.fillMaxSize().padding(horizontal=7.dp, vertical=6.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(3.dp)) {''',
       '''        Row(Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.White,Color(0xFFFFF9EC))))
            .padding(horizontal=7.dp, vertical=6.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(4.dp)) {''')
fix(ui, '''    val gap=(10f*scale).dp
    val cardHeight=(108f*scale).dp''',
       '''    val gap=(12f*scale).dp
    val cardHeight=(112f*scale).dp''')
fix("app/build.gradle.kts",
       'versionCode = 32\n        versionName = "0.19.8-islamic-3d-home"',
       'versionCode = 33\n        versionName = "0.19.9-3d-full-artwork"')
v=Path("tools/verify_project.py")
s=v.read_text(encoding="utf-8")
assert "versionCode 32" in s and "0.19.8-islamic-3d-home" in s
v.write_text(s.replace("versionCode 32","versionCode 33")
  .replace("versionCode = 32","versionCode = 33")
  .replace("0.19.8-islamic-3d-home","0.19.9-3d-full-artwork"),encoding="utf-8")
assert ui.read_text(encoding="utf-8").count("contentScale=ContentScale.Crop")==0
assert "KhatmaVisualHome(" in (root/"SecureV2Screen.kt").read_text(encoding="utf-8")
print("P17 PASS: full-fit artwork and raised 3D-style card surfaces; business logic untouched.")
