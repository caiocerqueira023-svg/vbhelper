package com.tamerslab.hardware.dim.card;

import com.tamerslab.hardware.dim.adventure.AdventureLevels;
import com.tamerslab.hardware.dim.character.CharacterStats;
import com.tamerslab.hardware.dim.fusion.AttributeFusions;
import com.tamerslab.hardware.dim.fusion.SpecificFusions;
import com.tamerslab.hardware.dim.header.DimHeader;
import com.tamerslab.hardware.dim.sprite.SpriteData;
import com.tamerslab.hardware.dim.transformation.TransformationRequirements;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
public class Card<
        HeaderType extends DimHeader,
        CharacterStatsType extends CharacterStats,
        TransformationType extends TransformationRequirements,
        AdventureType extends AdventureLevels,
        FusionType extends AttributeFusions,
        SpecficFusionType extends SpecificFusions
        > {
    private final HeaderType header;
    private final CharacterStatsType characterStats;
    private final TransformationType transformationRequirements;
    private final AdventureType adventureLevels;
    private final FusionType attributeFusions;
    private final SpecficFusionType specificFusions;
    private final SpriteData spriteData;
    private final int checksum;
    private final int calculatedCheckSum;
}
