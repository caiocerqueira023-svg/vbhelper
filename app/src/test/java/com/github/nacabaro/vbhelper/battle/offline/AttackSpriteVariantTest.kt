package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueKind
import com.github.nacabaro.vbhelper.battle.offline.core.attackSpriteVariantFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttackSpriteVariantTest {
    @Test fun specialUsesItsDigimonsLargeAttackSprite() {
        assertEquals("large", attackSpriteVariantFor(TechniqueKind.SPECIAL))
    }

    @Test fun regularAttacksUseTheirDigimonsNormalAttackSprite() {
        listOf(TechniqueKind.BASIC, TechniqueKind.MELEE, TechniqueKind.PROJECTILE, TechniqueKind.AREA)
            .forEach { kind -> assertEquals("$kind", "small", attackSpriteVariantFor(kind)) }
    }

    @Test fun healingAndSupportDoNotUseAttackSprites() {
        assertNull(attackSpriteVariantFor(TechniqueKind.HEAL))
        assertNull(attackSpriteVariantFor(TechniqueKind.SUPPORT))
    }
}
