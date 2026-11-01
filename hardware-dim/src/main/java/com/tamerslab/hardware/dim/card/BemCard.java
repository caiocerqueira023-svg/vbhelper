package com.tamerslab.hardware.dim.card;

import com.tamerslab.hardware.dim.adventure.BemAdventureLevels;
import com.tamerslab.hardware.dim.character.BemCharacterStats;
import com.tamerslab.hardware.dim.fusion.AttributeFusions;
import com.tamerslab.hardware.dim.fusion.BemSpecificFusions;
import com.tamerslab.hardware.dim.header.BemHeader;
import com.tamerslab.hardware.dim.transformation.BemTransformationRequirements;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class BemCard extends Card<
        BemHeader,
        BemCharacterStats,
        BemTransformationRequirements,
        BemAdventureLevels,
        AttributeFusions,
        BemSpecificFusions> { }
