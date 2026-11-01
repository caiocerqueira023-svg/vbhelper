package com.tamerslab.hardware.nfc.be

import com.tamerslab.hardware.nfc.data.block.TransformationRequirementsBlockTranslator
import com.tamerslab.hardware.nfc.getUInt16
import com.tamerslab.hardware.nfc.toByteArray
import java.nio.ByteOrder

class BETransformationRequirementsBlockTranslator : TransformationRequirementsBlockTranslator<BENfcCharacter>() {
    companion object {
        const val TRAINING_PP_IDX = 0
        const val CHARACTER_CREATION_FIRMWARE_VERSION_IDX = 12
    }

    override fun parseBlockIntoCharacter(block: ByteArray, character: BENfcCharacter) {
        super.parseBlockIntoCharacter(block, character)
        character.setTrainingPp(block.getUInt16(TRAINING_PP_IDX, ByteOrder.BIG_ENDIAN))
        character.characterCreationFirmwareVersion = FirmwareVersion(
            majorVersion = block[CHARACTER_CREATION_FIRMWARE_VERSION_IDX],
            minorVersion = block[CHARACTER_CREATION_FIRMWARE_VERSION_IDX+1])
    }

    override fun writeCharacterIntoBlocks(character: BENfcCharacter, block: ByteArray): ByteArray {
        super.writeCharacterIntoBlocks(character, block)
        character.getTrainingPp().toByteArray(block, TRAINING_PP_IDX, ByteOrder.BIG_ENDIAN)
        block[CHARACTER_CREATION_FIRMWARE_VERSION_IDX] = character.characterCreationFirmwareVersion.majorVersion
        block[CHARACTER_CREATION_FIRMWARE_VERSION_IDX+1] = character.characterCreationFirmwareVersion.minorVersion
        return block
    }
}