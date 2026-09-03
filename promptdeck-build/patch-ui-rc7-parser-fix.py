from pathlib import Path
import re

p = Path('/tmp/pd/PromptDeck/app/src/main/java/com/kareem/promptdeck/MainActivity.java')
s = p.read_text()

# Normalize the three Java-source fragments that can be mangled by nested Python/Java escaping.
s = re.sub(
    r'^\s*String t=prompt\.replaceAll\(.*?\);\s*$',
    '    String t=prompt.replaceAll("\\\\s+"," ").trim();',
    s,
    flags=re.M,
)

s = re.sub(
    r'^\s*java\.util\.regex\.Pattern header=.*?;\s*$',
    '    java.util.regex.Pattern header=java.util.regex.Pattern.compile("^\\\\s*(?:\\\\d+[.)]\\\\s*)?/([A-Za-z0-9_-]+)(?:\\\\s*(?:→|->|—|–|:|\\\\\\\\||=)\\\\s*(.*))?\\\\s*$");',
    s,
    flags=re.M,
)

# Replace the whole continuation clause, even if an earlier patch accidentally split the character literal across lines.
s = re.sub(
    r'\}\s*else if\(currentName!=null\)\{if\(body\.length\(\)>0\)body\.append\(.*?\);body\.append\(line\);\}',
    '}else if(currentName!=null){if(body.length()>0)body.append("\\n");body.append(line);}',
    s,
    flags=re.S,
)

p.write_text(s)
print('PromptDeck RC7 parser escaping fix applied')
