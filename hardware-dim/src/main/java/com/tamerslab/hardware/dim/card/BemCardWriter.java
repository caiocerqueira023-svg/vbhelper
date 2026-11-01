package com.tamerslab.hardware.dim.card;

import com.tamerslab.hardware.dim.adventure.BemAdventureWriter;
import com.tamerslab.hardware.dim.character.BemCharacterWriter;
import com.tamerslab.hardware.dim.fusion.BemFusionWriter;
import com.tamerslab.hardware.dim.header.BemHeaderWriter;
import com.tamerslab.hardware.dim.sprite.*;
import com.tamerslab.hardware.dim.transformation.BemTransformationWriter;
import com.tamerslab.hardware.dim.util.ByteUtils;
import com.tamerslab.hardware.dim.util.DIMChecksumBuilder;
import com.tamerslab.hardware.dim.util.OutputStreamWithNot;
import com.tamerslab.hardware.dim.util.RawChecksumBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

@RequiredArgsConstructor
@Slf4j
public class BemCardWriter {
    private final BemHeaderWriter bemHeaderWriter;
    private final BemCharacterWriter bemCharacterWriter;
    private final BemTransformationWriter bemTransformationWriter;
    private final BemAdventureWriter bemAdventureWriter;
    private final BemSpriteWriter bemSpriteWriter;
    private final BemFusionWriter bemFusionWriter;

    public BemCardWriter() {
        SpriteChecksumAreasCalculator spriteChecksumAreasCalculator = SpriteChecksumAreasCalculator.buildForBEM();
        UnorderedSpriteChecksumHacker unorderedChecksumHacker = new UnorderedSpriteChecksumHacker(spriteChecksumAreasCalculator, SpriteWriter.PIXEL_POINTER_TABLE_START, new RawChecksumBuilder());
        SpriteWriter spriteWriter = new SpriteWriter(unorderedChecksumHacker);
        this.bemHeaderWriter = new BemHeaderWriter();
        this.bemCharacterWriter = new BemCharacterWriter();
        this.bemTransformationWriter = new BemTransformationWriter();
        this.bemAdventureWriter = new BemAdventureWriter();
        this.bemSpriteWriter = new BemSpriteWriter(spriteWriter);
        this.bemFusionWriter = new BemFusionWriter();
    }

    public void writeBemCard(BemCard bemCard, OutputStream outputStream) {
        DIMChecksumBuilder checksumBuilder = new DIMChecksumBuilder();
        OutputStreamWithNot outputStreamWithNot = OutputStreamWithNot.wrap(outputStream, checksumBuilder);
        try {
            bemHeaderWriter.writeHeader(bemCard.getHeader(), outputStreamWithNot);
            outputStreamWithNot.writeZerosUntilOffset(0x10000);
            outputStreamWithNot.write16BitValueUntilOffset(BemCardConstants.NONE_VALUE, 0x20000);
            outputStreamWithNot.writeZerosUntilOffset(BemCardConstants.CHARACTER_SECTION_START);
            bemCharacterWriter.writeBemCharacters(bemCard.getCharacterStats(), outputStreamWithNot);
            outputStreamWithNot.writeZerosUntilOffset(BemCardConstants.TRANSFORMATION_SECTION_START);
            bemTransformationWriter.writeBemTransformations(bemCard.getTransformationRequirements(), outputStreamWithNot);
            outputStreamWithNot.writeZerosUntilOffset(BemCardConstants.ADVENTURE_SECTION_START);
            bemAdventureWriter.writeAdventures(bemCard.getAdventureLevels(), outputStreamWithNot);
            outputStreamWithNot.writeZerosUntilOffset(BemCardConstants.SPRITE_DIMENSIONS_START);
            bemSpriteWriter.writeSpriteDimensions(bemCard.getSpriteData(), outputStreamWithNot);
            outputStreamWithNot.writeZerosUntilOffset(BemCardConstants.ATTRIBUTE_FUSION_START);
            bemFusionWriter.writeAttributeFusions(bemCard.getAttributeFusions(), outputStreamWithNot);
            outputStreamWithNot.writeZerosUntilOffset(BemCardConstants.SPECIFIC_FUSION_START);
            bemFusionWriter.writeSpecificFusions(bemCard.getSpecificFusions(), outputStreamWithNot);
            outputStreamWithNot.writeZerosUntilOffset(0x90000);
            outputStreamWithNot.write16BitValueUntilOffset(BemCardConstants.NONE_VALUE, BemCardConstants.SPRITE_PACKAGE_START);
            bemSpriteWriter.writeSpritePackage(bemCard.getSpriteData(), bemCard.getHeader().hasSpriteSignature(), outputStreamWithNot);
            outputStreamWithNot.writeZerosUntilOffset(BemCardConstants.CHECKSUM_LOCATION);
            outputStreamWithNot.writeBytes(ByteUtils.convert16BitIntToBytes(outputStreamWithNot.getChecksum()));
        } catch (IOException e) {
            log.error("Failed to write DIM file!", e);
            throw new UncheckedIOException(e);
        }
    }
}
