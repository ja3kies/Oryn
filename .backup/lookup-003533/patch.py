import re, sys
R = "app/src/main/kotlin/com/example/oryn/reader/"
problems = []

def load(p):
    with open(R + p, encoding="utf-8") as f:
        return f.read()

def save(p, s):
    with open(R + p, "w", encoding="utf-8") as f:
        f.write(s)

# --- PdfReader.kt : deux paramètres + branchement du menu de sélection
p = "ui/screen/PdfReader.kt"
s = load(p)
if "onDefine: ((String) -> Unit)? = null" in s:
    print("= PdfReader.kt déjà branché")
else:
    a = "    onJumpHandled: () -> Unit\n"
    b = "                onDefine = null,\n                onTranslate = null,\n"
    if s.count(a) == 1 and s.count(b) == 1:
        s = s.replace(a, "    onJumpHandled: () -> Unit,\n    onDefine: ((String) -> Unit)? = null,\n    onTranslate: ((String) -> Unit)? = null\n")
        s = s.replace(b, "                onDefine = onDefine?.let { f -> { f(rangeText.trim()) } },\n                onTranslate = onTranslate?.let { f -> { f(rangeText.trim()) } },\n")
        save(p, s)
        print("+ PdfReader.kt branché")
    else:
        problems.append(p)

# --- ReaderScreen.kt : état, callbacks, fiche
p = "ui/screen/ReaderScreen.kt"
s = load(p)
if "LookupSheet(" in s:
    print("= ReaderScreen.kt déjà branché")
else:
    state_re = re.compile(r"^( *)var jumpRequest by remember \{ mutableStateOf<Pair<Int, Int>\?>\(null\) \}\n", re.M)
    cb_re = re.compile(r"^( *)onJumpHandled = \{ jumpRequest = null \}\n", re.M)
    bar_re = re.compile(r"^( *)// Barre du haut\n", re.M)
    if len(state_re.findall(s)) == 1 and len(cb_re.findall(s)) == 2 and len(bar_re.findall(s)) == 1:
        s = state_re.sub(lambda m: m.group(0) + m.group(1) + "// Fiche « Définir / Traduire » ouverte depuis le menu de sélection\n" + m.group(1) + "var lookup by remember { mutableStateOf<LookupRequest?>(null) }\n", s)
        s = cb_re.sub(lambda m: m.group(1) + "onJumpHandled = { jumpRequest = null },\n" + m.group(1) + "onDefine = { lookup = LookupRequest(LookupMode.DEFINE, it) },\n" + m.group(1) + "onTranslate = { lookup = LookupRequest(LookupMode.TRANSLATE, it) }\n", s)
        s = bar_re.sub(lambda m: m.group(1) + "lookup?.let { request -> LookupSheet(request) { lookup = null } }\n\n" + m.group(0), s)
        save(p, s)
        print("+ ReaderScreen.kt branché")
    else:
        problems.append(p)

# --- I18n.kt : entrées anglaises (facultatif : sans elles, l'anglais retombe sur le français)
p = "i18n/I18n.kt"
s = load(p)
entries = {
    "Définition": "Definition", "Traduction": "Translation",
    "Aucune définition trouvée pour « %s ».": "No definition found for “%s”.",
    "Sélectionne un seul mot pour voir sa définition.": "Select a single word to see its definition.",
    "Traduire ce passage": "Translate this passage",
    "Pas de connexion : impossible de joindre le service.": "No connection: couldn't reach the service.",
    "Traduire vers": "Translate to",
    "Ce passage est déjà en %s.": "This passage is already in %s.",
    "Passage coupé : seuls les premiers caractères sont traduits.": "Passage shortened: only the first characters are translated.",
    "Traduction indisponible pour ce passage.": "Translation unavailable for this passage.",
}
todo = {k: v for k, v in entries.items() if '"%s" to' % k not in s}
if not todo:
    print("= I18n.kt déjà à jour")
else:
    m = re.search(r'^( *)"Fermer" to "Close",\n', s, re.M)
    if m:
        add = "".join('%s"%s" to "%s",\n' % (m.group(1), k, v) for k, v in todo.items())
        s = s[:m.end()] + add + s[m.end():]
        save(p, s)
        print("+ I18n.kt : %d entrées ajoutées" % len(todo))
    else:
        print("! I18n.kt : point d'insertion introuvable (traductions anglaises ignorées, sans gravité)")

if problems:
    print("ECHEC : ces fichiers ont changé et n'ont pas pu être branchés automatiquement : " + ", ".join(problems))
    sys.exit(2)
