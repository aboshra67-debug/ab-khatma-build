"""P52 safe, allowlisted patch applied on top of the P51 / VC73 safe source.

1. Home: compact khatma cards (same data and callbacks as P39), up to 4 on the home screen.
2. Mushaf surah list: listen button per surah, reciter picker, multi-surah selection,
   mini player, and a media foreground service so playback continues with the screen off.

Nothing is deleted. Every replacement must match exactly once or the build stops.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1])
here = Path(__file__).parent
java = root / "app/src/main/java/com/ab/khatma"


def patch(path, old, new):
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    assert count == 1, (str(path), count, old[:90])
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def add_file(src_name, dest):
    assert not dest.exists(), dest
    dest.write_bytes((here / src_name).read_bytes())


# ---- version -------------------------------------------------------------
gradle = root / "app/build.gradle.kts"
patch(gradle, "versionCode = 73", "versionCode = 74")
patch(gradle, 'versionName = "0.19.46-p51-home-legacy-schedule"',
      'versionName = "0.19.47-p52-compact-wards-surah-listen"')

# ---- 1. compact home cards ------------------------------------------------
add_file("P52CompactWards.kt", java / "secure/P52CompactWards.kt")
ui = java / "secure/SecureV2Screen.kt"
patch(ui,
      '''                                P39ReaderTodayHome(
                                    assignments = groups,
                                    maxPreview = 2,''',
      '''                                P52CompactWards(
                                    assignments = groups,
                                    maxPreview = 4,''')
patch(ui,
      '''                        P39ReaderTodayHome(assignments = groups, busy = busy,''',
      '''                        P52CompactWards(assignments = groups, busy = busy,''')

# ---- 2. surah listening ---------------------------------------------------
add_file("P52SurahAudio.kt", java / "quran/P52SurahAudio.kt")
add_file("P52SurahListenUi.kt", java / "ui/screens/P52SurahListenUi.kt")

manifest = root / "app/src/main/AndroidManifest.xml"
patch(manifest,
      '''        <service android:name=".prayer.AdhanPlaybackService"
            android:exported="false" android:foregroundServiceType="mediaPlayback" />''',
      '''        <service android:name=".prayer.AdhanPlaybackService"
            android:exported="false" android:foregroundServiceType="mediaPlayback" />
        <service android:name=".quran.P52ListenService"
            android:exported="false" android:foregroundServiceType="mediaPlayback" />''')

quran = java / "ui/screens/QuranScreen.kt"
patch(quran, "import com.ab.khatma.quran.HusaryAudioConfig\n",
      "import com.ab.khatma.quran.HusaryAudioConfig\nimport com.ab.khatma.quran.P52SurahAudio\n")
# The verse player and the surah player never overlap.
patch(quran,
      "    fun playAyah(aya: QuranAyah, fromSequence: Boolean = false) {\n",
      "    fun playAyah(aya: QuranAyah, fromSequence: Boolean = false) {\n        P52SurahAudio.pause()\n")
patch(quran,
      '''    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (!repo.isBundled()) {''',
      '''    DisposableEffect(Unit) {
        P52SurahAudio.init(context)
        P52SurahAudio.beforePlay = { stopAudio() }
        onDispose { P52SurahAudio.beforePlay = null }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (!repo.isBundled()) {''')

# Index screen: selection state, toolbar above the list, list takes remaining height, mini player below.
patch(quran,
      '''    var tab by remember { mutableIntStateOf(0) }
    val lastPage = prefs.lastPage()''',
      '''    var tab by remember { mutableIntStateOf(0) }
    val listenContext = LocalContext.current
    var selecting by remember { mutableStateOf(false) }
    var selectedSurahs by remember { mutableStateOf<List<Int>>(emptyList()) }
    val lastPage = prefs.lastPage()''')
patch(quran,
      '''                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 0.dp, top = 10.dp, end = 0.dp, bottom = 20.dp),''',
      '''                if (tab == 0) {
                    P52ListenToolbar(
                        selecting = selecting,
                        selectedCount = selectedSurahs.size,
                        onToggleSelecting = { selecting = !selecting; selectedSurahs = emptyList() },
                        onPlaySelected = {
                            P52SurahAudio.play(listenContext, selectedSurahs.sorted())
                            selecting = false
                            selectedSurahs = emptyList()
                        },
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 0.dp, top = 10.dp, end = 0.dp, bottom = 20.dp),''')
patch(quran,
      '''                        items(repo.surahs(), key = { "surah_${it.index}" }) { surah ->
                            QuranIndexTile(
                                number = surah.index,
                                title = "سورة ${surah.name}",
                                subtitle = "${surah.ayahs} آية  •  تبدأ من صفحة ${repo.firstPageOfSurah(surah.index)}",
                                green = green, gold = gold, border = line,
                                onClick = { onOpenSurah(surah.index) },
                            )
                        }''',
      '''                        items(repo.surahs(), key = { "surah_${it.index}" }) { surah ->
                            P52SurahTile(
                                number = surah.index,
                                title = "سورة ${surah.name}",
                                subtitle = "${surah.ayahs} آية  •  تبدأ من صفحة ${repo.firstPageOfSurah(surah.index)}",
                                selecting = selecting,
                                selected = surah.index in selectedSurahs,
                                emblem = { MushafNumberEmblem(surah.index) },
                                onOpen = { onOpenSurah(surah.index) },
                                onListen = {
                                    if (P52SurahAudio.currentSurah == surah.index) P52SurahAudio.togglePause()
                                    else P52SurahAudio.play(listenContext, (surah.index..114).toList())
                                },
                                onToggleSelect = {
                                    selectedSurahs = if (surah.index in selectedSurahs)
                                        selectedSurahs - surah.index else selectedSurahs + surah.index
                                },
                            )
                        }''')
patch(quran,
      '''                                onClick = { onOpenJuz(juz) },
                            )
                        }
                    }
                }
            }
        }
    }
}''',
      '''                                onClick = { onOpenJuz(juz) },
                            )
                        }
                    }
                }
                P52MiniPlayer()
            }
        }
    }
}''')

# ---- safety checks --------------------------------------------------------
assert len(list((root / "app/src/main/assets/quran_pages").glob("*.webp"))) == 604
assert len(list((root / "app/src/main/res/raw").glob("adhan_*.ogg"))) == 5
assert (java / "secure/P39ReaderTodayHome.kt").exists()  # kept, not deleted
assert not list(root.rglob("*.p12")) and not list(root.rglob("*.jks")) and not list(root.rglob("*.keystore"))
print("P52: compact home cards (4 shown), surah listening with 18 verified reciters, VC74")
