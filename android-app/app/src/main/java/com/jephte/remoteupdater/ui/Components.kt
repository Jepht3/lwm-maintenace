package com.jephte.remoteupdater.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jephte.remoteupdater.R

/* ---------- Icônes ---------- */

@Composable
fun Ico(@DrawableRes id: Int, tint: Color, size: Dp = 22.dp, contentDescription: String? = null, modifier: Modifier = Modifier) {
    Icon(painterResource(id), contentDescription, tint = tint, modifier = modifier.size(size))
}

/** Rond de chargement : l'icône « spinner » qui tourne. */
@Composable
fun Spinner(color: Color, size: Dp = 20.dp) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    Icon(painterResource(R.drawable.ic_ui_spinner), null, tint = color, modifier = Modifier.size(size).rotate(angle))
}

/* ---------- Texte ---------- */

val MonoStyle = TextStyle(fontFamily = Mono, fontSize = 12.sp)

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, color = LocalUi.current.text, modifier = modifier)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.6.sp,
        color = LocalUi.current.textSubtle,
        modifier = modifier.padding(start = 4.dp, top = 6.dp),
    )
}

/* ---------- Surfaces ---------- */

@Composable
fun UiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    radius: Dp = 20.dp,
    background: Color? = null,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(radius)
    val m = modifier.fillMaxWidth().border(1.dp, borderColor ?: ui.border, shape)
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = background ?: ui.surface, modifier = m) { Column(content = content) }
    } else {
        Surface(shape = shape, color = background ?: ui.surface, modifier = m) { Column(content = content) }
    }
}

@Composable
fun ListDivider(startIndent: Dp = 0.dp) {
    Box(Modifier.padding(start = startIndent).fillMaxWidth().height(1.dp).background(LocalUi.current.divider))
}

/* ---------- Pastilles, tuiles ---------- */

@Composable
fun StatusPill(text: String, fg: Color, bg: Color, @DrawableRes icon: Int? = null, spinning: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (spinning) Spinner(fg, 12.dp) else if (icon != null) Ico(icon, fg, 13.dp)
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Tuile carrée arrondie avec initiale (ton dérivé du nom) ou icône. */
@Composable
fun LetterTile(name: String, size: Dp = 42.dp, bg: Color? = null, fg: Color? = null) {
    val ui = LocalUi.current
    val (b, f) = avatarTone(name, ui)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.29f)).background(bg ?: b),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = fg ?: f, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.4f).sp)
    }
}

@Composable
fun IconTile(@DrawableRes icon: Int, bg: Color, fg: Color, size: Dp = 40.dp, iconSize: Dp = 20.dp, border: Color? = null) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(size * 0.3f)) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, fg, iconSize) }
}

/* ---------- Progression ---------- */

@Composable
fun ProgressBar(fraction: Float, color: Color, track: Color, height: Dp = 6.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "progress")
    Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(CircleShape).background(color))
    }
}

/** Barre « en cours » : un segment qui glisse en continu (pas de faux pourcentage). */
@Composable
fun IndeterminateBar(color: Color, track: Color, height: Dp = 6.dp) {
    val t = rememberInfiniteTransition(label = "indet")
    val x by t.animateFloat(-0.4f, 1.1f, infiniteRepeatable(tween(1300), RepeatMode.Restart), label = "x")
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        val w = maxWidth
        Box(Modifier.offset(x = w * x).width(w * 0.35f).fillMaxHeight().clip(CircleShape).background(color))
    }
}

/* ---------- Boutons ---------- */

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 52.dp,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Wp.Blue,
            contentColor = Color.White,
            disabledContainerColor = Wp.Blue.copy(alpha = 0.45f),
            disabledContentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp),
        modifier = modifier.height(height),
    ) {
        if (loading) Spinner(Color.White, 18.dp) else if (icon != null) Ico(icon, Color.White, 18.dp)
        if (loading || icon != null) Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    color: Color? = null,
    height: Dp = 46.dp,
    enabled: Boolean = true,
) {
    val ui = LocalUi.current
    val c = color ?: ui.text
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (color != null) color else ui.border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c),
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = modifier.height(height),
    ) {
        if (icon != null) {
            Ico(icon, c, 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Bouton carré 44 dp avec bordure (actions d'en-tête). */
@Composable
fun SquareIconButton(@DrawableRes icon: Int, contentDescription: String, onClick: () -> Unit, badge: Boolean = false) {
    val ui = LocalUi.current
    Box {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            color = ui.surface,
            modifier = Modifier.size(44.dp).border(1.dp, ui.border, RoundedCornerShape(14.dp)),
        ) {
            Box(contentAlignment = Alignment.Center) { Ico(icon, ui.text, 20.dp, contentDescription) }
        }
        if (badge) Box(Modifier.align(Alignment.TopEnd).padding(9.dp).size(8.dp).clip(CircleShape).background(Wp.Red))
    }
}

/** Bouton icône sans fond (barre du haut). */
@Composable
fun GhostIconButton(@DrawableRes icon: Int, contentDescription: String, onClick: () -> Unit, tint: Color? = null) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, tint ?: LocalUi.current.text, 22.dp, contentDescription) }
}

