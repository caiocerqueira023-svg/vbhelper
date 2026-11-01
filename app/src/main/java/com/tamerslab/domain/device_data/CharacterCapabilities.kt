package com.tamerslab.domain.device_data

import com.tamerslab.domain.card.CardCharacter
import com.tamerslab.transfer.ExportFormat
import com.tamerslab.utils.DeviceType

/** A catalog definition has no physical DIM/BEM slot of its own. */
val CardCharacter.isCatalogPartner: Boolean get() = cardId == null

/** A loaded DIM/BEM slot (as opposed to an app/catalog definition). */
val CardCharacter.hasPhysicalSlot: Boolean get() = cardId != null

/**
 * What a stored partner can actually do, derived from its **current** definition and export target
 * rather than from its stored [DeviceType]. The stored type only names the primary stat family and
 * may legitimately differ from where the partner is currently projected (a converted or returning
 * partner keeps its family while pointing at a device slot).
 *
 * Use this instead of `characterType == DeviceType.APP` style checks whenever the question is
 * "can this reach a watch / needs a DIM" rather than "which stat sidecar is primary".
 */
data class CharacterCapabilities(
    val definition: CardCharacter?,
    val exportTarget: CardCharacter? = null,
    val exportCardIsBem: Boolean = false,
    val definitionCardIsBem: Boolean = false,
) {
    /** No physical slot of its own; it needs one before it can be written to a watch. */
    val isCatalogPartner: Boolean get() = definition?.cardId == null

    /** The loaded DIM/BEM slot a watch payload is written from, if any. */
    val slot: CardCharacter? get() = exportTarget ?: definition?.takeIf { it.cardId != null }

    val hasPhysicalSlot: Boolean get() = slot != null

    val slotId: Long? get() = slot?.id

    /** Needs an exact DIM/BEM association before it can be sent to a real watch. */
    val needsAssociation: Boolean get() = definition?.cardId == null && exportTarget == null

    /** Whether the outgoing payload is a BE (BEM) or VB (DIM) shape. */
    val isBem: Boolean get() = when {
        exportTarget != null -> exportCardIsBem
        definition?.cardId != null -> definitionCardIsBem
        else -> false
    }

    /** The transfer format implied by the backing slot; null when no slot exists yet. */
    val transferFormat: ExportFormat? get() = slot?.let { if (isBem) ExportFormat.BE else ExportFormat.VB }

    companion object {
        /** Quick UI-side check when only the definition and export id are loaded. */
        fun needsAssociation(definition: CardCharacter?, exportCardCharacterId: Long?): Boolean =
            definition?.cardId == null && exportCardCharacterId == null
    }
}
