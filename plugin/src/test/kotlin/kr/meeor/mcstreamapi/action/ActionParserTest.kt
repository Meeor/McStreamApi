package kr.meeor.mcstreamapi.action

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ActionParserTest {
    @Test
    fun `parses supported action types`() {
        val result = ActionParser().parse(
            listOf(
                mapOf("type" to "cmd", "command" to "say @s"),
                mapOf("type" to "at_player_cmd", "command" to "summon minecraft:chicken ~ ~ ~"),
                mapOf("type" to "summon", "entity" to "minecraft:zombie"),
                mapOf("type" to "sound", "sound" to "minecraft:entity.player.levelup"),
                mapOf("type" to "give", "target" to "@s", "material" to "DIAMOND", "amount" to "<..2>"),
                mapOf("type" to "chat", "message" to "hello"),
                mapOf("type" to "broadcast", "message" to "world"),
                mapOf("type" to "title", "target" to "@s", "title" to "title", "subtitle" to "sub"),
            ),
        )

        assertEquals(8, result.actions.size)
        assertIs<Action.Command>(result.actions[0])
        assertIs<Action.AtPlayerCommand>(result.actions[1])
        assertIs<Action.Summon>(result.actions[2])
        assertIs<Action.Sound>(result.actions[3])
        assertIs<Action.Give>(result.actions[4])
        assertIs<Action.Chat>(result.actions[5])
        assertIs<Action.Broadcast>(result.actions[6])
        assertIs<Action.Title>(result.actions[7])
        assertEquals(emptyList(), result.disabledActions)
    }

    @Test
    fun `disables actions with missing required fields`() {
        val result = ActionParser().parse(
            listOf(
                mapOf("type" to "cmd"),
                mapOf("type" to "at_player_cmd"),
                mapOf("type" to "summon"),
                mapOf("type" to "sound"),
                mapOf("type" to "give", "amount" to 1),
                mapOf("type" to "chat"),
                mapOf("type" to "broadcast"),
                mapOf("type" to "title"),
                mapOf("type" to "unknown"),
            ),
        )

        assertEquals(emptyList(), result.actions)
        assertEquals(
            listOf(
                "MISSING_COMMAND",
                "MISSING_COMMAND",
                "MISSING_ENTITY",
                "MISSING_SOUND",
                "MISSING_MATERIAL",
                "MISSING_MESSAGE",
                "MISSING_MESSAGE",
                "MISSING_TITLE",
                "UNKNOWN_TYPE",
            ),
            result.disabledActions.map { it.reason },
        )
    }

    @Test
    fun `ignores complex item meta on normal give`() {
        val result = ActionParser().parse(
            listOf(
                mapOf(
                    "type" to "give",
                    "material" to "PLAYER_HEAD",
                    "amount" to 1,
                    "customModelData" to 1001,
                    "glow" to true,
                    "playerHead" to "{donator}",
                ),
            ),
        )

        val action = assertIs<Action.Give>(result.actions.single())
        assertEquals(null, action.meta.customModelData)
        assertEquals(null, action.meta.glow)
        assertEquals(null, action.meta.playerHead)
        assertEquals(emptyList(), result.disabledActions)
    }

    @Test
    fun `parses custom item meta options through custom give`() {
        val result = ActionParser(
            customItems = mapOf(
                "donor_head" to mapOf(
                    "material" to "PLAYER_HEAD",
                    "amount" to 1,
                    "customModelData" to 1001,
                    "unbreakable" to true,
                    "glow" to true,
                    "itemFlags" to listOf("HIDE_ENCHANTS", "HIDE_ATTRIBUTES"),
                    "playerHead" to "{donator}",
                    "enchantments" to mapOf("minecraft:unbreaking" to 3),
                    "persistentData" to listOf(
                        mapOf("key" to "mcstreamapi:item_id", "type" to "string", "value" to "donation_head"),
                        mapOf("key" to "mcstreamapi:amount", "type" to "long", "value" to 1000L),
                        mapOf("key" to "mcstreamapi:special", "type" to "boolean", "value" to true),
                    ),
                    "attributes" to listOf(
                        mapOf(
                            "attribute" to "minecraft:generic.attack_damage",
                            "amount" to 2.0,
                            "operation" to "ADD_NUMBER",
                            "slot" to "mainhand",
                        ),
                    ),
                ),
            ),
        ).parse(
            listOf(
                mapOf(
                    "type" to "custom-give",
                    "item" to "{item.donor_head}",
                ),
            ),
        )

        val action = assertIs<Action.Give>(result.actions.single())
        assertEquals(1001, action.meta.customModelData)
        assertEquals(true, action.meta.unbreakable)
        assertEquals(true, action.meta.glow)
        assertEquals(listOf("HIDE_ENCHANTS", "HIDE_ATTRIBUTES"), action.meta.itemFlags)
        assertEquals("{donator}", action.meta.playerHead)
        assertEquals("minecraft:unbreaking", action.meta.enchantments.single().enchantment)
        assertEquals(3, action.meta.persistentData.size)
        assertEquals("donation_head", action.meta.persistentData[0].value)
        assertEquals(1000L, action.meta.persistentData[1].value)
        assertEquals(true, action.meta.persistentData[2].value)
        assertEquals("minecraft:generic.attack_damage", action.meta.attributes.single().attribute)
        assertEquals(emptyList(), result.disabledActions)
    }

    @Test
    fun `parses custom give from custom item config`() {
        val result = ActionParser(
            customItems = mapOf(
                "donation_diamond" to mapOf(
                    "material" to "DIAMOND",
                    "amount" to 1,
                    "name" to "&b후원 다이아몬드",
                    "glow" to true,
                    "persistentData" to listOf(
                        mapOf("key" to "mcstreamapi:item_id", "type" to "string", "value" to "donation_diamond"),
                    ),
                ),
            ),
        ).parse(
            listOf(
                mapOf(
                    "type" to "custom-give",
                    "target" to "@s",
                    "item" to "{item.donation_diamond}",
                    "amount" to 2,
                ),
            ),
        )

        val action = assertIs<Action.Give>(result.actions.single())
        assertEquals("DIAMOND", action.material)
        assertEquals(ActionQuantity.Fixed(2), action.amount)
        assertEquals("&b후원 다이아몬드", action.name)
        assertEquals(true, action.meta.glow)
        assertEquals("donation_diamond", action.meta.persistentData.single().value)
        assertEquals(emptyList(), result.disabledActions)
    }

    @Test
    fun `parses custom give item placeholder prefix`() {
        val result = ActionParser(
            customItems = mapOf(
                "donation_diamond" to mapOf(
                    "material" to "DIAMOND",
                    "amount" to 1,
                ),
            ),
        ).parse(
            listOf(
                mapOf(
                    "type" to "custom-give",
                    "item" to "{item.donation_diamond}",
                ),
            ),
        )

        val action = assertIs<Action.Give>(result.actions.single())
        assertEquals("DIAMOND", action.material)
        assertEquals(emptyList(), result.disabledActions)
    }

    @Test
    fun `defers custom give item lookup when item uses placeholder`() {
        val result = ActionParser(
            customItems = mapOf(
                "donation_diamond" to mapOf(
                    "material" to "DIAMOND",
                    "amount" to 1,
                ),
            ),
        ).parse(
            listOf(
                mapOf(
                    "type" to "custom-give",
                    "item" to "{random.custom_item}",
                ),
            ),
        )

        assertIs<Action.DynamicCustomGive>(result.actions.single())
        assertEquals(emptyList(), result.disabledActions)
    }

    @Test
    fun `disables custom give with unknown item`() {
        val result = ActionParser().parse(
            listOf(
                mapOf("type" to "custom-give", "item" to "missing_item"),
            ),
        )

        assertEquals(emptyList(), result.actions)
        assertEquals(listOf("UNKNOWN_ITEM"), result.disabledActions.map { it.reason })
    }
}
