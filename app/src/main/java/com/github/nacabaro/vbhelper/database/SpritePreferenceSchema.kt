package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

/** Schema 54: both artwork priorities have mutual fallback; no gameplay mode is stored. */
object SpritePreferenceSchema {
    const val DISPLAY_SPRITE_QUERY = """SELECT c.id AS cardCharacterId, s.id, s.width, s.height, s.spriteIdle1,
        CASE WHEN length(s.spriteIdle2) = s.width * s.height * 2 THEN s.spriteIdle2 ELSE s.spriteIdle1 END AS spriteIdle2,
        CASE WHEN length(s.spriteWalk1) = s.width * s.height * 2 THEN s.spriteWalk1 ELSE s.spriteIdle1 END AS spriteWalk1,
        CASE WHEN length(s.spriteWalk2) = s.width * s.height * 2 THEN s.spriteWalk2 ELSE s.spriteIdle1 END AS spriteWalk2,
        CASE WHEN length(s.spriteRun1) = s.width * s.height * 2 THEN s.spriteRun1 ELSE s.spriteIdle1 END AS spriteRun1,
        CASE WHEN length(s.spriteRun2) = s.width * s.height * 2 THEN s.spriteRun2 ELSE s.spriteIdle1 END AS spriteRun2,
        CASE WHEN length(s.spriteTrain1) = s.width * s.height * 2 THEN s.spriteTrain1 ELSE s.spriteIdle1 END AS spriteTrain1,
        CASE WHEN length(s.spriteTrain2) = s.width * s.height * 2 THEN s.spriteTrain2 ELSE s.spriteIdle1 END AS spriteTrain2,
        CASE WHEN length(s.spriteHappy) = s.width * s.height * 2 THEN s.spriteHappy ELSE s.spriteIdle1 END AS spriteHappy,
        CASE WHEN length(s.spriteSleep) = s.width * s.height * 2 THEN s.spriteSleep ELSE s.spriteIdle1 END AS spriteSleep,
        CASE WHEN length(s.spriteAttack) = s.width * s.height * 2 THEN s.spriteAttack ELSE s.spriteIdle1 END AS spriteAttack,
        CASE WHEN length(s.spriteDodge) = s.width * s.height * 2 THEN s.spriteDodge ELSE s.spriteIdle1 END AS spriteDodge
        FROM CardCharacter c
        LEFT JOIN SpeciesProfile p ON p.cardCharacterId = c.id
        LEFT JOIN SpeciesCatalogEntry catalog ON catalog.speciesKey = p.appearanceKey OR catalog.originalKey = p.appearanceKey
        LEFT JOIN SpeciesSpriteChoice choice ON choice.speciesKey = COALESCE(catalog.speciesKey, p.appearanceKey)
        LEFT JOIN SpeciesDefaultSprite dot ON dot.cardCharacterId = c.id
        JOIN Sprite s ON s.id = CASE WHEN (SELECT mode FROM AppSourceState WHERE id = 1) = 'DOT_FIRST'
            THEN COALESCE(CASE WHEN choice.spriteId < -1 THEN choice.spriteId END, dot.spriteId,
                (SELECT bundled.spriteId FROM CardCharacter bundled WHERE bundled.catalogSpeciesId = c.speciesId AND bundled.spriteId < -1 LIMIT 1),
                CASE WHEN choice.spriteId >= 0 THEN choice.spriteId END,
                CASE WHEN c.cardId IS NOT NULL THEN c.spriteId END,
                (SELECT native.spriteId FROM CardCharacter native JOIN Card imported ON imported.id = native.cardId
                    WHERE native.speciesId = c.speciesId ORDER BY imported.isBEm DESC, native.id LIMIT 1), c.spriteId)
            ELSE COALESCE(CASE WHEN choice.spriteId >= 0 THEN choice.spriteId END,
                CASE WHEN c.cardId IS NOT NULL THEN c.spriteId END,
                (SELECT native.spriteId FROM CardCharacter native JOIN Card imported ON imported.id = native.cardId
                    WHERE native.speciesId = c.speciesId ORDER BY imported.isBEm DESC, native.id LIMIT 1),
                CASE WHEN choice.spriteId < -1 THEN choice.spriteId END, dot.spriteId, c.spriteId) END"""

    fun migrate(db: SupportSQLiteDatabase) {
        // Keep the legacy table/column for a preserving migration; its values are cosmetic now.
        db.execSQL("UPDATE AppSourceState SET mode = CASE WHEN mode IN ('EXPANDED', 'DOT_FIRST') THEN 'DOT_FIRST' ELSE 'VB_FIRST' END")
        db.execSQL("DROP VIEW IF EXISTS DisplaySprite")
        db.execSQL("CREATE VIEW `DisplaySprite` AS $DISPLAY_SPRITE_QUERY")
    }
}
