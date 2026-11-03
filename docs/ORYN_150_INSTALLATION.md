# Oryn Reader 1.5.0 — installation Android

## APK et signature

Télécharger l'artefact `oryn-debug-apk` du workflow Android CI associé à la branche `feat/oryn-reader-150`. Vérifier dans les logs CI les contrôles `PACKAGE_AND_SIGNATURE=VERIFIED` et `versionName='1.5.0'`. Un artefact compilé ne prouve pas une installation réussie.

Le certificat debug persistant attendu a pour empreinte SHA-256 :
`0a04ec768a45ed39a4d04a441aed80e93277006277ee89d58c8c1437fd61f205`.

## Cas de l'ancienne application

L'APK 1.0 initial était signé avec une clé différente. Android interdit sa mise à jour en place avec le nouveau certificat. **Ne pas modifier le package ID ni désactiver les contrôles de signature.**

Avant toute désinstallation, sauvegarder les données importantes via les fonctions d'export de l'application si disponibles et conserver les fichiers EPUB/PDF originaux hors de l'espace privé de l'application. La désinstallation peut supprimer les données internes (bibliothèque locale, préférences et progression).

Pour une installation propre : désinstaller explicitement l'ancienne application depuis les paramètres Android, puis ouvrir le nouvel APK depuis le gestionnaire de fichiers et autoriser cette source si nécessaire. Si le système refuse, relever **le message Android exact** avant toute nouvelle opération.

## Qualification

- Build JVM et signature : uniquement PASS après exécution de la CI et inspection des preuves.
- Installation sur téléphone réel : BLOCKED tant qu'elle n'est pas effectuée.
- Lancement et parcours de lecture : NOT_RUN tant qu'ils ne sont pas effectués.
- Les futures mises à jour debug doivent réutiliser le même certificat et un versionCode croissant.

## Rollback

La branche de référence `feature/community-epub-catalog` n'est pas modifiée. La PR de qualification reste en brouillon. Pour revenir au code antérieur, utiliser l'APK de référence **uniquement** si sa signature correspond à l'application installée ; sinon, une réinstallation peut être nécessaire et entraîner une perte des données internes.
