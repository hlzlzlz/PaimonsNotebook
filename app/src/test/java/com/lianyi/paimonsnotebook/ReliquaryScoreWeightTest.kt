package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.util.reliquary.ReliquaryScoreWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 圣遗物手动权重的序列化回归测试
*
* 背景:本类曾因字段被R8混淆(无@SerializedName)导致升级后权重读不回来,
* 表现为"用户设置的权重自己还原成默认值"。这里锁定两件事:
* 1) 新格式(带@SerializedName)可正常往返;
* 2) 旧格式(混淆单字母键名)仍能按位置迁移读回,不会静默丢数据。
* */
class ReliquaryScoreWeightTest {

    @Test
    fun newFormat_roundTrip() {
        val weight = ReliquaryScoreWeight(
            critRate = 1.5,
            critDmg = 2.0,
            atkPercent = 0.5,
            hpPercent = 0.25,
            defPercent = 0.1,
            chargeEfficiency = 0.75,
            elementMastery = 0.3
        )

        val json = weight.stringify()

        //新格式必须是可读的具名字段
        assertTrue("新格式应包含具名字段", json.contains("critRate"))
        assertFalse("新格式不应被判为旧格式", ReliquaryScoreWeight.isLegacyFormat(json))

        val restored = ReliquaryScoreWeight.fromJson(json)
        assertNotNull("新格式应能解析", restored)
        assertEquals(weight, restored)
    }

    @Test
    fun legacyFormat_isDetected() {
        //1.8.7及更早版本被R8混淆后的实际键名形态
        val legacyJson = """{"a":1.5,"b":2.0,"c":0.5,"d":0.25,"e":0.1,"f":0.75,"g":0.3}"""

        assertTrue("旧格式应被识别", ReliquaryScoreWeight.isLegacyFormat(legacyJson))
    }

    @Test
    fun legacyFormat_migratesByPosition() {
        val legacyJson = """{"a":1.5,"b":2.0,"c":0.5,"d":0.25,"e":0.1,"f":0.75,"g":0.3}"""

        val migrated = ReliquaryScoreWeight.fromJson(legacyJson)

        assertNotNull("旧格式应能迁移解析", migrated)
        assertEquals(1.5, migrated!!.critRate, 1e-9)
        assertEquals(2.0, migrated.critDmg, 1e-9)
        assertEquals(0.5, migrated.atkPercent, 1e-9)
        assertEquals(0.25, migrated.hpPercent, 1e-9)
        assertEquals(0.1, migrated.defPercent, 1e-9)
        assertEquals(0.75, migrated.chargeEfficiency, 1e-9)
        assertEquals(0.3, migrated.elementMastery, 1e-9)
    }

    @Test
    fun legacyFormat_partialKeys_fallsBackToDefault() {
        //只有前两个键(早期版本字段更少)时,其余应退回默认值1.0而非崩溃
        val legacyJson = """{"a":1.5,"b":2.0}"""

        val migrated = ReliquaryScoreWeight.fromJson(legacyJson)

        assertNotNull(migrated)
        assertEquals(1.5, migrated!!.critRate, 1e-9)
        assertEquals(2.0, migrated.critDmg, 1e-9)
        assertEquals(1.0, migrated.atkPercent, 1e-9)
        assertEquals(1.0, migrated.elementMastery, 1e-9)
    }

    @Test
    fun malformedJson_returnsNull() {
        assertEquals(null, ReliquaryScoreWeight.fromJson("not a json"))
        assertEquals(null, ReliquaryScoreWeight.fromJson(""))
    }
}
