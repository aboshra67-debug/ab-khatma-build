"""P16 Safe Patch: individual photo-style 3D cards from archived illustrations.
No auth, group, data, or access-control changes. Keep the original installation ID.
"""
from pathlib import Path
import os

root = Path("app/src/main/java/com/ab/khatma/secure")
def change(file, before, after):
    path = Path(file)
    source = path.read_text(encoding="utf-8")
    count=source.count(before)
    assert count==1, f"P16 unsafe anchor in {path}: {count}"
    path.write_text(source.replace(before,after,1),encoding="utf-8")

def extract_tiles():
    from PIL import Image, ImageEnhance, ImageFilter
    atlas=Image.open(Path(os.environ["GITHUB_WORKSPACE"])/"v0_19/p14_art_atlas.webp").convert("RGB")
    assert atlas.size==(360,180),f"Unexpected atlas dimensions: {atlas.size}"
    paths=["home_card_mushaf","home_card_khatmas","home_card_join",
           "home_card_prayer","home_card_profile","home_card_adhkar",
           "home_daily_quran","home_hero_masjid"]
    folder=Path("app/src/main/res/drawable-nodpi")
    for i,name in enumerate(paths):
        x,y=(i%4)*90,(i//4)*90
        # P14/P15 screenshot had white tails in lower 20%: discard them.
        crop_height=90 if i==7 else 70
        image=atlas.crop((x,y,x+90,y+crop_height))
        image=image.resize((270,270 if i==7 else 210),Image.Resampling.LANCZOS)
        image=ImageEnhance.Sharpness(image).enhance(1.15)
        target=folder/(name+".png")
        assert not target.exists(),f"Unexpected existing image {name}"
        image.save(target,optimize=True)

extract_tiles()

ui=root/"KhatmaVisualHome.kt"
change(ui,
'''import androidx.compose.ui.res.painterResource''',
'''import androidx.compose.ui.res.painterResource
import androidx.annotation.DrawableRes''')
change(ui,
'''private val rim = Color(0xFFE5DDCC)''',
'''private val rim = Color(0xFFE5DDCC)
@DrawableRes private fun p16Image(index:Int):Int=when(index) {
    0 -> com.ab.khatma.R.drawable.home_card_mushaf
    1 -> com.ab.khatma.R.drawable.home_card_khatmas
    2 -> com.ab.khatma.R.drawable.home_card_join
    3 -> com.ab.khatma.R.drawable.home_card_prayer
    4 -> com.ab.khatma.R.drawable.home_card_profile
    5 -> com.ab.khatma.R.drawable.home_card_adhkar
    6 -> com.ab.khatma.R.drawable.home_daily_quran
    else -> com.ab.khatma.R.drawable.home_hero_masjid
}''')
change(ui,
'''                KhatmaArt(7, Modifier.fillMaxSize())''',
'''                Image(painterResource(p16Image(7)), contentDescription=null,
                    contentScale=ContentScale.Crop, modifier=Modifier.fillMaxSize())''')
change(ui,
'''            KhatmaArt(6,Modifier.size(64.dp))''',
'''            Image(painterResource(p16Image(6)),contentDescription=null,
                contentScale=ContentScale.Crop,
                modifier=Modifier.size(64.dp).clip(RoundedCornerShape(15.dp)))''')
change(ui,
'''            KhatmaArt(art,Modifier.size(height*0.67f))''',
'''            Image(painterResource(p16Image(art)),contentDescription=null,
                contentScale=ContentScale.Crop,
                modifier=Modifier.size(height*0.70f).clip(RoundedCornerShape(15.dp)))''')
change(ui,
'''        modifier=modifier.height(height),shape=RoundedCornerShape(21.dp),''',
'''        modifier=modifier.height(height),shape=RoundedCornerShape(22.dp),''')
change(ui,
'''    val gap=(8f*scale).dp
    val cardHeight=(100f*scale).dp''',
'''    val gap=(10f*scale).dp
    val cardHeight=(108f*scale).dp''')
change(ui,
'''        VisualGreeting(userName,(136f*scale).dp)
        VisualWard((81f*scale).dp,onOpenMushaf)''',
'''        VisualGreeting(userName,(136f*scale).dp)
        VisualWard((86f*scale).dp,onOpenMushaf)''')
change(ui,
'''            KhatmaArt(art,Modifier.size(height*0.70f))''',
'''            KhatmaArt(art,Modifier.size(height*0.70f))''') if False else None
change("app/build.gradle.kts",
'''        versionCode = 31
        versionName = "0.19.7-home-visual-rtl-repair"''',
'''        versionCode = 32
        versionName = "0.19.8-islamic-3d-home"''')
v=Path("tools/verify_project.py")
s=v.read_text(encoding="utf-8")
assert "versionCode 31" in s and "0.19.7-home-visual-rtl-repair" in s
v.write_text(s.replace("versionCode 31","versionCode 32")
    .replace("versionCode = 31","versionCode = 32")
    .replace("0.19.7-home-visual-rtl-repair","0.19.8-islamic-3d-home"),encoding="utf-8")
assert 'KhatmaVisualHome(' in (root/"SecureV2Screen.kt").read_text()
assert 'SecureV2Api.googleLogin(firebaseToken)' in (root/"SecureV2Screen.kt").read_text()
print("P16 PASS: standalone 3D-style illustration resources, no missing drawables.")
