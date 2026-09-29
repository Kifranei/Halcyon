"""Regenerate search destinations from labeled preference rows. Run from repository root.

Only explicit screen/function ownership is used; labels never determine routing.
The generated anchors preserve preference content and are idempotent.
"""
from pathlib import Path
import re

ROOT = Path('app/src/main/java/com/ella/music/ui/settings')
OWNERS = {
 'SettingsAppearance': 'AppearancePage("theme")',
 'SettingsLyrics': 'Lyrics("")', 'SettingsMiniLyrics': 'Lyrics("")',
 'SettingsDesktopLyrics': 'Lyrics("")', 'SettingsLyricDelivery': 'Lyrics("")',
 'SettingsXiaomiSuperIsland': 'Lyrics("")',
 'SettingsAudio': 'Audio("")', 'SettingsBackup': 'Backup("")',
 'CoverMediaSettingsScreen': 'CoverMedia("")',
 'SettingsHomeDisplay': 'HomeDisplay("")',
 'SettingsPreferenceSections': 'Library("")',
 'EqualizerScreen': 'Equalizer("")',
 'LyricFontScreen': 'LyricFont', 'LyricFontComponents': 'LyricFont',
 'LyricPluginSourceSettingsScreen': 'LyricPlugins',
 'BottomNavigationSettingsScreen': 'BottomNavigation',
 'PlayerShortcutSettingsScreen': 'PlayerShortcuts("horizontal")',
 'LastFmSettingsScreen': 'Page("lastfm_settings")',
 'SettingsMaintenanceScreen': 'Maintenance',
 'SettingsAppShortcuts': 'AppearancePage("theme")',
 '../folder/ScanSettingsComponents': 'Scan("")',
}

def masked(text):
    # Keep offsets and line breaks while ignoring quoted delimiters and comments.
    return re.sub(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/',
                  lambda m: re.sub(r'[^\n]', ' ', m.group()), text)

def closing(mask, start):
    stack = []
    for i in range(start, len(mask)):
        c = mask[i]
        if c in '({[': stack.append(c)
        elif c in ')}]':
            stack.pop()
            if not stack: return i + 1
    raise ValueError('Unbalanced Kotlin expression')

