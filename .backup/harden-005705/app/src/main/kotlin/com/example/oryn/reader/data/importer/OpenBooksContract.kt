package com.example.oryn.reader.data.importer

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/** Sélecteur de livres qui demande aussi l'accès en écriture, pour pouvoir modifier la couverture d'un EPUB. */
class OpenBooksContract : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
}
