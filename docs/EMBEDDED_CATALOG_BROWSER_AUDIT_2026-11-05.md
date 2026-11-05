# Audit — navigation intégrée des catalogues et détection EPUB

**Date :** 5 novembre 2026  
**Référence rollback :** `2223f6c4767247abf4817318dfdcc04f8d76655a` (Oryn 1.2.0, PR #11).  
**Version de correction :** Oryn 1.2.1 / `versionCode=5`.

## Cause racine

`ExploreScreen` dirigeait la plupart des catalogues vers `WebLibraryDialog`, mais la carte Anna's Archive avait un branchement spécifique `ACTION_VIEW` vers le navigateur externe. L'utilisateur demande un navigateur **intégré** pour toutes les sources, Anna comprise.

`WebLibraryDialog` détectait aussi un lien EPUB/PDF en affichant « Fichier détecté » alors que **rien n'avait été téléchargé**. Le téléchargement n'était lancé qu'après appui sur « Ajouter à la bibliothèque ». Le texte prêtait donc à confusion.

## Correctifs

- Tous les liens de cartes (sources ouvertes, classiques et index externes) suivent `openSource` → `WebLibraryDialog`. Anna reste classée dans « Index externes · droits à vérifier » ; la distinction juridique n'est pas supprimée.
- L'interface annonce « Lien de fichier détecté · pas encore téléchargé », puis « Télécharger et ajouter » et « Téléchargement et import… ». L'utilisateur garde le contrôle d'un téléchargement non sollicité. Aucun import automatique depuis une page web non fiable.
- Durcissement du navigateur partagé : JavaScript activé pour la compatibilité des catalogues, mais accès aux fichiers locaux et accès inter-origines depuis des fichiers désactivés, contenu mixte interdit, navigation hors HTTP(S) refusée, HTTPS exigé pour les EPUB/PDF à télécharger, Safe Browsing Android activé lorsqu'il est disponible.
- Un simple sous-cadre (iframe) ne déclenche plus la détection d'un lien livre : seules les navigations principales GET et les téléchargements remontés par `DownloadListener` sont éligibles.
- En cas de mort du moteur WebView, fermer le panneau et signaler le plantage ; ne pas réutiliser un renderer mort. Le bouton existant « Ouvrir dans le navigateur » reste accessible en cas de refus HTTP ou de protections anti-robots.

## Ce qui n'est PAS vérifié

- Le bon fonctionnement réseau **de chaque catalogue** sur Pixel 9 et sur les autres réseaux. Une source peut refuser Android WebView (HTTP 403, contrôle anti-robot, JavaScript incompatible). Le fait d'afficher l'URL dans WebView ne prouve pas qu'elle se charge.
- Le téléchargement et l'import de chaque EPUB/PDF : les URL peuvent expirer, nécessiter authentification, rediriger, retourner du HTML ou contenir un fichier non pris en charge. `GutendexClient.downloadFromWeb` applique des contrôles HTTPS, redirection, taille, contenu et format ; les erreurs restent à tester sur appareil.
- Un ouvrage indexé par Anna's Archive n'est pas présumé libre de droits. Aucun système de contournement anti-bot, fausse connexion ni désactivation des contrôles de sécurité n'a été ajouté.

## Matrice de vérification physique obligatoire

- Depuis Découvrir → Catalogues, ouvrir au minimum DOAB, OAPEN, Open Library, Gutenberg, Gallica, Wikisource, puis Anna's Archive. Vérifier le chargement en WebView, l'erreur et le bouton navigateur externe si nécessaire.
- Un EPUB libre de droits (par exemple Gutenberg/Standard Ebooks) : toucher un lien → vérifier « pas encore téléchargé » → cliquer « Télécharger et ajouter » → vérifier la progression, le format, l'entrée en bibliothèque et la lecture. Tester un lien indisponible/403 et confirmer qu'aucun livre fictif n'est ajouté.
- Vérifier les retours, téléchargements interrompus, doublons, rotations, accents et thèmes, grand texte et TalkBack ; enregistrer les URL et réponses problématiques sans exposer de cookies ni de secrets.
- CI: `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:lintRelease`, APK debug/release, compilation des tests instrumentés, verrous réseau communautaires, package, version et certificat de signature. **Compilation ≠ validation WebView sur appareil**.

La PR doit rester en brouillon et aucune branche stable ne doit être fusionnée avant retour et validation de l'utilisateur.