def generate():
    definitions = []
    for stem, default in OWNERS.items():
        path = ROOT / (stem + '.kt')
        source = path.read_text(encoding='utf-8-sig')
        # Remove only wrappers emitted by this generator before scanning again.
        source = re.sub(r'(?m)^[ \t]*// search-anchor:start\n[^\n]*\n', '', source)
        source = re.sub(r'(?m)^[ \t]*} // search-anchor:end\n', '', source)
        source = re.sub(r' /\* search-reveal \*/ \|\| SettingsSearchFocus\.reveals\([^)]*\)', '', source)
        if stem.startswith('../') and 'import com.ella.music.ui.settings.SettingsSearchAnchor' not in source:
            source = source.replace('\n\n', '\n\nimport com.ella.music.ui.settings.SettingsSearchAnchor\nimport com.ella.music.ui.settings.SettingsSearchFocus\n', 1)
        mask = masked(source)
        edits = []
        for match in re.finditer(r'\b(?:\w*Preference|SplitSettingTextField|BasicComponent|SuperIslandSpinner|EqControlSlider|BackupExportFormatRow)\s*\(', mask):
            start = match.start()
            if re.search(r'fun\s*$', mask[max(0,start-10):start]): continue
            end = closing(mask, mask.index('(',start))
            args = source[match.end():end-1]
            label = re.search(r'\b(?:title|label)\s*=\s*stringResource\(\s*R\.string\.(\w+)', args)
            if not label: continue
            # Ignore labels belonging to nested content callbacks.
            prefix = masked(args[:label.start()])
            if prefix.count('{') != prefix.count('}'): continue
            tail = re.match(r'\s*\{', mask[end:])
            if tail: end = closing(mask, end + tail.end()-1)
            resource = label[1]
            target, sheet = default, ''
            functions = list(re.finditer(r'\bfun\s+(\w+)\s*\(', mask[:start]))
            function = functions[-1][1] if functions else ''
            if stem == 'SettingsAppearance':
                pages = list(re.finditer(r'if\s*\(page == APPEARANCE_PAGE_(\w+)\)', mask[:start]))
                if pages: target = 'AppearancePage("' + pages[-1][1].lower() + '")'
            if stem == 'SettingsPreferenceSections':
                if function in ('SettingsAiInterpretationSection','SettingsMcpSection','SettingsLastFmSection'):
                    target = 'Integrations("")'
                elif function == 'SettingsLyricShareSection': target = 'Lyrics("")'
                elif function == 'SettingsHomeCustomizeSection': target = 'HomeDisplay("")'
            if stem == 'SettingsHomeDisplay' and function == 'LyricSourcePriorityBlock': target = 'Lyrics("")'
            if stem == 'SettingsLyrics':
                if function in ('SettingsPlayerLyricSizingControls','SettingsPlayerLyricAlignmentPreference'): sheet = 'sizing'
                elif function == 'SettingsPlayerMiniLyricControls': sheet = 'mini'
            if stem == 'SettingsXiaomiSuperIsland': sheet = 'island'
            summary = re.search(r'\bsummary\s*=\s*stringResource\(\s*R\.string\.(\w+)', args)
            definitions.append((resource, summary[1] if summary else None, target, sheet))
            indent = re.match(r'[ \t]*', source[source.rfind('\n',0,start)+1:start])[0]
            edits.append((start, end, indent, resource))
        for start,end,indent,res in reversed(edits):
            newline = '' if source[end:end+1] == '\n' else '\n'
            source = source[:start] + '// search-anchor:start\n' + indent + 'SettingsSearchAnchor(R.string.'+res+') {\n' + indent + source[start:end] + '\n' + indent + '} // search-anchor:end' + newline + source[end:]
        # Search may reveal dependent controls without silently changing their saved prerequisites.
        mask = masked(source)
        reveals = []
        for branch in re.finditer(r'\bif\s*\(', mask):
            condition_end = closing(mask, mask.index('(', branch.start()))
            body_start = condition_end
            while body_start < len(mask) and mask[body_start].isspace(): body_start += 1
            if body_start >= len(mask) or mask[body_start] != '{': continue
            condition = source[branch.end():condition_end-1]
            if re.search(r'\bpage\b|highlight|searchRequest', condition): continue
            body_end = closing(mask, body_start)
            if re.match(r'\s*else\b', mask[body_end:]): continue
            ids = list(dict.fromkeys(re.findall(r'SettingsSearchAnchor\((R\.string\.\w+)\)', source[body_start:body_end])))
            if ids: reveals.append((condition_end-1, ' /* search-reveal */ || SettingsSearchFocus.reveals('+', '.join(ids)+')'))
        for offset, addition in reversed(reveals): source = source[:offset] + addition + source[offset:]
        path.write_text(source, encoding='utf-8')
    unique = list(dict.fromkeys(definitions))
    output = ['package com.ella.music.ui.settings', '', 'import com.ella.music.R', '',
              '// Generated by tools/generate_settings_search.py; destinations follow preference ownership.',
              'internal val generatedSettingsSearchCatalog = listOf(']
    for res,summary,target,sheet in unique:
        output.append(f'    SettingsSearchDefinition(R.string.{res}, '+(f'R.string.{summary}' if summary else 'null')+f', target = SettingsSearchTarget.{target}, sheet = "{sheet}"),')
    output.append(')\n')
    (ROOT/'SettingsSearchGenerated.kt').write_text('\n'.join(output), encoding='utf-8')
    print(f'Indexed {len(unique)} preference destinations')

if __name__ == '__main__': generate()
