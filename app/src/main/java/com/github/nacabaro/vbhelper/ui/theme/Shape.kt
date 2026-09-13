package com.github.nacabaro.vbhelper.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Deliberately clipped corners make Material controls feel like parts of a
// device interface instead of floating rounded cards.
val VitalArenaShapes = Shapes(
    extraSmall = CutCornerShape(4.dp),
    small = CutCornerShape(7.dp),
    medium = CutCornerShape(10.dp),
    large = CutCornerShape(14.dp),
    extraLarge = CutCornerShape(18.dp)
)
