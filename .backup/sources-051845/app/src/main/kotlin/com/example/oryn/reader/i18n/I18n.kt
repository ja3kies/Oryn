package com.example.oryn.reader.i18n

import android.content.Context
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

/** Choix de langue de l'application : suit le système, ou force le français / l'anglais. */
enum class AppLanguage(val code: String) {
    SYSTEM("system"), FRENCH("fr"), ENGLISH("en");

    companion object {
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/**
 * Langue courante. `mode` est un état Compose : tout écran qui appelle [tr] / [trf] (ou le `Text` de ce paquet)
 * se recompose tout seul quand l'utilisateur change de langue. Le choix est mémorisé dans SharedPreferences,
 * lisible tout de suite au démarrage (pas de flash dans la mauvaise langue).
 */
object Lang {
    private const val PREFS = "oryn_language"
    private const val KEY = "mode"

    var mode by mutableStateOf(AppLanguage.SYSTEM)
        private set

    fun load(context: Context) {
        mode = AppLanguage.fromCode(
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        )
    }

    fun set(context: Context, language: AppLanguage) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, language.code).apply()
        mode = language
    }

    /** Vrai si l'interface doit s'afficher en anglais (« Système » : anglais dès que le téléphone n'est pas en français). */
    val isEnglish: Boolean
        get() = when (mode) {
            AppLanguage.ENGLISH -> true
            AppLanguage.FRENCH -> false
            AppLanguage.SYSTEM -> Locale.getDefault().language != "fr"
        }

    /** Locale à utiliser pour formater nombres et dates. */
    val locale: Locale get() = if (isEnglish) Locale.ENGLISH else Locale.FRENCH
}

/** Traduit un texte écrit en français ; renvoie le texte tel quel s'il est inconnu ou si l'interface est en français. */
fun tr(fr: String): String = if (Lang.isEnglish) Dictionary[fr] ?: fr else fr

/** Comme [tr] pour un modèle contenant des « %s » remplacés dans l'ordre par [args] (le « % » seul reste intact). */
fun trf(fr: String, vararg args: Any?): String {
    val pattern = tr(fr)
    val out = StringBuilder(pattern.length + 16)
    var i = 0
    var a = 0
    while (i < pattern.length) {
        if (pattern.startsWith("%s", i) && a < args.size) {
            out.append(args[a++].toString()); i += 2
        } else {
            out.append(pattern[i]); i++
        }
    }
    return out.toString()
}