/* ---------- Sélecteurs ---------- */

/** Contrôle segmenté (fond gris, segment actif blanc). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, badges: List<Int> = emptyList()) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (ui.isDark) ui.bg else ui.neutralSoft).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) (if (ui.isDark) ui.border else ui.surface) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
            ) {
                Text(label, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, color = if (active) ui.text else ui.textMuted, maxLines = 1)
                val b = badges.getOrNull(i) ?: 0
                if (b > 0) {
                    Spacer(Modifier.width(5.dp))
                    Text("$b", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = ui.warning)
                }
            }
        }
    }
}

/** Filtres en pilules (actif = gris foncé). */
@Composable
fun FilterPills(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.horizontalScroll(rememberScrollState())) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (active) ui.chipDark else ui.surface)
                    .border(1.dp, if (active) ui.chipDark else ui.border, CircleShape)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(label, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, color = if (active) ui.onChipDark else ui.text)
            }
        }
    }
}

@Composable
fun UiSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val ui = LocalUi.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Wp.Blue,
            checkedThumbColor = Color.White,
            checkedBorderColor = Wp.Blue,
            uncheckedTrackColor = ui.neutralSoft,
            uncheckedThumbColor = if (ui.isDark) Wp.Gray20 else Wp.Gray50,
            uncheckedBorderColor = ui.border,
        ),
    )
}

/* ---------- Champs ---------- */

@Composable
fun UiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    @DrawableRes leading: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    mono: Boolean = false,
    error: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ui = LocalUi.current
    var visible by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ui.text)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder?.let { { Text(it, color = ui.textSubtle, style = if (mono) MonoStyle.copy(fontSize = 13.sp) else MaterialTheme.typography.bodyLarge) } },
            leadingIcon = leading?.let { { Ico(it, ui.textMuted, 20.dp) } },
            trailingIcon = when {
                trailing != null -> trailing
                password -> {
                    {
                        GhostIconButton(
                            if (visible) R.drawable.ic_ui_eye_off else R.drawable.ic_ui_eye,
                            if (visible) "Masquer le mot de passe" else "Afficher le mot de passe",
                            { visible = !visible },
                            tint = ui.textMuted,
                        )
                    }
                }
                else -> null
            },
            singleLine = singleLine,
            minLines = minLines,
            isError = error != null,
            textStyle = if (mono) MonoStyle.copy(fontSize = 13.sp, color = ui.text) else MaterialTheme.typography.bodyLarge.copy(color = ui.text),
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = ui.surface,
                focusedContainerColor = ui.surface,
                unfocusedBorderColor = ui.border,
                focusedBorderColor = Wp.Blue,
                cursorColor = ui.accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error, fontSize = 12.sp, color = ui.dangerText)
    }
}

/* ---------- En-têtes ---------- */

/** Barre du haut des écrans secondaires : retour, titre, actions. */
@Composable
fun TopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    val ui = LocalUi.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().background(ui.surface).statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
    ) {
        if (onBack != null) GhostIconButton(R.drawable.ic_ui_chevron_left, "Retour", onBack)
        Column(Modifier.weight(1f).padding(start = if (onBack == null) 12.dp else 0.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp, color = ui.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = ui.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/** Ligne de réglage : icône, titre, sous-titre, élément à droite. */
@Composable
fun SettingRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ui = LocalUi.current
    val tint = if (danger) ui.danger else ui.accent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        IconTile(icon, if (danger) ui.dangerSoft else ui.accentSoft, tint, size = 36.dp, iconSize = 19.dp)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (danger) ui.dangerText else ui.text)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = ui.textSubtle)
        }
        when {
            trailing != null -> trailing()
            onClick != null && !danger -> Ico(R.drawable.ic_ui_chevron_right, ui.textSubtle, 18.dp)
        }
    }
}

/** Logo de l'application (pour l'instant la piste A de l'icône). */
@Composable
fun AppLogo(size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Wp.Blue),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_logo_mark), null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}
