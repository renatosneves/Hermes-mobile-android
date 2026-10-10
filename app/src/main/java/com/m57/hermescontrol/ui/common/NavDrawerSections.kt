package com.m57.hermescontrol.ui.common

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.m57.hermescontrol.DrawerSection
import com.m57.hermescontrol.R
import com.m57.hermescontrol.ScreenDefinition
import com.m57.hermescontrol.theme.BotsPalette
import com.m57.hermescontrol.theme.ToyFonts
import com.m57.hermescontrol.theme.toySticker

private const val PREFS = "nav_drawer"

/** Remembers which drawer sections are collapsed, across launches. */
private fun readCollapsed(
    context: Context,
    section: DrawerSection,
): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("collapsed_${section.name}", false)

private fun writeCollapsed(
    context: Context,
    section: DrawerSection,
    collapsed: Boolean,
) {
    context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("collapsed_${section.name}", collapsed)
        .apply()
}

/**
 * The navigation drawer's sections: each header toggles its items, the state is remembered, and the
 * section holding [currentScreen] opens itself. In Toybox it is drawn as sticker cards.
 */
@Composable
fun NavDrawerSections(
    screens: List<ScreenDefinition>,
    currentScreen: NavKey,
    onNavigate: (NavKey) -> Unit,
) {
    val context = LocalContext.current
    val collapsed =
        remember {
            mutableStateMapOf<DrawerSection, Boolean>().also { map ->
                DrawerSection.entries.forEach { map[it] = readCollapsed(context, it) }
            }
        }
    val setCollapsed: (DrawerSection, Boolean) -> Unit = { section, value ->
        collapsed[section] = value
        writeCollapsed(context, section, value)
    }
    // Going to a screen whose section is closed opens that section.
    LaunchedEffect(currentScreen) {
        val owner = screens.firstOrNull { it.key == currentScreen }?.drawerSection
        if (owner != null && collapsed[owner] == true) setCollapsed(owner, false)
    }
    val toy = BotsPalette.isToybox
    Column(
        verticalArrangement = Arrangement.spacedBy(if (toy) 14.dp else 0.dp),
        modifier = if (toy) Modifier.padding(horizontal = 18.dp) else Modifier,
    ) {
        for (section in DrawerSection.entries) {
            val items = screens.filter { it.drawerSection == section }
            if (items.isEmpty()) continue
            val open = collapsed[section] != true
            val toggle = { setCollapsed(section, open) }
            if (toy) {
                ToySection(section, items, open, currentScreen, toggle, onNavigate)
            } else {
                PlainSection(section, items, open, currentScreen, toggle, onNavigate)
            }
        }
    }
}

@Composable
private fun SectionChevron(
    open: Boolean,
    tint: Color,
) {
    val angle by animateFloatAsState(if (open) 180f else 0f, label = "nav-section-chevron")
    Icon(
        Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(22.dp).rotate(angle),
    )
}

@Composable
private fun PlainSection(
    section: DrawerSection,
    items: List<ScreenDefinition>,
    open: Boolean,
    currentScreen: NavKey,
    onToggle: () -> Unit,
    onNavigate: (NavKey) -> Unit,
) {
    val title = stringResource(section.titleRes)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { contentDescription = title }
                .padding(start = 16.dp, top = 8.dp, bottom = 4.dp, end = 16.dp)
                .testTag("nav_section_${section.name.lowercase()}"),
    ) {
        Text(
            text = title.uppercase(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        SectionChevron(open, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AnimatedVisibility(
        visible = open,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column {
            items.forEach { entry ->
                NavigationDrawerItem(
                    icon = { Icon(entry.icon, contentDescription = null) },
                    label = { Text(stringResource(entry.labelRes)) },
                    selected = currentScreen == entry.key,
                    onClick = { onNavigate(entry.key) },
                    colors =
                        NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )
            }
        }
    }
}

/** The chip colour of a Toybox section: purple, orange, dark, pink. */
private fun chipColor(section: DrawerSection): Color =
    when (section) {
        DrawerSection.CONVERSE -> BotsPalette.ToyAccent
        DrawerSection.AUTOMATE -> BotsPalette.ToyTalk
        DrawerSection.CONFIGURE -> BotsPalette.ToyOutline
        DrawerSection.INSPECT -> BotsPalette.ToyPink
    }

@Composable
private fun ToySection(
    section: DrawerSection,
    items: List<ScreenDefinition>,
    open: Boolean,
    currentScreen: NavKey,
    onToggle: () -> Unit,
    onNavigate: (NavKey) -> Unit,
) {
    val cardShape = RoundedCornerShape(24.dp)
    val title = stringResource(section.titleRes)
    val chip = chipColor(section)
    val tint = lerp(chip, BotsPalette.ToyWhite, 0.82f)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(end = 4.dp, bottom = 4.dp)
                .toySticker(cardShape, BotsPalette.ToyWhite, depth = 4.dp)
                .padding(10.dp)
                .animateContentSize()
                .testTag("nav_section_${section.name.lowercase()}"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onToggle)
                    .padding(horizontal = 8.dp),
        ) {
            val chipShape = RoundedCornerShape(12.dp)
            Text(
                text = title.uppercase(),
                color = BotsPalette.ToyOnAccent,
                fontFamily = ToyFonts.Body,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                letterSpacing = 0.65.sp,
                modifier =
                    Modifier
                        .toySticker(chipShape, chip, shadow = null, outline = 3.dp)
                        .padding(horizontal = 12.dp, vertical = 3.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = pluralStringResource(R.plurals.nav_drawer_items, items.size, items.size),
                color = BotsPalette.ToyMutedText,
                fontFamily = ToyFonts.Body,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(6.dp))
            SectionChevron(open, BotsPalette.ToyText)
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { entry ->
                    ToyItem(entry, tint, selected = currentScreen == entry.key) { onNavigate(entry.key) }
                }
            }
        }
    }
}

@Composable
private fun ToyItem(
    entry: ScreenDefinition,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    // The yellow row always carries dark text, by day and by night.
    val textColor = if (selected) BotsPalette.ToyOutline else BotsPalette.ToyText
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .then(
                    if (selected) {
                        Modifier.toySticker(shape, BotsPalette.ToyYellow, shadow = null)
                    } else {
                        Modifier
                    },
                ).clip(shape)
                .clickable(role = Role.Tab, onClick = onClick)
                .padding(horizontal = 10.dp),
    ) {
        val iconShape = RoundedCornerShape(12.dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(36.dp)
                    .toySticker(iconShape, tint, shadow = null, outline = 2.dp)
                    .background(Color.Transparent),
        ) {
            Icon(
                entry.icon,
                contentDescription = null,
                tint = BotsPalette.ToyText,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = stringResource(entry.labelRes),
            color = textColor,
            fontFamily = ToyFonts.Display,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}
