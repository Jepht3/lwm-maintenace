package com.jephte.remoteupdater.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.jephte.remoteupdater.MainActivity
import com.jephte.remoteupdater.data.Site
import com.jephte.remoteupdater.data.SiteRepository
import kotlinx.coroutines.launch

/** Connexion d'un nouveau site : QR code, code de connexion ou saisie manuelle. */
@Composable
fun AddSiteScreen(vm: AppViewModel, onBack: () -> Unit, onAdded: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current

    var code by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun connect(site: Site?) {
        if (site == null) {
            error = "Code de connexion invalide."
            return
        }
        busy = true
        error = null
        scope.launch {
            val err = vm.addSite(site)
            busy = false
            if (err == null) {
                vm.notify("${site.name} est connecté.")
                onAdded()
            } else {
                error = err
            }
        }
    }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        (context as? MainActivity)?.expectExternalActivity()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue.orEmpty()
                code = value
                connect(SiteRepository.parseConnectionCode(value))
            }
            .addOnFailureListener { error = "Scanner indisponible : ${it.message}" }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = colors.chipLight,
        focusedContainerColor = colors.chipLight,
        unfocusedBorderColor = Color.Transparent,
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.tint)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onBack)
        Spacer(Modifier.height(6.dp))
        TwoToneHeadline("Connecter", "un site.")
        Text(
            "Sur votre site : Réglages › Remote Updater (Réglages du réseau en multisite), puis « Générer un code de connexion ».",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        // 1. QR code
        SoftCard(onClick = if (busy) null else ::scan) {
            Text("Le plus rapide", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("Scanner le QR code", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("Visez le QR code affiché dans l'administration WordPress.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipPill("Caméra", dark = true)
                Spacer(Modifier.width(8.dp))
                ChipPill("Sans saisie", dark = false)
                Spacer(Modifier.weight(1f))
                ArrowButton(onClick = ::scan, icon = Icons.Rounded.QrCodeScanner, contentDescription = "Scanner le QR code")
            }
        }

        // 2. Code de connexion ou saisie manuelle
        SoftCard {
            Text(if (manual) "Saisie manuelle" else "Code de connexion", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(if (manual) "Adresse et clé" else "Coller le code", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(14.dp))
            if (!manual) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = { Text("wpru:…") },
                    minLines = 3,
                    shape = RoundedCornerShape(20.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("https://monsite.fr") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    placeholder = { Text("Clé d'accès (wpru_…)") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = fieldColors,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipPill(
                    if (manual) "Utiliser un code" else "Saisie manuelle",
                    dark = false,
                    icon = if (manual) Icons.Rounded.ContentPaste else Icons.Rounded.Key,
                    onClick = {
                        manual = !manual
                        error = null
                    },
                )
                Spacer(Modifier.weight(1f))
                val ready = if (manual) url.isNotBlank() && key.isNotBlank() else code.isNotBlank()
                if (busy) SmallSpinner()
                else ArrowButton(
                    onClick = {
                        if (ready) connect(if (manual) SiteRepository.manualSite(url, key) else SiteRepository.parseConnectionCode(code))
                    },
                    contentDescription = "Connecter",
                )
            }
            if (busy) {
                Spacer(Modifier.height(10.dp))
                Text("Connexion au site…", color = MaterialTheme.colorScheme.primary)
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
