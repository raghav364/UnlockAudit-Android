#!/usr/bin/env python3
"""Offline structural + SQLite query verification; Android runtime not simulated."""
import re, sqlite3, sys
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parent.parent
app = root/'app/src/main'
checks=[]
def check(name, expr):
    result=bool(expr); checks.append((name,result)); print(('PASS' if result else 'FAIL')+': '+name)

for file in app.rglob('*.xml'):
    ET.parse(file)
check('Android manifest and resource XML valid', True)
manifest=(app/'AndroidManifest.xml').read_text()
check('same package ID', 'com.raghava.unlockaudit' in (root/'app/build.gradle.kts').read_text())
check('target API 36', 'targetSdk = 36' in (root/'app/build.gradle.kts').read_text())
check('no INTERNET permission', 'android.permission.INTERNET' not in manifest)
check('no QUERY_ALL_PACKAGES permission', 'android.permission.QUERY_ALL_PACKAGES' not in manifest)
check('cloud backup disabled', 'android:allowBackup="false"' in manifest and 'android:dataExtractionRules' in manifest)
check('user-controlled foreground tracking', 'foregroundServiceType="specialUse"' in manifest and 'ACTION_STOP' in (app/'java/com/raghava/unlockaudit/service/TrackerService.kt').read_text())
check('structured Room database', '@Database(' in (app/'java/com/raghava/unlockaudit/data/AuditDatabase.kt').read_text())
check('idempotent migration key', 'Index(value = ["importKey"], unique = true)' in (app/'java/com/raghava/unlockaudit/data/Entities.kt').read_text())
check('live session idempotency', 'importKey = "live:$start"' in (app/'java/com/raghava/unlockaudit/data/AuditRepository.kt').read_text())
check('all selected v2.0 navigation tabs', all(x in (app/'java/com/raghava/unlockaudit/ui/AuditApp.kt').read_text() for x in ['Home','History','Insights','Settings']))
check('scrollable screens', (app/'java/com/raghava/unlockaudit/ui/AuditApp.kt').read_text().count('LazyColumn(') >= 4)
check('calendar date picker', 'DatePickerDialog(' in (app/'java/com/raghava/unlockaudit/ui/AuditApp.kt').read_text())
check('document-based CSV export', 'ActivityResultContracts.CreateDocument' in (app/'java/com/raghava/unlockaudit/ui/AuditApp.kt').read_text())
check('retention supported', 'supportedDays = setOf(0, 30, 90, 365)' in (app/'java/com/raghava/unlockaudit/domain/RetentionPolicy.kt').read_text())

# Verify actual SQL in DAO using SQLite; two sessions, three app events must not inflate duration.
dao=(app/'java/com/raghava/unlockaudit/data/AuditDao.kt').read_text()
queries=re.findall(r'@Query\("""(.*?)"""\)',dao,re.S)
check('DAO multi-line SQL extracted',len(queries)>=2)
if len(queries)>=2:
    db=sqlite3.connect(':memory:')
    db.execute('CREATE TABLE sessions(id INTEGER PRIMARY KEY, localDate TEXT, startedAt INTEGER, endedAt INTEGER, interrupted INTEGER)')
    db.execute('CREATE TABLE app_events(id INTEGER PRIMARY KEY, sessionId INTEGER, packageName TEXT, appLabel TEXT, occurredAt INTEGER)')
    db.executemany('INSERT INTO sessions VALUES (?,?,?,?,?)', [
        (1,'2026-11-21',1000,61000,0),(2,'2026-11-21',100000,280000,0),
        (3,'2026-11-20',100000,910000,1)])
    db.executemany('INSERT INTO app_events VALUES (?,?,?,?,?)',[
        (1,1,'com.one','One',3000),(2,1,'com.two','Two',4000),
        (3,2,'com.one','One',150000)])
    summary=queries[1]
    check('daily summary groups with correct durations',db.execute(summary,{'date':'','search':''}).fetchall()==[
        ('2026-11-21',2,240000,2),('2026-11-20',1,0,0)])
    check('search filters daily summaries',db.execute(summary,{'date':'','search':'Two'}).fetchall()==[
        ('2026-11-21',1,60000,2)])
    sessions=queries[0]
    check('session search finds only matching sessions', db.execute(sessions,{'date':'2026-11-21','search':'Two'}).fetchall()==[
        (1,'2026-11-21',1000,61000,0)])
    check('percent is a literal search character, not a wildcard',
          db.execute(summary, {'date':'', 'search':'%'}).fetchall() == []
          and db.execute(sessions, {'date':'2026-11-21', 'search':'%'}).fetchall() == [])
    check('underscore is a literal search character, not a wildcard',
          db.execute(summary, {'date':'', 'search':'_'}).fetchall() == [])
    db.execute("INSERT INTO app_events VALUES (4,2,'com.alpha','A_100% apps',200000)")
    check('literal percent and underscore still match real app names',
          db.execute(sessions, {'date':'2026-11-21', 'search':'%'}).fetchall() == [(2,'2026-11-21',100000,280000,0)]
          and db.execute(sessions, {'date':'2026-11-21', 'search':'_'}).fetchall() == [(2,'2026-11-21',100000,280000,0)])
    check('search is case-insensitive and matches partial app labels',
          db.execute(sessions, {'date':'2026-11-21', 'search':'ALPHA'}).fetchall() == [(2,'2026-11-21',100000,280000,0)])
    check('date filter excludes all other dates',
          db.execute(summary, {'date':'2026-11-20', 'search':''}).fetchall() == [('2026-11-20',1,0,0)])

print(f'\n{sum(b for _,b in checks)}/{len(checks)} offline checks passed.')
if not all(b for _,b in checks): sys.exit(1)
