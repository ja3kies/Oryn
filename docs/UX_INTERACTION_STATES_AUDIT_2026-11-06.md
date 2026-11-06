# UX — contrôles, feedback et mouvements (6 novembre 2026)

## Références reçues

- Motion: entrées eases-out, sorties courtes, microdécalages seulement quand utiles ; aucun rebond sur les commandes quotidiennes, respect des réglages Android de réduction des animations.
- Sheet vs modal: sheet pour les actions contextuelles réversibles liées à un écran ; modal pour une décision critique et irréversible en deux actions au maximum.
- Éviter les dark patterns ; déclencher un retour immédiat au clic ; ne jamais montrer des erreurs techniques brutes ; skeletons ressemblant au contenu réel ; bouton d'onboarding stable.

## Trois objectifs de cette passe

**1 — Découvrir : skeletons et erreurs exploitables**
- Avant : un spinner centré pour les recherches Gutenberg, absence d'aperçu de disposition ; exception Java affichée sous le message d'erreur.
- Après : quatre placeholders stables avec silhouette couverture/titre/auteur/métadonnées, utilisant les surfaces Material 3 ; message utilisateur concis et CTA Réessayer / Parcourir Gutenberg, sans classe d'exception ni message backend.
- Ne jamais « inventer » de livre ou afficher un skeleton comme un vrai résultat.

**2 — Import EPUB depuis WebView : retour réel**
- Avant : « fichier détecté » ne signifiait pas fichier importé ; après le démarrage, la bannière disparaissait dès la fin du téléchargement, même si la persistance avait échoué.
- Après : `WebImportPhase.DOWNLOADING`, `IMPORTING`, `SUCCEEDED` (après `repository.addBook`), `FAILED`. L'état reste visible jusqu'à fermeture ; action Réessayer en échec, Voir la bibliothèque en succès. Choix de téléchargement toujours explicite.
- L'UI reconnaît immédiatement l'appui (état optimiste jusqu'à réception de la phase). Aucun affichage de réussite avant écriture en base.
- Retour TalkBack poli sur le statut ; pas d'animation d'entrée/sortie du panneau quand les animations système sont désactivées. Conserver HTTP(S) strict et l'accès externe en secours, sans contournement anti-bot.

**3 — Onboarding : CTA ancré et accessibilité**
- Zone illustration/texte scrollable indépendamment des contrôles ; hauteur illustration bornée ; bouton « Suivant / Commencer » garde sa position en bas, même en police 200 %.
- Conserver les transitions réduites déjà présentes, ne pas ajouter de mouvement cosmétique ni de rebond.

## Déjà conformes, à ne pas casser

- Suppression de livres : `AlertDialog` avec Annuler et Supprimer, et texte décrivant les conséquences ; ne pas la transformer en sheet.
- Reader Sheet : paramètres contextualisés et réversibles ; ne pas les transformer en modal de confirmation.
- Navbar : 4 destinations et bouton `+` séparé. Couleurs Material/Oryn et thèmes lecteur restent distincts.
- Pas de nouveau chargement automatique ou de requête réseau dans la communauté (verrous maintenus).

## Portes de validation

- Tests JVM ciblant les phases + tests UI instrumentés de l'onboarding à 200 % (compilation CI ≠ exécution sur Pixel 9).
- CI complète JVM, lintDebug, lintRelease, assembleDebug, assembleRelease, assembleDebugAndroidTest, vérification verrouillage communauté, APK identité/version/signature.
- Sur Pixel 9 : navigation 4 onglets, recherche classique en chargement/résultat/erreur, import lien EPUB licite success/échec, back navigation, fonts 200 %, thèmes clair/sombre, TalkBack et animations réduites. Capturer les éléments réellement vérifiés et les URL testées.
- Garder la PR en brouillon et aucune fusion sans autorisation.

Rollback : `9fc180d50d8a86a6fc3a818e6b4874c0da4a08a6` (Oryn 1.2.1) ; aucune migration ni suppression de données.
