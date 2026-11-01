package com.tamerslab.battle.online

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineBattleContractTest {
    @Test fun pvpAndAuthJsonShapesRemainStable() {
        val gson = Gson()
        val pvp = gson.fromJson("""{"status":"active","state":1,"currentRound":2,"playerHP":30,
            "opponentHP":20,"playerAttackHit":true,"playerAttackDamage":4,"opponentAttackDamage":0,
            "winner":"","opponentCharaId":"dim001_mon01"}""", PVPDataModel::class.java)
        assertEquals("dim001_mon01", pvp.opponentCharaId)
        assertEquals(30, pvp.playerHP)
        val auth = gson.fromJson("""{"success":true,"userInfo":{"userId":"u1","additionalInfo":{"id":7}},
            "sessionToken":"session"}""", AuthenticateResponse::class.java)
        assertEquals(7L, auth.userInfo?.additionalInfo?.id)
        assertEquals("session", auth.sessionToken)
    }

}

