package com.tamerslab.hardware.dim.card;

import com.tamerslab.hardware.dim.adventure.DimAdventures;
import com.tamerslab.hardware.dim.character.DimStats;
import com.tamerslab.hardware.dim.fusion.DimFusions;
import com.tamerslab.hardware.dim.fusion.DimSpecificFusions;
import com.tamerslab.hardware.dim.header.DimHeader;
import com.tamerslab.hardware.dim.transformation.DimEvolutionRequirements;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class DimCard extends Card<
		DimHeader,
		DimStats,
		DimEvolutionRequirements,
		DimAdventures,
		DimFusions,
		DimSpecificFusions> { }
