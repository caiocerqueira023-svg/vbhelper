package com.tamerslab.hardware.dim.card;


import com.tamerslab.hardware.dim.adventure.BemAdventureLevels;
import com.tamerslab.hardware.dim.adventure.BemAdventuresReader;
import com.tamerslab.hardware.dim.character.BemCharacterReader;
import com.tamerslab.hardware.dim.character.BemCharacterStats;
import com.tamerslab.hardware.dim.fusion.AttributeFusions;
import com.tamerslab.hardware.dim.fusion.BemFusionReader;
import com.tamerslab.hardware.dim.fusion.BemSpecificFusions;
import com.tamerslab.hardware.dim.header.BemHeader;
import com.tamerslab.hardware.dim.header.BemHeaderReader;
import com.tamerslab.hardware.dim.sprite.BemSpriteReader;
import com.tamerslab.hardware.dim.sprite.SpriteData;
import com.tamerslab.hardware.dim.transformation.BemTransformationReader;
import com.tamerslab.hardware.dim.transformation.BemTransformationRequirements;
import com.tamerslab.hardware.dim.util.ByteOffsetInputStream;
import com.tamerslab.hardware.dim.util.ByteUtils;
import com.tamerslab.hardware.dim.util.DIMChecksumBuilder;
import com.tamerslab.hardware.dim.util.InputStreamWithNot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class BemCardReader {
    private final BemHeaderReader bemHeaderReader;
    private final BemCharacterReader bemCharacterReader;
    private final BemTransformationReader bemTransformationReader;
    private final BemAdventuresReader bemAdventuresReader;
    private final BemFusionReader bemFusionReader;
    private final BemSpriteReader bemSpriteReader;

    public BemCardReader() {
        bemHeaderReader = new BemHeaderReader();
        bemCharacterReader = new BemCharacterReader();
        bemTransformationReader = new BemTransformationReader();
        bemAdventuresReader = new BemAdventuresReader();
        bemFusionReader = new BemFusionReader();
        bemSpriteReader = new BemSpriteReader();
    }

    public BemCard readBemCard(InputStream inputStream) {
        DIMChecksumBuilder checksumBuilder = new DIMChecksumBuilder();
        InputStreamWithNot inputStreamWithNot = new InputStreamWithNot(inputStream, checksumBuilder);
        try {
            byte[] headerBytes = inputStreamWithNot.readNBytes(0x1030);
            return readBemCard(headerBytes, inputStreamWithNot);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

     BemCard readBemCard(byte[] headerBytes, InputStreamWithNot inputStream) {
        BemHeader header = bemHeaderReader.readBemHeaderFromHeaderBytes(headerBytes);
        try {
            inputStream.readToOffset(BemCardConstants.CHARACTER_SECTION_START);
            BemCharacterStats characters = bemCharacterReader.readCharacterStats(inputStream);
            inputStream.readToOffset(BemCardConstants.TRANSFORMATION_SECTION_START);
            BemTransformationRequirements transformations = bemTransformationReader.readTransformations(inputStream);
            inputStream.readToOffset(BemCardConstants.ADVENTURE_SECTION_START);
            BemAdventureLevels adventureLevels = bemAdventuresReader.readAdventures(inputStream);
            inputStream.readToOffset(BemCardConstants.SPRITE_DIMENSIONS_START);
            List<SpriteData.SpriteDimensions> spriteDimensions = bemSpriteReader.readSpriteDimensions(inputStream);
            inputStream.readToOffset(BemCardConstants.ATTRIBUTE_FUSION_START);
            AttributeFusions attributeFusions = bemFusionReader.readAttributeFusion(inputStream);
            inputStream.readToOffset(BemCardConstants.SPECIFIC_FUSION_START);
            BemSpecificFusions specificFusions = bemFusionReader.readSpecificFusions(inputStream);
            inputStream.readToOffset(BemCardConstants.SPRITE_PACKAGE_START);
            SpriteData spriteData = bemSpriteReader.getSpriteData(inputStream, spriteDimensions);
            int checksumOnCard = readToChecksum(inputStream);
            int calculatedChecksum = inputStream.getChecksum();
            if(checksumOnCard != calculatedChecksum) {
                log.warn("Checksums don't match! Calculated: {} Received: {}", Integer.toHexString(calculatedChecksum), Integer.toHexString(checksumOnCard));
            }

            return BemCard.builder()
                    .header(header)
                    .characterStats(characters)
                    .transformationRequirements(transformations)
                    .adventureLevels(adventureLevels)
                    .attributeFusions(attributeFusions)
                    .specificFusions(specificFusions)
                    .spriteData(spriteData)
                    .calculatedCheckSum(calculatedChecksum)
                    .checksum(checksumOnCard)
                    .build();
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private int readToChecksum(ByteOffsetInputStream inputStream) throws IOException {
        inputStream.readToOffset(BemCardConstants.CHECKSUM_LOCATION);
        byte[] checksumBytes = inputStream.readNBytes(2);
        int dimChecksum = ByteUtils.getUnsigned16Bit(checksumBytes)[0];
        return dimChecksum;
    }
}