/** Remplace `androidx.compose.material3.Text` : traduit automatiquement les textes connus. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    androidx.compose.material3.Text(
        text = tr(text), modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
        textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    androidx.compose.material3.Text(
        text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
        textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines, inlineContent = inlineContent,
        onTextLayout = onTextLayout, style = style
    )
}

private val Dictionary: Map<String, String> by lazy {
    mapOf(
        "Accueil" to "Home",
        "Bibliothèque" to "Library",
        "Favoris" to "Favorites",
        "Réglages" to "Settings",
        "Importer un fichier" to "Import a file",
        "Ajouter un document" to "Add a document",
        "Saluer la mascotte" to "Wave at the mascot",
        "Importer un livre" to "Import a book",
        "Effacer" to "Clear",
        "Retour" to "Back",
        "Fermer" to "Close",
        "Annuler" to "Cancel",
        "Supprimer" to "Delete",
        "Enregistrer" to "Save",
        "Ouvrir" to "Open",
        "Modifier" to "Edit",
        "Réessayer" to "Try again",
        "Titre" to "Title",
        "Auteur" to "Author",
        "Description" to "Description",
        "Menu" to "Menu",
        "Bonne nuit" to "Good night",
        "Bonjour" to "Hello",
        "Bon après-midi" to "Good afternoon",
        "Bonsoir" to "Good evening",
        "Ajoute ton premier livre avec le bouton +" to "Add your first book with the + button",
        "Bravo, tu as terminé « %s » !" to "Well done, you finished “%s”!",
        "Tu en étais à %s % de « %s »" to "You were %s% into “%s”",
        "%s livre terminé, %s à découvrir" to "%s book finished, %s to discover",
        "%s livres terminés, %s à découvrir" to "%s books finished, %s to discover",
        "Quelques pages avant de dormir ?" to "A few pages before bed?",
        "Ta bibliothèque t'attend" to "Your library is waiting for you",
        "%s livre dans ta bibliothèque" to "%s book in your library",
        "%s livres dans ta bibliothèque" to "%s books in your library",
        "Catalogues en ligne" to "Online catalogs",
        "Aucun livre" to "No books",
        "Ajoute un EPUB ou un PDF, ou explore les catalogues libres." to "Add an EPUB or a PDF, or explore the free catalogs.",
        "Ajoutés récemment" to "Recently added",
        "PARCOURIR" to "BROWSE",
        "Terminés" to "Finished",
        "Non lus" to "Unread",
        "Reprendre la lecture" to "Resume reading",
        "Afficher les infos du livre" to "Show book info",
        "Continuer la lecture : %s, %s, %s pour cent lu" to "Continue reading: %s, %s, %s percent read",
        "CONTINUER LA LECTURE" to "CONTINUE READING",
        "Retirer des favoris" to "Remove from favorites",
        "Ajouter aux favoris" to "Add to favorites",
        "Progression : %s pour cent" to "Progress: %s percent",
        "Reprendre" to "Resume",
        "Tous" to "All",
        "En cours" to "In progress",
        "Non lu" to "Unread",
        "Terminé" to "Finished",
        "Lus récemment" to "Recently read",
        "Titre (A → Z)" to "Title (A → Z)",
        "Auteur (A → Z)" to "Author (A → Z)",
        "Progression" to "Progress",
        "Grille" to "Grid",
        "Liste" to "List",
        "Annuler la sélection" to "Cancel selection",
        "%s sélectionné" to "%s selected",
        "%s sélectionnés" to "%s selected",
        "Tout sélectionner" to "Select all",
        "Supprimer la sélection" to "Delete selection",
        "%s livre" to "%s book",
        "%s livres" to "%s books",
        " · %s en cours" to " · %s in progress",
        "Trier et filtrer" to "Sort and filter",
        "Passer en affichage %s" to "Switch to %s view",
        "Rechercher dans ta bibliothèque" to "Search your library",
        "Aucun livre ne correspond." to "No matching books.",
        "Tout voir" to "See all",
        "Récents" to "Recent",
        "%s livre mis de côté" to "%s book saved",
        "%s livres mis de côté" to "%s books saved",
        "Aucun favori" to "No favorites",
        "Touche le marque-page d'un livre pour le retrouver ici." to "Tap a book's bookmark to find it here.",
        "Couverture de %s" to "Cover of %s",
        "à l'instant" to "just now",
        "il y a %s min" to "%s min ago",
        "il y a %s h" to "%s h ago",
        "il y a %s j" to "%s d ago",
        "Supprimer ce livre ?" to "Delete this book?",
        "« %s » sera retiré de ta bibliothèque. Tes fichiers importés ne sont pas touchés." to "“%s” will be removed from your library. Your imported files are not touched.",
        "Infos" to "Info",
        "Sélectionner" to "Select",
        "Supprimer de la bibliothèque" to "Remove from library",
        "Changer" to "Change",
        "Pas encore commencé" to "Not started yet",
        "%s % lu" to "%s% read",
        "Recherche du résumé…" to "Looking for the summary…",
        "Aucun résumé disponible pour ce livre (ou pas de connexion pour le chercher)." to "No summary available for this book (or no connection to look for it).",
        "Réduire" to "Show less",
        "Lire la suite" to "Read more",
        "Source : %s" to "Source: %s",
        "Modifier le livre" to "Edit book",
        "  Pas encore noté" to "  Not rated yet",
        " · %s avis" to " · %s ratings",
        "Résumé de l'éditeur (dans l'EPUB)" to "Publisher summary (in the EPUB)",
        "Auteur inconnu" to "Unknown author",
        "Sans titre" to "Untitled",
        "Livre sans titre" to "Untitled book",
        "Essentiels" to "Essentials",
        "Apparence" to "Appearance",
        "Réduire les réglages" to "Collapse settings",
        "Tous les réglages" to "All settings",
        "Sommaire" to "Contents",
        "Recherche" to "Search",
        "Annotations" to "Annotations",
        "Chapitre précédent" to "Previous chapter",
        "Chapitre suivant" to "Next chapter",
        "Diminuer la taille" to "Decrease size",
        "Augmenter la taille" to "Increase size",
        "Réglages de lecture" to "Reading settings",
        "Thème %s" to "%s theme",
        "Taille" to "Size",
        "Interligne" to "Line spacing",
        "Police" to "Font",
        "Texte" to "Text",
        "Aligné à gauche" to "Left-aligned",
        "Centré" to "Centered",
        "Justifié" to "Justified",
        "Marges" to "Margins",
        "Marges étroites" to "Narrow margins",
        "Marges normales" to "Normal margins",
        "Marges larges" to "Wide margins",
        "Défilement" to "Scroll",
        "Mode pages" to "Page mode",
        "Mode défilement" to "Scroll mode",
        "Thème de lecture" to "Reading theme",
        "Accent" to "Accent",
        "Luminosité" to "Brightness",
        "Luminosité automatique" to "Automatic brightness",
        "Diminuer" to "Decrease",
        "Augmenter" to "Increase",
        "Clair" to "Light",
        "Beige" to "Beige",
        "Prune" to "Plum",
        "Noir OLED" to "OLED black",
        "Par défaut" to "Default",
        "Sérif" to "Serif",
        "Sans-serif" to "Sans-serif",
        "Étroites" to "Narrow",
        "Normales" to "Normal",
        "Larges" to "Wide",
        "Scroll" to "Scroll",
        "Pages" to "Pages",
        "À gauche" to "Left",
        "Sombre" to "Dark",
        "Système" to "System",
        "Dynamique" to "Dynamic",
        "Page %s" to "Page %s",
        "Copié" to "Copied",
        "Ouvrir ce lien ?" to "Open this link?",
        "Souligner" to "Underline",
        "Note" to "Note",
        "Étendre" to "Extend",
        "Définir" to "Define",
        "Traduire" to "Translate",
        "Copier" to "Copy",
        "Annotations (%s)" to "Annotations (%s)",
        "Exporter" to "Export",
        "Aucune annotation. Appuie longuement sur un mot dans le livre pour le sélectionner, puis surligne-le, souligne-le ou ajoute une note." to "No annotations. Long-press a word in the book to select it, then highlight it, underline it or add a note.",
        "↩  Retour à ma lecture" to "↩  Back to my reading",
        "Aller au passage" to "Go to passage",
        "Android n'autorise plus l'accès à ce fichier (autorisation expirée ou jamais conservée)." to "Android no longer allows access to this file (permission expired or never kept).",
        "Re-sélectionne le même fichier pour rétablir l'accès : ta progression et tes annotations sont conservées." to "Select the same file again to restore access: your progress and annotations are kept.",
        "Réautoriser le fichier" to "Re-authorize the file",
        "moins d'une minute restante" to "less than a minute left",
        "≈ %s min restantes" to "≈ %s min left",
        "≈ %s h %s restantes" to "≈ %s h %s left",
        "page %s / %s" to "page %s / %s",
        "Ce livre n'a pas de sommaire et aucun titre n'a pu être repéré dans le texte." to "This book has no table of contents and no heading could be found in the text.",
        "Rechercher dans le livre" to "Search in the book",
        "Mot ou expression" to "Word or phrase",
        "Saisis au moins 2 lettres." to "Type at least 2 letters.",
        "Recherche…" to "Searching…",
        "Aucun résultat." to "No results.",
        "200 premiers résultats" to "First 200 results",
        "%s résultat(s)" to "%s result(s)",
        "Thème de l'application" to "App theme",
        "Couleur d'accentuation" to "Accent color",
        "Lecture" to "Reading",
        "Masquer la barre d'état" to "Hide the status bar",
        "Pendant la lecture, pour un affichage plein écran." to "While reading, for a full-screen display.",
        "Orange Pop" to "Orange Pop",
        "Fuchsia Néon" to "Neon Fuchsia",
        "Vert Émeraude" to "Emerald Green",
        "Bleu Océan" to "Ocean Blue",
        "Langue" to "Language",
        "Langue de l'application" to "App language",
        "Français" to "Français",
        "English" to "English",
        "Plus de 88 000 livres académiques en accès ouvert" to "Over 88,000 open-access academic books",
        "Livres scientifiques récents, PDF libres" to "Recent scholarly books, free PDFs",
        "Des livres en communs" to "Des livres en communs",
        "Essais, BD et fictions sous licence libre (Framasoft)" to "Essays, comics and fiction under free licenses (Framasoft)",
        "Millions de titres ; seuls les livres du domaine public sont téléchargeables" to "Millions of titles; only public-domain books can be downloaded",
        "Classiques soigneusement relus et mis en forme" to "Carefully proofread and formatted classics",
        "La bibliothèque numérique de la BnF" to "The BnF digital library",
        "Textes du domaine public en français" to "Public-domain texts in French",
        "Classiques français gratuits" to "Free French classics",
        "Livres, revues et documents numérisés" to "Digitized books, magazines and documents",
        "Le site officiel, plus de 70 000 titres" to "The official site, over 70,000 titles",
        "Toutes" to "All",
        "Impossible de joindre la bibliothèque. Vérifie ta connexion." to "Can't reach the library. Check your connection.",
        "Explorer" to "Explore",
        "Importer depuis l'appareil" to "Import from this device",
        "Catalogues" to "Catalogs",
        "Rechercher un classique" to "Search for a classic",
        "Récents · accès ouvert et licences libres" to "Recent · open access and free licenses",
        "Les romans récents du commerce sont protégés (DRM) : ils ne peuvent pas être ajoutés à l'app." to "Recent commercial novels are DRM-protected: they can't be added to the app.",
        "Classiques · domaine public" to "Classics · public domain",
        "Classiques libres de droits" to "Copyright-free classics",
        "Project Gutenberg · domaine public" to "Project Gutenberg · public domain",
        "Titre ou auteur…" to "Title or author…",
        "Les plus populaires" to "Most popular",
        "Résultats" to "Results",
        "Parcourir Gutenberg" to "Browse Gutenberg",
        "Aucun livre trouvé." to "No books found.",
        "Impossible de charger la suite." to "Couldn't load more.",
        "Chargement…" to "Loading…",
        "Voir plus" to "See more",
        "Déjà dans ta bibliothèque" to "Already in your library",
        "Ajouter à ma bibliothèque" to "Add to my library",
        "Réponse inattendue du serveur" to "Unexpected server response",
        "Fichier incomplet" to "Incomplete file",
        "Catalogue de secours : aucune réponse exploitable" to "Fallback catalog: no usable response",
        "Page précédente" to "Previous page",
        "Recharger" to "Reload",
        "Ouvrir dans le navigateur" to "Open in browser",
        "Impossible de charger cette page. Vérifie ta connexion, puis réessaie." to "Can't load this page. Check your connection, then try again.",
        "Le site limite temporairement les requêtes (trop d'accès en peu de temps). Attends environ 30 secondes, puis réessaie." to "The site is temporarily rate-limiting requests (too many in a short time). Wait about 30 seconds, then try again.",
        "Le site refuse l'accès dans l'application. Essaie de l'ouvrir dans ton navigateur." to "The site refuses access inside the app. Try opening it in your browser.",
        "Cette page n'existe plus." to "This page no longer exists.",
        "Format non pris en charge. Cherche un EPUB ou un PDF." to "Unsupported format. Look for an EPUB or a PDF.",
        "Réessayer (%s s)" to "Try again (%s s)",
        "Fichier détecté" to "File detected",
        "  Ajout…" to "  Adding…",
        "  Ajouter à la bibliothèque" to "  Add to library",
        "URI inaccessible" to "Inaccessible URI",
        "Partage : %s fichier(s) inaccessible(s)" to "Share: %s inaccessible file(s)",
        "%s importé(s)" to "%s imported",
        "%s déjà présent(s)" to "%s already present",
        "%s format(s) non pris en charge" to "%s unsupported format(s)",
        "%s échec(s) d’accès ou d’import" to "%s access or import failure(s)",
        "Aucun livre importé." to "No books imported.",
        "Importation : %s" to "Import: %s",
        "« %s » ajouté" to "“%s” added",
        "Téléchargement impossible. Vérifie ta connexion." to "Download failed. Check your connection.",
        "Format non pris en charge (EPUB, PDF ou TXT)." to "Unsupported format (EPUB, PDF or TXT).",
        "« %s » ajouté à ta bibliothèque" to "“%s” added to your library",
        "Téléchargement impossible. Le site demande peut-être une connexion." to "Download failed. The site may require you to sign in.",
        "Image illisible" to "Unreadable image",
        "Couverture enregistrée aussi dans l’EPUB" to "Cover also saved in the EPUB",
        "Couverture changée dans Oryn, mais l’EPUB n’a pas pu être modifié." to "Cover changed in Oryn, but the EPUB couldn't be modified.",
        "Chemin de fichier invalide." to "Invalid file path.",
        "Le fichier texte est vide ou inaccessible." to "The text file is empty or inaccessible.",
        "Aucun contenu textuel lisible trouvé dans cet EPUB." to "No readable text found in this EPUB.",
        "Le fichier HTML est vide ou inaccessible." to "The HTML file is empty or inaccessible.",
        "Format non supporté pour la lecture directe." to "Unsupported format for direct reading.",
        "Erreur de chargement : %s" to "Loading error: %s",
        "Impossible d'ouvrir le document PDF." to "Can't open the PDF document.",
        "Ce PDF ne contient aucune page." to "This PDF has no pages.",
        "Couverture changée dans Oryn. Cet EPUB est en lecture seule : supprime-le puis réimporte-le pour pouvoir le modifier." to "Cover changed in Oryn. This EPUB is read-only: delete it and re-import it to be able to edit it.",
        "%s livre%s terminé%s, %s à découvrir" to "%s book(s) finished, %s to discover",
        "Page" to "Page",
        "Préparation du texte…" to "Preparing text…",
        "Une seule fois par livre : le résultat est ensuite gardé." to "Only once per book: the result is then kept.",
        "Revenir aux pages" to "Back to pages",
        "Ce PDF ne contient pas de texte exploitable : affichage en pages." to "This PDF has no usable text: showing pages instead.",
        "Aucun texte extractible sur cette page." to "No extractable text on this page.",
        "Mode Page" to "Page mode",
        "Mode Texte" to "Text mode"
    )
}
