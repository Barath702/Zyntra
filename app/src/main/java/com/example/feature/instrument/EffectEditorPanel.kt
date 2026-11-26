package com.example.feature.instrument

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.RotaryKnob
import com.example.core.project.EffectChainTransforms
import com.example.core.project.EffectModel
import com.example.core.project.EffectType
import com.example.ui.theme.LocalStudioTokens

@Composable
fun EffectsChainEditor(
    effects: List<EffectModel>,
    accentColor: Color,
    onAddEffect: (EffectType) -> Unit,
    onUpdateEffect: (Int, EffectModel) -> Unit,
    onRemoveEffect: (Int) -> Unit,
    onMoveEffect: (Int, Int) -> Unit,
    fillHeight: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tokens = LocalStudioTokens.current
    var selectedSlot by remember { mutableIntStateOf(0) }
    var showAddMenu by remember { mutableStateOf(false) }

    val safeSlot = if (effects.isEmpty()) 0 else selectedSlot.coerceIn(0, effects.lastIndex)
    if (safeSlot != selectedSlot) {
        selectedSlot = safeSlot
    }

    Column(
        modifier = modifier
            .let { if (fillHeight) it.fillMaxSize() else it.fillMaxWidth() }
            .testTag("effects_chain_editor")
    ) {
        // Chain Strip: numbered effect chips + Add Effect up to 8 slots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            effects.forEachIndexed { idx, fx ->
                val isSel = idx == safeSlot
                val isActive = fx.type != EffectType.NONE && !fx.bypassed
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                isSel -> accentColor.copy(alpha = 0.22f)
                                isActive -> tokens.surfaceElevated
                                else -> tokens.padIdle
                            }
                        )
                        .border(
                            width = if (isSel) 1.dp else 0.5.dp,
                            color = if (isSel) accentColor else tokens.border,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { selectedSlot = idx }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("fx_chain_slot_$idx")
                ) {
                    val name = if (fx.type == EffectType.NONE) "Empty" else fx.type.displayName
                    Text(
                        text = "FX${idx + 1} · $name" + if (fx.bypassed && fx.type != EffectType.NONE) " (Off)" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isSel -> accentColor
                            fx.bypassed -> tokens.textMuted
                            else -> tokens.textPrimary
                        }
                    )
                }
            }

            if (effects.size < EffectChainTransforms.MAX_EFFECT_SLOTS) {
                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(tokens.padIdle)
                            .border(0.5.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .clickable { showAddMenu = true }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                            .testTag("fx_chain_add_button")
                    ) {
                        Text(
                            text = "+ Add FX (${effects.size}/${EffectChainTransforms.MAX_EFFECT_SLOTS})",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor
                        )
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        EffectType.entries.filter { it != EffectType.NONE }.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.displayName) },
                                onClick = {
                                    showAddMenu = false
                                    onAddEffect(t)
                                    selectedSlot = effects.size.coerceAtMost(EffectChainTransforms.MAX_EFFECT_SLOTS - 1)
                                }
                            )
                        }
                    }
                }
            }
        }

        val currentFx = effects.getOrNull(safeSlot)
        if (currentFx == null) {
            Box(
                modifier = (if (fillHeight) Modifier.weight(1f) else Modifier)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No effects in chain — tap + Add FX to insert up to 8 DSP effects.",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.textMuted
                )
            }
        } else {
            key(safeSlot, currentFx.id) {
                EffectEditorPanel(
                    effect = currentFx,
                    accentColor = accentColor,
                    onUpdateEffect = { onUpdateEffect(safeSlot, it) },
                    onRemoveEffect = {
                        onRemoveEffect(safeSlot)
                        if (safeSlot > 0 && safeSlot >= effects.size - 1) {
                            selectedSlot = safeSlot - 1
                        }
                    },
                    onMoveLeft = if (safeSlot > 0) {
                        {
                            onMoveEffect(safeSlot, safeSlot - 1)
                            selectedSlot = safeSlot - 1
                        }
                    } else null,
                    onMoveRight = if (safeSlot < effects.lastIndex) {
                        {
                            onMoveEffect(safeSlot, safeSlot + 1)
                            selectedSlot = safeSlot + 1
                        }
                    } else null,
                    showTypeTitle = false,
                    fillHeight = fillHeight,
                    modifier = if (fillHeight) Modifier.weight(1f) else Modifier
                )
            }
        }
    }
}

