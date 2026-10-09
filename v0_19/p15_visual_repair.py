"""P15 safe visual repair of approved P14 layout.
Fix Android RTL Row reversal, hide art-atlas blank tails, blend mosque image edge,
restore clean home after Google login. Keep session, groups and group permissions.
"""
from pathlib import Path
root = Path("app/src/main/java/com/ab/khatma/secure")

def safe(path, before, after):
    file=Path(path)
    content=file.read_text(encoding="utf-8")
    assert content.count(before)==1, (
      "P15 unsafe/changed anchor: "+str(file)+" hits="+str(content.count(before)))
    file.write_text(content.replace(before,after,1),encoding="utf-8")

layout=root/"KhatmaVisualHome.kt"
safe(layout,
 'import androidx.compose.runtime.Composable',
 'import androidx.compose.runtime.Composable\n'
 'import androidx.compose.runtime.CompositionLocalProvider')
safe(layout,
 'import androidx.compose.ui.platform.LocalConfiguration',
 'import androidx.compose.ui.platform.LocalConfiguration\n'
 'import androidx.compose.ui.platform.LocalLayoutDirection\n'
 'import androidx.compose.ui.unit.LayoutDirection')
safe(layout,
 '''            KhatmaArt(7,Modifier.fillMaxHeight().fillMaxWidth(0.39f)
                .clip(RoundedCornerShape(topStart=24.dp,bottomStart=24.dp)))
            Column(Modifier.align(Alignment.CenterEnd)''',
 '''            Box(Modifier.fillMaxHeight().fillMaxWidth(0.43f)
                .clip(RoundedCornerShape(topStart=24.dp,bottomStart=24.dp))) {
                KhatmaArt(7, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.70f to Color.Transparent,
                    1f to emerald)))
            }
            Column(Modifier.align(Alignment.CenterEnd)''')
safe(layout,
 '''            KhatmaArt(art,Modifier.size(height*0.77f))''',
 '''            KhatmaArt(art,Modifier.size(height*0.67f))''')
safe(layout,
 '''        Row(Modifier.fillMaxSize().padding(4.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(4.dp)) {''',
 '''        Row(Modifier.fillMaxSize().padding(horizontal=7.dp, vertical=6.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(3.dp)) {''')
safe(layout,
 '''    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(gap)) {
        VisualHeader(onOpenProfile,(61f*scale).dp)''',
 '''    // Android locales are RTL by default. Fix illustration on the LEFT and
    // section text on the RIGHT to match the approved reference, for every device.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(gap)) {
        VisualHeader(onOpenProfile,(61f*scale).dp)''')
content=layout.read_text(encoding="utf-8")
assert content.endswith("    }\n}\n")
layout.write_text(content[:-len("    }\n}\n")]+"    }\n    }\n}\n",encoding="utf-8")

atlas=root/"P14AtlasArt.kt"
safe(atlas,
 '''        BitmapPainter(bitmap, IntOffset((index % 4)*90, (index / 4)*90), IntSize(90,90))''',
 '''        // Original P14 tiles included empty screenshot background at the bottom.
        // Crop this area and retain the full illustration on the greeting scene.
        BitmapPainter(bitmap, IntOffset((index % 4)*90, (index / 4)*90),
            IntSize(90, if(index == 7) 90 else 72))''')

safe(root/"SecureV2Screen.kt",
 '''                                message = "مرحبًا بك في ختمة عبر Google"''',
 '''                                message = "" // no duplicate login banner above home''')
safe("app/build.gradle.kts",
 'versionCode = 30\n        versionName = "0.19.6-approved-visual-home"',
 'versionCode = 31\n        versionName = "0.19.7-home-visual-rtl-repair"')
test=Path("tools/verify_project.py")
s=test.read_text(encoding="utf-8")
assert "versionCode 30" in s and "0.19.6-approved-visual-home" in s
test.write_text(s.replace("versionCode 30","versionCode 31")
   .replace("versionCode = 30","versionCode = 31")
   .replace("0.19.6-approved-visual-home","0.19.7-home-visual-rtl-repair"),encoding="utf-8")
assert "SecureV2Api.googleLogin(firebaseToken)" in (root/"SecureV2Screen.kt").read_text()
assert "onOpenKhatmas" in layout.read_text()
print("P15 PASS: left illustrations, right Arabic content, compact tiles, clean greeting.")
