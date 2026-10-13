package com.jephte.remoteupdater.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jephte.remoteupdater.R
import com.jephte.remoteupdater.data.AccountStore
import com.jephte.remoteupdater.data.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/* =====================================================================
 * Accueil (premier lancement)
 * ================================================================== */

@Composable
fun WelcomeScreen(onCreate: () -> Unit) {
    val ui = LocalUi.current
    Column(
        Modifier.fillMaxSize().background(ui.hero).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Spacer(Modifier.weight(1f))
        AppLogo(64.dp)
        Spacer(Modifier.height(28.dp))
        Text("Toutes vos mises à jour WordPress, dans votre poche.", color = Color.White, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(28.dp))
        Feature(R.drawable.ic_ui_globe, "Tous vos sites au même endroit", "Connectez chaque site en scannant un QR code.")
        Feature(R.drawable.ic_ui_download, "Mise à jour en un geste", "Extensions, thèmes, WordPress et traductions, avec un suivi en direct.")
        Feature(R.drawable.ic_ui_shield, "Sécurisé", "Compte protégé par mot de passe, code PIN et empreinte.")
        Spacer(Modifier.weight(1f))
        PrimaryButton("Créer mon compte", onCreate, Modifier.fillMaxWidth(), height = 54.dp)
        Spacer(Modifier.height(10.dp))
        Text("Vos données restent sur ce téléphone.", color = ui.heroMuted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Feature(@DrawableRes icon: Int, title: String, text: String) {
    val ui = LocalUi.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 10.dp)) {
        IconTile(icon, ui.heroKey, ui.heroAccent, size = 42.dp)
        Column {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(text, color = ui.heroMuted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

/* =====================================================================
 * Création de compte
 * ================================================================== */

@Composable
fun RegisterScreen(account: AccountStore, onBack: () -> Unit, onDone: () -> Unit) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(ui.bg)) {
        TopBar("Créer votre compte", "Étape 1 sur 2", onBack)
        Column(
            Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Ce compte protège l'accès à vos sites. Il est stocké uniquement sur ce téléphone.", color = ui.textMuted, fontSize = 14.sp, lineHeight = 20.sp)
            UiTextField(name, { name = it }, "Nom", placeholder = "Jephté", leading = R.drawable.ic_ui_user)
            UiTextField(phone, { phone = it }, "Numéro de téléphone", placeholder = "+33 6 12 34 56 78", leading = R.drawable.ic_ui_phone, keyboardType = KeyboardType.Phone)
            UiTextField(password, { password = it }, "Mot de passe", placeholder = "8 caractères minimum", leading = R.drawable.ic_ui_lock, password = true)
            StrengthMeter(password)
            UiTextField(
                confirm, { confirm = it }, "Confirmer le mot de passe", leading = R.drawable.ic_ui_lock, password = true,
                error = if (confirm.isNotEmpty() && confirm != password) "Les mots de passe ne correspondent pas." else null,
            )
            error?.let { ErrorBox(it) }
        }
        Column(Modifier.background(ui.surface).navigationBarsPadding().padding(16.dp)) {
            PrimaryButton(
                "Continuer",
                onClick = {
                    if (password != confirm) {
                        error = "Les mots de passe ne correspondent pas."
                        return@PrimaryButton
                    }
                    busy = true
                    scope.launch {
                        val err = withContext(Dispatchers.Default) { account.register(name, phone, password) }
                        busy = false
                        if (err == null) onDone() else error = err
                    }
                },
                enabled = name.isNotBlank() && phone.isNotBlank() && password.isNotEmpty() && confirm.isNotEmpty(),
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StrengthMeter(password: String) {
    val ui = LocalUi.current
    val s = AccountStore.strength(password)
    val (label, color) = when (s) {
        0, 1 -> "Faible" to ui.danger
        2 -> "Moyen" to ui.warning
        3 -> "Bon" to ui.success
        else -> "Excellent" to ui.success
    }
    if (password.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(4) { i ->
            Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(if (i < s) color else ui.track))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun ErrorBox(text: String) {
    val ui = LocalUi.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ui.dangerSoft).padding(12.dp),
    ) {
        Ico(R.drawable.ic_ui_alert, ui.danger, 18.dp)
        Text(text, color = ui.dangerText, fontSize = 13.sp)
    }
}

/* =====================================================================
 * Connexion (téléphone + mot de passe)
 * ================================================================== */

@Composable
fun LoginScreen(account: AccountStore, pinBlocked: Boolean, onLoggedIn: () -> Unit, onReset: () -> Unit) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf(account.phone) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var forgot by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(ui.bg).imePadding().verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        AppLogo(56.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (account.name.isNotEmpty()) "Bonjour ${account.name}" else "Connexion", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, color = ui.text)
            Text("Connectez-vous avec votre numéro et votre mot de passe.", fontSize = 14.sp, color = ui.textMuted)
        }
        if (pinBlocked) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ui.warningSoft).padding(12.dp),
            ) {
                Ico(R.drawable.ic_ui_lock, ui.warning, 18.dp)
                Text("Trop de codes PIN erronés : reconnectez-vous avec votre mot de passe.", color = ui.warning, fontSize = 13.sp)
            }
        }
        UiTextField(phone, { phone = it }, "Numéro de téléphone", leading = R.drawable.ic_ui_phone, keyboardType = KeyboardType.Phone)
        UiTextField(password, { password = it }, "Mot de passe", leading = R.drawable.ic_ui_lock, password = true)
        error?.let { ErrorBox(it) }
        PrimaryButton(
            "Se connecter",
            onClick = {
                busy = true
                scope.launch {
                    val err = withContext(Dispatchers.Default) { account.login(phone, password) }
                    busy = false
                    if (err == null) onLoggedIn() else {
                        error = err
                        password = ""
                    }
                }
            },
            enabled = phone.isNotBlank() && password.isNotEmpty(),
            loading = busy,
            modifier = Modifier.fillMaxWidth(),
            height = 54.dp,
        )
        Text(
            "Mot de passe oublié ?",
            color = ui.accent,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { forgot = true }.padding(10.dp),
        )
    }

    if (forgot) {
        AlertDialog(
            onDismissRequest = { forgot = false },
            title = { Text("Mot de passe oublié") },
            text = {
                Text(
                    "Votre compte n'existe que sur ce téléphone : il n'y a pas de serveur pour le récupérer.\n\n" +
                        "Vous pouvez réinitialiser l'application : le compte, les sites connectés et l'historique seront effacés. " +
                        "Il faudra ensuite reconnecter vos sites (et révoquer les anciennes connexions dans WordPress).",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    forgot = false
                    onReset()
                }) { Text("Réinitialiser", color = ui.danger) }
            },
            dismissButton = { TextButton(onClick = { forgot = false }) { Text("Annuler") } },
        )
    }
}

