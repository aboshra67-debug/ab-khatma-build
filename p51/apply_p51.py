"""P51 safe, allowlisted patch applied AFTER P48/P49 and P50, without touching home modules below cards."""
from pathlib import Path
import sys

root=Path(sys.argv[1])
base=root/"app/src/main/java/com/ab/khatma/secure"

def patch(path,old,new):
    contents=path.read_text(encoding="utf-8")
    assert contents.count(old)==1,(str(path),contents.count(old),old[:80])
    path.write_text(contents.replace(old,new,1),encoding="utf-8")

gradle=root/"app/build.gradle.kts"
patch(gradle,"versionCode = 72","versionCode = 73")
patch(gradle,'versionName = "0.19.45-p50-leader-approved-plan"',
             'versionName = "0.19.46-p51-home-legacy-schedule"')

api=base/"SecureV2Api.kt"
patch(api,
    'doneCount = o.optInt("completed_count", 0), totalCount = o.optInt("total_count", 0)',
    '''doneCount = o.optInt("completed_count", 0), totalCount = o.optInt("total_count", 0),
                leaderName = o.optString("leader_name").takeIf { it.isNotBlank() && it != "null" }''')
patch(api,
    '''val doneCount: Int, val totalCount: Int)''',
    '''val doneCount: Int, val totalCount: Int, val leaderName: String? = null)''')

ward=base/"P39ReaderTodayHome.kt"
patch(ward,
    '''    onConfirm: (SecureV2Api.TodayItem) -> Unit,
)''',
    '''    onConfirm: (SecureV2Api.TodayItem) -> Unit,
    maxPreview: Int = Int.MAX_VALUE,
    viewerName: String = "",
    onShowAll: () -> Unit = {},
)''')
patch(ward,
    '''        readerWards.forEach { ward ->''',
    '''        readerWards.take(maxPreview).forEach { ward ->''')
patch(ward,
    '''                            Text("وردك اليوم: ''',
    '''                            val name = if (ward.role == "leader" && viewerName.isNotBlank())
                                    viewerName else ward.leaderName ?: "لم يرسله السيرفر بعد"
                            Text("القائد: " + name, color = Color(0xFF596D62),
                                style = MaterialTheme.typography.bodySmall)
                            Text("وردك اليوم: ''')
patch(ward,
    '''        }
    }
    }
}''',
    '''        }
        if (readerWards.size > maxPreview) {
            OutlinedButton(onClick = onShowAll, modifier = Modifier.fillMaxWidth()) {
                Text("عرض كل ختماتي (" + readerWards.size + ")")
            }
        }
    }
    }
}''')

ui=base/"SecureV2Screen.kt"
patch(ui,
    '''P50LeaderSchedule(token = active.token, groupId = selectedGroupId,
                                isLeader = chosen.role == "leader",
                                groupName = chosen.groupName, onRead = { onOpenQuran(it) },
                                onChanged = { reload() })''',
    '''P51ScheduleBridge(token = active.token, groupId = selectedGroupId,
                                isLeader = chosen.role == "leader",
                                groupName = chosen.groupName, todayAssignment = chosen,
                                onRead = { onOpenQuran(it) },
                                onChanged = { reload() },
                                onOpenPagePlans = { section = "plans"; chatGroupId = null })''')
patch(ui,
    '''                                    assignments = groups,
                                    busy = busy,''',
    '''                                    assignments = groups,
                                    maxPreview = 2,
                                    viewerName = active.userName,
                                    onShowAll = { section = "allwards"; message = "" },
                                    busy = busy,''')
# Add a dedicated list that uses exactly the preexisting P39 ward cards. No home sections touched.
patch(ui,
    '''                    } else if (section == "khatmas") {
                        P25GroupsHub(''',
    '''                    } else if (section == "allwards") {
                        Text("كل ختماتي اليوم", style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary)
                        P39ReaderTodayHome(assignments = groups, busy = busy,
                            viewerName = active.userName,
                            onRead = { ward ->
                                if (ward.juz != null) onOpenQuran(ward.juz)
                                else {
                                    selectedGroupId = ward.groupId
                                    section = "schedule"
                                    chatGroupId = null
                                }
                            },
                            onConfirm = { ward -> confirmTodayAssignment = ward })
                    } else if (section == "khatmas") {
                        P25GroupsHub(''')
# The nav bar header must explain the new standalone page.
patch(ui,
    '''"khatmas" -> "الختمات والمجموعات"''',
    '''"khatmas" -> "الختمات والمجموعات"
                                "allwards" -> "كل ختماتي اليوم"''')
# The usual back path defaults to home; no change to lower screens or bottom navigation.

target=base/"P51ScheduleBridge.kt"
assert not target.exists()
target.write_bytes((Path(__file__).parent/"P51ScheduleBridge.kt").read_bytes())
assert len(list((root/"app/src/main/assets/quran_pages").glob("*.webp")))==604
assert not list(root.rglob("*.p12")) and not list(root.rglob("*.jks"))
print("P51: home top only two cards, separate all-wards view, V4 or saved P35/P49 schedule fallback, unchanged lower home modules")
