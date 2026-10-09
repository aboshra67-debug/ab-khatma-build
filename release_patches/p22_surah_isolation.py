from pathlib import Path
root = Path.cwd()
src = root / 'app/src/main/java/com/ab/khatma/ui/screens/QuranScreen.kt'
s = src.read_text(encoding='utf-8')
def once(old, new, label):
    global s
    assert s.count(old) == 1, (label, s.count(old))
    s = s.replace(old, new, 1)
once('import androidx.compose.foundation.lazy.LazyColumn\n', 'import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.rememberLazyListState\n', 'import lazy state')
once('''                        pages = 1..604,
                        startPage = aya.page,
                        focusAyah = aya.surah to aya.ayah,''', '''                        pages = repo.surahPageRange(aya.surah),
                        surah = aya.surah,
                        startPage = aya.page,
                        focusAyah = aya.surah to aya.ayah,''', 'search result scoped target')
once('''    val currentSurah = repo.pageSurah(currentPage)
    val progress''', '''    // P22: use the selected surah instead of page-first surah.
    val currentSurah = target.surah ?: repo.pageSurah(currentPage)
    val progress''', 'reader header')
once('''                        ayahs = repo.loadPage(pageNumber),
                        repo = repo,''', '''                        // Physical Madina pages may contain adjacent surahs.
                        ayahs = repo.loadPage(pageNumber).let { pageAyahs ->
                            target.surah?.let { selectedSurah ->
                                pageAyahs.filter { it.surah == selectedSurah }
                            } ?: pageAyahs
                        },
                        repo = repo,''', 'filter on shared page')
once('''                val targetAyahs = remember(target) {
                    pages.flatMap { repo.loadPage(it) }
                        .filter { aya ->
                            when {
                                target.juz != null -> aya.juz == target.juz
                                target.surah != null -> aya.surah == target.surah
                                else -> true
                            }
                        }
                        .distinctBy { "${it.surah}:${it.ayah}" }
                }
                LazyColumn(
''', '''                val targetAyahs = remember(target) {
                    // Surah view is isolated from preceding and following surahs.
                    target.surah?.let(repo::loadSurah)
                        ?: pages.flatMap { repo.loadPage(it) }
                            .filter { target.juz == null || it.juz == target.juz }
                            .distinctBy { "${it.surah}:${it.ayah}" }
                }
                val ayahListState = rememberLazyListState()
                // Jump to searched verse also in flexible text mode.
                LaunchedEffect(target.key, target.focusAyah) {
                    target.focusAyah?.let { (surah, ayah) ->
                        val focusIndex = targetAyahs.indexOfFirst {
                            it.surah == surah && it.ayah == ayah
                        }
                        if (focusIndex >= 0) ayahListState.scrollToItem(focusIndex)
                    }
                }
                LazyColumn(
                    state = ayahListState,
''', 'flexible view and focus')
src.write_text(s, encoding='utf-8')
gradle = root / 'app/build.gradle.kts'
g = gradle.read_text(encoding='utf-8')
assert g.count('versionCode = 37') == 1
assert g.count('versionName = "0.19.13-p21-search-ui"') == 1
g = g.replace('versionCode = 37', 'versionCode = 38').replace('versionName = "0.19.13-p21-search-ui"', 'versionName = "0.19.14-p22-surah-isolated"')
gradle.write_text(g, encoding='utf-8')
verify = root / 'tools/verify_project.py'
v = verify.read_text(encoding='utf-8')
for old, new in [('versionCode 37', 'versionCode 38'), ('versionCode = 37', 'versionCode = 38'), ('versionName 0.19.13-p21-search-ui', 'versionName 0.19.14-p22-surah-isolated'), ('versionName = "0.19.13-p21-search-ui"', 'versionName = "0.19.14-p22-surah-isolated"')]:
    assert old in v, old
    v = v.replace(old, new)
verify.write_text(v, encoding='utf-8')
print('P22 SAFE PATCH: only QuranScreen.kt, app/build.gradle.kts, tools/verify_project.py; zero deletions')
