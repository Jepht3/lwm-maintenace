package com.jephte.remoteupdater.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.jephte.remoteupdater.MainActivity
import com.jephte.remoteupdater.R
import com.jephte.remoteupdater.data.Site
import com.jephte.remoteupdater.data.SiteRepository
import kotlinx.coroutines.launch

@Composable
fun AddSiteScreen(vm: AppViewModel, onBack: () -> Unit, onAdded: () -> Unit) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val folders by vm.folders.collectAsState()
    var folderId by remember { mutableStateOf<String?>(null) }

    fun connect(site: Site?) {
        if (site == null) {
            error = "Code de connexion invalide. Copiez-le en entier depuis WordPress."
            return
        }
        busy = true
        error = null
        scope.launch {
            val err = vm.addSite(site, folderId)
            busy = false
            if (err == null) {
                vm.notify("Site connecté")
                onAdded()
            } else error = err
        }
    }

    fun scan() {
        (context as? MainActivity)?.expectExternalActivity()
        GmsBarcodeScanning.getClient(context, GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
            .startScan()
            .addOnSuccessListener { b ->
                code = b.rawValue.orEmpty()
                connect(SiteRepository.parseConnectionCode(code))
            }
            .addOnFailureListener { error = "Scanner indisponible : ${it.message}" }
    }

    Column(Modifier.fillMaxSize().background(ui.bg)) {
        TopBar("Connecter un site", null, onBack)
        Column(
            Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "Dans WordPress, ouvrez Réglages › Remote Updater (Réglages du réseau en multisite) puis touchez « Générer un code de connexion ».",
                fontSize = 14.sp, lineHeight = 21.sp, color = ui.textMuted,
            )

            if (folders.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dossier client", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ui.text)
                    FilterPills(
                        listOf("Sans dossier") + folders.map { it.name },
                        folders.indexOfFirst { it.id == folderId } + 1,
                        { folderId = if (it == 0) null else folders[it - 1].id },
                    )
                }
            }

            // Viseur QR (fond uni foncé)
            Column(
                Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(24.dp)).background(ui.hero).clickable(enabled = !busy) { scan() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val corner = ui.heroAccent
                Box(
                    Modifier.size(150.dp).drawBehind {
                        val l = 30.dp.toPx(); val w = 4.dp.toPx()
                        listOf(
                            Offset(0f, 0f) to Pair(Offset(l, 0f), Offset(0f, l)),
                            Offset(size.width, 0f) to Pair(Offset(size.width - l, 0f), Offset(size.width, l)),
                            Offset(0f, size.height) to Pair(Offset(l, size.height), Offset(0f, size.height - l)),
                            Offset(size.width, size.height) to Pair(Offset(size.width - l, size.height), Offset(size.width, size.height - l)),
                        ).forEach { (c, ends) ->
                            drawLine(corner, c, ends.first, w, StrokeCap.Round)
                            drawLine(corner, c, ends.second, w, StrokeCap.Round)
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { if (busy) Spinner(ui.heroAccent, 32.dp) else Ico(R.drawable.ic_ui_qr, ui.heroMuted, 56.dp) }
                Text(if (busy) "Connexion au site…" else "Touchez pour scanner le QR code", color = ui.heroMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
            }
            PrimaryButton("Scanner le QR code", { scan() }, Modifier.fillMaxWidth(), icon = R.drawable.ic_ui_scan, enabled = !busy, height = 54.dp)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(1.dp).background(ui.border))
                Text("OU", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ui.textSubtle)
                Box(Modifier.weight(1f).height(1.dp).background(ui.border))
            }

            if (!manual) {
                UiTextField(
                    code, { code = it; error = null }, "Code de connexion", placeholder = "wpru:eyJ2Ijox…", mono = true,
                    trailing = {
                        Text(
                            "Coller", color = ui.onAccentSoft, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(10.dp)).background(ui.accentSoft)
                                .clickable { clipboard.getText()?.text?.let { code = it.trim() } }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    },
                )
                PrimaryButton("Connecter", { connect(SiteRepository.parseConnectionCode(code)) }, Modifier.fillMaxWidth(), enabled = code.isNotBlank(), loading = busy)
            } else {
                UiTextField(url, { url = it }, "Adresse du site", placeholder = "https://monsite.fr", leading = R.drawable.ic_ui_globe, keyboardType = KeyboardType.Uri)
                UiTextField(key, { key = it }, "Clé d'accès", placeholder = "wpru_…", leading = R.drawable.ic_ui_key, password = true)
                PrimaryButton("Connecter", { connect(SiteRepository.manualSite(url, key)) }, Modifier.fillMaxWidth(), enabled = url.isNotBlank() && key.isNotBlank(), loading = busy)
            }
            error?.let { ErrorBox(it) }
            Text(
                if (manual) "Utiliser un code de connexion" else "Saisir l'adresse et la clé manuellement",
                color = ui.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { manual = !manual; error = null }.padding(10.dp),
            )
            Box(Modifier.navigationBarsPadding())
        }
    }
}
