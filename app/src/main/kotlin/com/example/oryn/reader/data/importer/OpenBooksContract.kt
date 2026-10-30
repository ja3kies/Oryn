package com.example.oryn.reader.data.importer

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Sélecteur de livres en lecture seule.
 *
 * Les couvertures sont stockées dans le stockage privé d'Oryn (CoverStore),
 * pas dans le fichier EPUB source : aucune raison de demander l'accès en
 * écriture à un document utilisateur quelconque (photo, PDF, TXT…).
 * EPUB, PDF et TXT restent immuables une fois importés.
 */
class OpenBooksContract : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
}
