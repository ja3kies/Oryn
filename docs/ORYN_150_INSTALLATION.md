# Oryn — qualification Android de la branche 1.1.0

Finalisation du 4 novembre 2026 sur `feat/oryn-reader-150`, départ vérifié `36d041bf6136863c41450bb06dcf000bb1e4b6eb`. Version conservée : **1.1.0 / code 2** ; package `com.example.oryn.reader`. PR de qualification : https://github.com/ja3kies/Oryn/pull/2 (brouillon, aucune fusion vers une branche stable).

## Périmètre et matrice fonctionnelle

L'inventaire du dépôt, les historiques des PR #3/#4 et la reprise des tests #5 ont été examinés. La CI du point de départ a été vérifiée via l'API : exécution [41768125989](https://github.com/ja3kies/Oryn/actions/runs/41768125989), réussie. Les états ci-dessous décrivent le code et les contrôles locaux ; **aucun parcours sur téléphone n'est déclaré validé**.

| Fonction | État initial constaté | État après corrections et preuve disponible |
| --- | --- | --- |
| Import et bibliothèque EPUB/PDF | Partiel : extension seule, doublons possibles, références partagées fragiles | Fonctionnel côté code : entrée accessible/non vide, signature, structure EPUB, transaction d'import ; tests JVM et scénarios Room préparés |
| Lecture EPUB/TXT, sommaire et préférences | Présents ; cache de pagination réutilisable pour le mauvais document | Corrigé : cache par document/densité, viewport stable, tests de remplacement et restauration préparés |
| Recherche EPUB et PDF en mode texte | Présente ; scans longs non annulables | Fonctionnel sur JVM : Unicode simple, positions UTF-16, limite de résultats, précédent/suivant et bouclage ; scan linéaire annulable |
| PDF natif : rendu, navigation, zoom 1–5×, sélection | Présents ; allocations/cache et fermeture perfectibles, réouverture parfois reculée d'une page | Corrigé côté code : budgets de rendu/cache, résolution du viewport, fermeture hors UI, arrondi de restauration testé ; rendu/gestes à qualifier sur appareil |
| PDF en mode texte | Partiel : croissance non bornée, instantanés et identité de cache insuffisants | Bornes et cache contrôlés ; retour explicite aux pages si indisponible ; extraction PDFBox réelle et rotation encore à qualifier |
| Surlignages, notes, favoris/signets de livres | Présents | Conservés ; favoris atomiques en SQL, mises à jour de chemin ciblées ; qualification des sélections sur appareil restante |
| Signet indépendant par position/page | Absent | Non inventé : le bouton existant reste le favori/signettage du livre ; la progression reste distincte |
| Contrôles immersifs et ReaderSheet | Présents | HIDDEN/COMPACT/EXPANDED conservés, minuteur et progressions distinctes ; scénarios Android disponibles |
| Bibliothèque, clavier, petits écrans, accessibilité | Partiel : sélection périmée, suppression multiple immédiate, clés non sauvegardables | Confirmation et sélection par ID, état écran conservé, clés Long, libellés permanents, cibles 48 dp et couvertures non rognées ; appareil requis pour validation visuelle/TalkBack |
| Room et migrations 1→2→3→4 | Migrations présentes, aucune migration destructive | Schéma/version inchangés ; trois ouvertures historiques réelles préparées en tests instrumentés, non exécutées sans appareil |
| Catalogue, Auth et transferts communautaires | Bloqués volontairement | Toujours bloqués ; aucune configuration fictive, aucun appel ou déploiement de production |

## Correctifs et sécurité

- Les mutations de fichiers et références Room sont coordonnées dans tout le processus, y compris entre activités. Les transferts réseau préparent un fichier privé hors verrou ; seule la publication prend le verrou. Les originaux externes ne sont jamais supprimés. Les annulations libèrent les copies nouvelles sans référence ; les références existantes et fichiers préexistants sont conservés.
- Les couvertures sont écrites dans un fichier temporaire puis publiées entièrement avec un nom unique ; les bitmaps sont libérés. Une couverture partagée ne disparaît plus à la première suppression/remplacement.
- EPUB : limites de décompression appliquées à **toutes** les entrées, même ignorées/drainées implicitement ; chemins ambigus, doublons, noms démesurés et contrôles rejetés. Aucune extraction vers un chemin fourni par l'archive.
- Cookies, Authorization et autres en-têtes sensibles restent limités à l'origine HTTPS initiale lors des redirections Web. Le journal qui révélait le texte sélectionné est retiré.
- Les recherches et les copies vérifient l'annulation ; PDF : cache bitmap de 24 Mio, plafond de 4 millions de pixels par rendu, reflow de 8 millions de caractères, limites de pages/spans et instantanés vérifiés. Ces bornes ne prouvent pas une borne mémoire globale pour les comportements internes de PDFBox.
- Aucun changement de schéma, dépendance runtime, identifiant d'application, clé de signature, permission, backend ou verrou communautaire. Aucun effacement de bibliothèque et aucune désinstallation.

## Validation réellement exécutée

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --continue --console=plain
python3 scripts/check-community-release.py
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```

| Contrôle local final | Résultat |
| --- | --- |
| JVM | **193 découverts : 192 réussis, 0 échec/erreur, 1 ignoré** (sonde Auth isolée sans configuration) |
| Lint debug/release | **0 erreur** ; 93 / 85 avertissements existants, aucune règle désactivée |
| Builds debug/release | Réussis ; release non signée |
| APK de tests Android | Compilée ; **32 scénarios non exécutés**, appareil absent |
| Garde APK communautaire | Réussie : fixtures hors release, callback désactivé sans filtre, permissions/cleartext vérifiés |
| Signature APK locale | `apksigner verify` réussi, certificat local existant conservé |
| Dépendances | Résolution debugRuntimeClasspath réussie, aucune dépendance non résolue, aucune version runtime ajoutée/modifiée |
| Diff, secrets et journaux | `git diff --check` réussi ; aucun motif de clé privée/JWT/clé GitHub-AWS-Supabase secrète dans les fichiers modifiés et journaux de finalisation ; scan limité aux motifs contrôlés |

APK local : 28 265 133 octets ; SHA-256 `40e60ad8a06a3a1e955aa9626b6ed522017b6b2e05680852bf7f7749e60739fa`.
APK release non signée : SHA-256 `279d53bca917209d915a57ec44d2c30faeaa5907c854860969698559bb9af5ad`.
La première compilation du nouveau test ZIP avait révélé deux accès nullables ; ils ont été corrigés et les vérifications relancées, sans suppression de test.

La compilation de l'APK instrumentée n'est **pas** une exécution des tests. Les 32 scénarios Android comprennent lecture/Back, styles, annulations de sélection, restauration, remplacement de document, intégrité de bibliothèque/couvertures et migrations. Aucun appareil ADB, émulateur prêt, domaine HTTPS vérifié ou Supabase isolé prêt n'était disponible. Aucun test SQL/RLS, App Link réel ou Keystore matériel n'est déclaré réussi.

Contrôle différentiel indépendant : **10 000 cas Unicode déterministes réussis** entre le KMP compilé et l'oracle Java `regionMatches(ignoreCase)` sur des textes Unicode valides, avec positions et non-chevauchement comparés. Ce contrôle ne démontre pas le comportement de toutes les séquences UTF-16 malformées.

Mesure synthétique JVM JDK 17, un échantillon après trois échauffements KMP, document de 2 000 000 `a`, requête de 8 192 `a` suivis de `b` : **7,526 ms KMP / 13 558,915 ms ancienne recherche**. Machine partagée avec un build ; aucune promesse de ce gain sur Android, aucune mesure de rendu/consommation Android. Journaux détaillés hors Git : `/tmp/oryn-finalization-*.log`.

La CI vérifie désormais JVM, lint debug/release, APK debug/release, compilation des tests instrumentés, identité/signature et garde des fonctions communautaires. Les événements push/PR d'une même source partagent un groupe de concurrence, sans double validation coûteuse.

## APK, signature et installation sans perte de données

Télécharger `oryn-debug-apk` dans le workflow Android CI correspondant au **SHA exact de la livraison**, ou utiliser le fichier local `app/build/outputs/apk/debug/app-debug.apk`. Le fichier `build-info.txt` de l’artefact CI contient le SHA réellement compilé, le SHA-256 de l’APK et celui du certificat, vérifiés dans les logs. Le SHA du commit et le lien d’exécution sont fournis dans la livraison ; ne pas prendre simplement le dernier artefact d'une autre branche.

Le workflow conserve la clé debug persistante déjà configurée dans GitHub. Empreinte publique attendue du certificat CI : `0a04ec768a45ed39a4d04a441aed80e93277006277ee89d58c8c1437fd61f205`. La compilation locale conserve sa clé debug existante, certificat `7140158e1d6c21835cc39c3c65f35b7a927657defcda54cdeae8260c97f9044a` ; **ces deux signatures diffèrent déjà**. Aucune clé privée n'est copiée dans le dépôt ni changée pour cette mission.

Vérifier le certificat de l'application installée et choisir l'APK compatible. Ne pas désinstaller, changer le package ID ou régénérer une clé pour contourner un refus de mise à jour. En cas de signature incompatible, relever le message Android exact et conserver l'application/données existantes. L'APK release est compilée non signée ; aucune release officielle n'est publiée.

Permissions : `INTERNET` pour les fonctions réseau existantes ; accès aux documents choisis via Storage Access Framework et autorisations URI accordées par l'utilisateur. Aucun accès global au stockage ni nouvelle permission dangereuse. L'application reste utilisable localement sans compte ni backend.

## Qualification sur appareil — restante

- [ ] Installer l'APK compatible sans désinstallation ; lancer hors ligne et importer EPUB/PDF valides, fichier vide, extension trompeuse et document corrompu.
- [ ] Lire, changer de page et défiler, parcourir le sommaire, chercher Unicode/longue requête, précédent/suivant/bouclage, revenir à la lecture ; vérifier les offsets et sélections.
- [ ] Fermer/rouvrir, tuer/recréer l'activité, tourner l'écran ; vérifier la même page PDF, progression EPUB, réglages, notes et favoris.
- [ ] Contrôles compacts/étendus, Back, swipe de feuille, minuteur, clavier, TalkBack, polices agrandies, modes clair/sombre/sépia et paysage : aucun texte masqué.
- [ ] Importer deux références du même contenu ; modifier/enlever une couverture, supprimer un livre avec annulation puis confirmation, vérifier l'autre livre et les originaux.
- [ ] Tests instrumentés : `./gradlew :app:connectedDebugAndroidTest` seulement après `adb devices -l` confirmant un appareil autorisé. Utiliser un profil de test, jamais la bibliothèque de l'utilisateur pour les migrations.
- [ ] Révoquer une URI, retirer un fichier après import ; vérifier l'erreur et la réparation sans perte de progression/favoris.
- [ ] Gros PDF, PDF scanné et remplacement au même URI : navigation/zoom, reflow ou fallback, mémoire et cache ; aucune prétention de qualification sur grand document réel ici.
- [ ] Auth, catalogue et partages restent verrouillés. Toute activation exige un environnement isolé vérifié, App Links HTTPS réellement associés et validations RLS/Storage séparées.

## Problèmes résiduels et retour arrière

- **Qualification bloquante appareil** : gestes, rendu natif, IME, TalkBack et migrations réelles non exécutés. Pas de validation physique revendiquée.
- **Préproduction réseau bloquée** : instance isolée, domaine vérifié et validations serveur absents ; aucune connexion de production autorisée.
- **Limites connues** : recherche native PDF uniquement via son mode texte ; signets indépendants par page absents ; moteurs PDF natif/PDFBox et documents extrêmes à mesurer sur appareil ; avertissements lint conservés et classés dans le rapport de sécurité historique.

Retour arrière du code : référence locale `backup/oryn-finalization-36d041b`, référence distante de départ `36d041bf6136863c41450bb06dcf000bb1e4b6eb`. Créer une branche depuis cette référence ou utiliser des commits `git revert` examinés sur la branche de développement ; jamais de reset destructif ni force-push. Le schéma Room reste v4 et les clés de préférences inchangées. Ne rétrograder l'APK que si Android autorise version et certificat ; conserver les données et relever les refus au lieu de désinstaller.