/* =====================================================================
 * Code PIN à 4 chiffres
 * ================================================================== */

enum class PinMode { SETUP, UNLOCK, CHANGE }

@Composable
fun PinScreen(
    mode: PinMode,
    lock: AppLock,
    userName: String,
    biometricAvailable: Boolean,
    onBiometric: () -> Unit,
    onDone: () -> Unit,
    onUsePassword: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    val ui = LocalUi.current
    val len = AppLock.PIN_LENGTH
    // 0 = PIN actuel (CHANGE), 1 = nouveau, 2 = confirmation
    var step by remember { mutableIntStateOf(if (mode == PinMode.CHANGE) 0 else 1) }
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var askBio by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (mode == PinMode.UNLOCK && lock.biometricEnabled && biometricAvailable) onBiometric()
    }
    LaunchedEffect(error) {
        if (error != null) for (x in listOf(16f, -14f, 10f, -6f, 3f, 0f)) shake.animateTo(x, tween(45))
    }

    fun submit(v: String) {
        when {
            mode == PinMode.UNLOCK -> if (lock.checkPin(v)) onDone() else {
                pin = ""
                val left = AppLock.MAX_FAILS - lock.failures
                error = if (left > 0) "Code incorrect · $left essai(s) restant(s)" else "Code bloqué"
                if (left <= 0) onUsePassword?.invoke()
            }
            step == 0 -> if (lock.matches(v)) {
                step = 1; pin = ""; error = null
            } else {
                pin = ""; error = "Code actuel incorrect"
            }
            step == 1 -> {
                first = v; step = 2; pin = ""; error = null
            }
            else -> if (v == first) {
                lock.setPin(v)
                if (mode == PinMode.SETUP && biometricAvailable) askBio = true else onDone()
            } else {
                step = 1; pin = ""; error = "Les codes ne correspondent pas. Recommencez."
            }
        }
    }

    val title = when {
        mode == PinMode.UNLOCK -> if (userName.isNotEmpty()) "Bon retour $userName" else "Bon retour"
        step == 0 -> "Code actuel"
        step == 1 -> if (mode == PinMode.SETUP) "Choisissez un code PIN" else "Nouveau code PIN"
        else -> "Confirmez le code"
    }
    val subtitle = when {
        mode == PinMode.UNLOCK -> "Entrez votre code à 4 chiffres"
        mode == PinMode.SETUP && step == 1 -> "Étape 2 sur 2 · pour déverrouiller l'app rapidement"
        step == 2 -> "Saisissez-le une seconde fois"
        else -> "4 chiffres"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().background(ui.hero).statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onCancel != null) GhostIconButton(R.drawable.ic_ui_close, "Annuler", onCancel, tint = Color.White)
        }
        Spacer(Modifier.weight(0.6f))
        AppLogo(64.dp)
        Spacer(Modifier.height(22.dp))
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = ui.heroMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }) {
            repeat(len) { i ->
                val filled = i < pin.length
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(if (filled) Color.White else Color.Transparent)
                        .border(2.dp, if (filled) Color.White else Wp.Gray50, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(error ?: " ", color = Color(0xFFFFABAF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (mode == PinMode.UNLOCK && onUsePassword != null) {
            Text(
                "Se connecter avec le mot de passe",
                color = ui.heroAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onUsePassword).padding(10.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        PinPad(
            showBiometric = mode == PinMode.UNLOCK && lock.biometricEnabled && biometricAvailable,
            onDigit = { d ->
                if (pin.length < len) {
                    pin += d
                    error = null
                    if (pin.length == len) submit(pin)
                }
            },
            onDelete = { pin = pin.dropLast(1) },
            onBiometric = onBiometric,
        )
        Spacer(Modifier.height(20.dp))
    }

    if (askBio) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Déverrouiller avec l'empreinte ?") },
            text = { Text("Vous pourrez ouvrir l'application avec votre empreinte digitale ou votre visage, sans saisir le code.") },
            confirmButton = {
                TextButton(onClick = {
                    lock.biometricEnabled = true
                    askBio = false
                    onDone()
                }) { Text("Activer") }
            },
            dismissButton = {
                TextButton(onClick = {
                    lock.biometricEnabled = false
                    askBio = false
                    onDone()
                }) { Text("Plus tard") }
            },
        )
    }
}

@Composable
private fun PinPad(showBiometric: Boolean, onDigit: (Char) -> Unit, onDelete: () -> Unit, onBiometric: () -> Unit) {
    val ui = LocalUi.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                row.forEach { d -> PinKey({ onDigit(d) }, ui.heroKey) { Text("$d", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            if (showBiometric) PinKey(onBiometric, Color.Transparent) { Ico(R.drawable.ic_ui_fingerprint, ui.heroAccent, 30.dp, "Empreinte digitale") }
            else Spacer(Modifier.size(72.dp))
            PinKey({ onDigit('0') }, ui.heroKey) { Text("0", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold) }
            PinKey(onDelete, Color.Transparent) { Ico(R.drawable.ic_ui_chevron_left, Wp.Gray10, 28.dp, "Effacer") }
        }
    }
}

@Composable
private fun PinKey(onClick: () -> Unit, bg: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(72.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

/* =====================================================================
 * Changement de mot de passe
 * ================================================================== */

@Composable
fun ChangePasswordScreen(account: AccountStore, onBack: () -> Unit, onDone: () -> Unit) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(ui.bg)) {
        TopBar("Mot de passe", "Changer le mot de passe du compte", onBack)
        Column(
            Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UiTextField(current, { current = it }, "Mot de passe actuel", leading = R.drawable.ic_ui_lock, password = true)
            UiTextField(new, { new = it }, "Nouveau mot de passe", leading = R.drawable.ic_ui_key, password = true)
            StrengthMeter(new)
            UiTextField(
                confirm, { confirm = it }, "Confirmer", leading = R.drawable.ic_ui_key, password = true,
                error = if (confirm.isNotEmpty() && confirm != new) "Les mots de passe ne correspondent pas." else null,
            )
            error?.let { ErrorBox(it) }
        }
        Column(Modifier.background(ui.surface).navigationBarsPadding().padding(16.dp)) {
            PrimaryButton(
                "Enregistrer",
                onClick = {
                    if (new != confirm) return@PrimaryButton
                    busy = true
                    scope.launch {
                        val err = withContext(Dispatchers.Default) { account.changePassword(current, new) }
                        busy = false
                        if (err == null) onDone() else error = err
                    }
                },
                enabled = current.isNotEmpty() && new.isNotEmpty() && new == confirm,
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
