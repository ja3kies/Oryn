# Parcours de lecture et boucle de vérification

Point de départ : Oryn 1.2.7, commit 5e0e82a. La [CI Android](https://github.com/ja3kies/Oryn/actions/runs/45812734039) et le [scénario de capture](../../scripts/capture-reading-journey.py) documentent le parcours nominal. Les points « à vérifier » sont des questions ouvertes.

| Moment | Ce que veut le lecteur | Manque observé ou preuve à obtenir |
|---|---|---|
| Première ouverture | Commencer une lecture depuis un accueil vide | Onboarding et accueil capturés. À vérifier avec TalkBack : trouver Importer dès le premier passage. |
| Import | Ajouter ses livres et identifier ceux qui sont prêts | Premier livre capturé. Un lot mêlant livre valide, doublon et fichier illisible mérite une preuve du résultat individuel. |
| Bibliothèque et favoris | Retrouver un titre, ouvrir sa fiche, le garder | Un favori conserve un livre. Il manque un marque-page autonome pour retrouver une page sans créer de note. |
| Découverte | Trouver un livre disponible, consulter la source, télécharger | L’écran est capturé. Vérifier la continuité entre téléchargement interrompu, nouvel essai et présence d’un seul exemplaire lisible. |
| Lecture et réglages | Lire, révéler la pilule, ajuster le texte, revenir au passage | Reprise EPUB/TXT testée sur émulateur. La même preuve reste à établir pour un PDF passant du rendu fixe au texte redistribué. |
| Sélection et annotation | Sélectionner « On », copier ou annoter exactement les deux lettres | Décalage visible dans la capture du Pixel. La correspondance entre poignée, surbrillance et presse-papiers reste à démontrer sur cet exemple. |
| Définition | Comprendre un mot sans perdre le fil | Vérifier « On » en contexte français avec interface anglaise : langue source, définition anglaise et exemple identifiable. Vérifier aussi source indisponible. |
| Reprise | Fermer, revenir par l’accueil et retrouver sa phrase et ses notes | Reprise de passage capturée. Reste à associer une preuve de persistance de la sélection annotée après changement de police et redémarrage. |

## Boucle prioritaire prête à lancer

/goal Dans le lecteur EPUB d’Oryn, un appui long sur chacune des deux lettres de « On ajoutait » sélectionne exactement « On » en texte italique justifié. Les poignées encadrent le même intervalle que la surbrillance. Copier rend exactement les deux caractères. Après annotation, changement de taille et réouverture, le passage annoté reste « On ». Fournir la capture du rendu, les bornes de sélection, la valeur copiée et le résultat du test Android pour le même commit. Préserver les annotations existantes, le geste de page et la Reader Pill. Arrêter après huit tours ou sur blocage externe documenté.

À chaque tour, reproduire sur un EPUB minimal contenant la phrase, confronter le texte rendu aux bornes et au contenu copié, corriger la première divergence puis rejouer le même scénario. Soumettre les preuves à un vérificateur indépendant. Consigner son verdict « atteint », « à reprendre » ou « bloqué », le commit et la preuve manquante. Si l’appareil signalé est inaccessible, distinguer le résultat de l’émulateur de la confirmation Pixel.

État initial : anomalie signalée sur Pixel, confirmation du correctif ouverte. La CI 1.2.7 portait notamment sur la reprise de lecture. Aucun nouveau résultat de sélection n’est revendiqué.

## Références d’interaction

- [Guide de boucle](https://saas-builder-ia.vercel.app/loop/guide/) : objectif unique, preuves explicites, évaluation et limite de tours.
- [Evil Buttons](https://evilbuttons.com/) : adapter les états copier, copié et échec au résultat réel.
- [SwiftPieces](https://github.com/Saivion/SwiftPieces) : vérifier les transitions de la pilule avec mouvement réduit et texte agrandi.
- [Kitchen](https://dqnamo.com/kitchen) : réserver le retour tactile aux changements d’état utiles, par exemple la prise d’une poignée.