@Composable
fun EffectEditorPanel(
    effect: EffectModel,
    accentColor: Color,
    onUpdateEffect: (EffectModel) -> Unit,
    onRemoveEffect: (() -> Unit)? = null,
    onSwapEffects: (() -> Unit)? = null,
    onMoveLeft: (() -> Unit)? = null,
    onMoveRight: (() -> Unit)? = null,
    showTypeTitle: Boolean = false,
    fillHeight: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tokens = LocalStudioTokens.current
    var showPicker by remember { mutableStateOf(false) }

    val presets = remember(effect.type) {
        listOf(
            "Subtle" to effect.type.defaultParams.map { (it * 0.65f).coerceIn(0.05f, 0.95f) },
            "Default" to effect.type.defaultParams,
            "Heavy" to effect.type.defaultParams.map { (it * 1.35f).coerceIn(0.05f, 0.98f) }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("effect_editor_panel")
    ) {
        val distributeEvenly = fillHeight && maxHeight >= 200.dp
        val knobSize = if (distributeEvenly && maxHeight >= 235.dp) 56.dp else if (distributeEvenly) 52.dp else 46.dp
        val colModifier = when {
            distributeEvenly -> Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp)
            fillHeight -> Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 5.dp)
            else -> Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp)
        }
        Column(
            modifier = colModifier,
            verticalArrangement = if (distributeEvenly) Arrangement.SpaceBetween else Arrangement.Top
        ) {
        // Compact header/preset action row (avoids repeating the effect name when shown in tab)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(tokens.padIdle)
                            .border(0.5.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .clickable { showPicker = true }
                            .padding(horizontal = if (showTypeTitle) 10.dp else 7.dp)
                            .testTag("effect_type_selector"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (showTypeTitle) {
                            Text(
                                text = if (effect.type == EffectType.NONE) "+ Add Effect" else effect.type.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (effect.type == EffectType.NONE) tokens.textSecondary else accentColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Change Effect Type",
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = tokens.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    DropdownMenu(expanded = showPicker, onDismissRequest = { showPicker = false }) {
                        EffectType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.displayName) },
                                onClick = {
                                    showPicker = false
                                    onUpdateEffect(
                                        effect.copy(
                                            type = t,
                                            bypassed = false,
                                            params = t.defaultParams,
                                            presetName = "Default"
                                        )
                                    )
                                }
                            )
                        }
                    }
                }

                if (effect.type != EffectType.NONE && !showTypeTitle) {
                    presets.forEach { (pName, pVals) ->
                        val sel = effect.presetName == pName
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (sel) accentColor.copy(alpha = 0.24f) else tokens.surfaceElevated)
                                .border(0.5.dp, if (sel) accentColor else tokens.border, RoundedCornerShape(4.dp))
                                .clickable {
                                    onUpdateEffect(effect.copy(presetName = pName, params = pVals))
                                }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (sel) accentColor else tokens.textSecondary
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onMoveLeft != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(tokens.padIdle)
                            .border(0.5.dp, tokens.border, RoundedCornerShape(4.dp))
                            .clickable(onClick = onMoveLeft)
                            .testTag("fx_move_left"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Move Effect Left",
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (onMoveRight != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(tokens.padIdle)
                            .border(0.5.dp, tokens.border, RoundedCornerShape(4.dp))
                            .clickable(onClick = onMoveRight)
                            .testTag("fx_move_right"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Move Effect Right",
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (onSwapEffects != null) {
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(tokens.padIdle)
                            .border(0.5.dp, tokens.border, RoundedCornerShape(4.dp))
                            .clickable(onClick = onSwapEffects)
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Swap", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                    }
                }
                if (onRemoveEffect != null && (effect.type != EffectType.NONE || onMoveLeft != null || onMoveRight != null)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(tokens.padIdle)
                            .border(0.5.dp, tokens.border, RoundedCornerShape(4.dp))
                            .clickable(onClick = onRemoveEffect)
                            .testTag("fx_remove_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove Effect",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (effect.type != EffectType.NONE) {
                    val active = !effect.bypassed
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) accentColor.copy(alpha = 0.22f) else tokens.padIdle)
                            .border(
                                width = if (active) 1.dp else 0.5.dp,
                                color = if (active) accentColor else tokens.border,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onUpdateEffect(effect.copy(bypassed = active)) }
                            .padding(horizontal = 9.dp)
                            .testTag("effect_bypass_switch"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (active) "ON" else "OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            color = if (active) accentColor else tokens.textMuted
                        )
                    }
                }
            }
        }

        if (effect.type != EffectType.NONE && showTypeTitle) {
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                presets.forEach { (pName, pVals) ->
                    val sel = effect.presetName == pName
                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (sel) accentColor.copy(alpha = 0.24f) else tokens.surfaceElevated)
                            .border(0.5.dp, if (sel) accentColor else tokens.border, RoundedCornerShape(4.dp))
                            .clickable {
                                onUpdateEffect(effect.copy(presetName = pName, params = pVals))
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(pName, style = MaterialTheme.typography.labelSmall, color = if (sel) accentColor else tokens.textSecondary)
                    }
                }
            }
        }

        if (effect.type != EffectType.NONE) {
            if (!distributeEvenly) {
                Spacer(Modifier.height(6.dp))
            }

            Column(
                modifier = if (distributeEvenly) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(),
                verticalArrangement = if (distributeEvenly) Arrangement.SpaceEvenly else Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val lbl = effect.type.paramLabels.getOrElse(i) { "P${i + 1}" }
                        val v = effect.params.getOrElse(i) { effect.type.defaultParams.getOrElse(i) { 0.5f } }
                        RotaryKnob(
                            label = lbl,
                            value = v,
                            onValueChange = { nv ->
                                val next = effect.params.toMutableList()
                                while (next.size < 6) {
                                    next.add(effect.type.defaultParams.getOrElse(next.size) { 0.5f })
                                }
                                next[i] = nv
                                onUpdateEffect(effect.copy(params = next, presetName = "Custom"))
                            },
                            accentColor = accentColor,
                            modifier = Modifier.weight(1f),
                            size = knobSize,
                            defaultValue = effect.type.defaultParams.getOrElse(i) { 0.5f }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 4 until 6) {
                        val lbl = effect.type.paramLabels.getOrElse(i) { "P${i + 1}" }
                        val v = effect.params.getOrElse(i) { effect.type.defaultParams.getOrElse(i) { 0.5f } }
                        RotaryKnob(
                            label = lbl,
                            value = v,
                            onValueChange = { nv ->
                                val next = effect.params.toMutableList()
                                while (next.size < 6) {
                                    next.add(effect.type.defaultParams.getOrElse(next.size) { 0.5f })
                                }
                                next[i] = nv
                                onUpdateEffect(effect.copy(params = next, presetName = "Custom"))
                            },
                            accentColor = accentColor,
                            modifier = Modifier.weight(1f),
                            size = knobSize,
                            defaultValue = effect.type.defaultParams.getOrElse(i) { 0.5f }
                        )
                    }
                    RotaryKnob(
                        label = "Dry/Wet",
                        value = effect.wetDry,
                        onValueChange = { nw -> onUpdateEffect(effect.copy(wetDry = nw)) },
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f),
                        size = knobSize,
                        defaultValue = 0.4f
                    )
                }
            }
        }
        }
    }
}
