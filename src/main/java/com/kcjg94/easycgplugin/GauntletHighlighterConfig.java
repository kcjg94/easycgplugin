package com.kcjg94.easycgplugin;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("gauntlethighlighter")
public interface GauntletHighlighterConfig extends Config
{
	@ConfigSection(
		name = "Resource Nodes",
		description = "Crystal deposits, phren roots, grym roots, linum tirinum and fishing spots",
		position = 0
	)
	String resourceSection = "resourceSection";

	@ConfigItem(
		keyName = "highlightResources",
		name = "Highlight resource nodes",
		description = "Outlines gatherable resource nodes",
		section = resourceSection,
		position = 0
	)
	default boolean highlightResources()
	{
		return true;
	}

	@ConfigItem(
		keyName = "resourceColor",
		name = "Resource node color",
		description = "Color used to outline resource nodes",
		section = resourceSection,
		position = 1
	)
	default Color resourceColor()
	{
		return Color.CYAN;
	}

	@ConfigItem(
		keyName = "highlightDepleted",
		name = "Highlight depleted nodes",
		description = "Also highlights nodes that have already been used up",
		section = resourceSection,
		position = 2
	)
	default boolean highlightDepleted()
	{
		return false;
	}

	@ConfigItem(
		keyName = "limitByInventory",
		name = "Stop once target reached",
		description = "Once an item hits its target below, its resource node (and, for weapon frames, ground drops) stops highlighting for the rest of the run - even if you later use some of that item",
		section = resourceSection,
		position = 3
	)
	default boolean limitByInventory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "oreTarget",
		name = "Corrupted ore target",
		description = "How many Corrupted ore you need",
		section = resourceSection,
		position = 4
	)
	default int oreTarget()
	{
		return 13;
	}

	@ConfigItem(
		keyName = "barkTarget",
		name = "Phren bark target",
		description = "How many Phren bark you need",
		section = resourceSection,
		position = 5
	)
	default int barkTarget()
	{
		return 13;
	}

	@ConfigItem(
		keyName = "tirinumTarget",
		name = "Linum tirinum target",
		description = "How many Linum tirinum you need",
		section = resourceSection,
		position = 6
	)
	default int tirinumTarget()
	{
		return 13;
	}

	@ConfigItem(
		keyName = "herbTarget",
		name = "Grym leaf (herb) target",
		description = "How many Grym leaf you need",
		section = resourceSection,
		position = 7
	)
	default int herbTarget()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "paddlefishTarget",
		name = "Raw paddlefish target",
		description = "How many Raw paddlefish you need",
		section = resourceSection,
		position = 8
	)
	default int paddlefishTarget()
	{
		return 12;
	}

	@ConfigItem(
		keyName = "highlightRoomNodes",
		name = "Highlight room nodes",
		description = "Outlines the node you light with your sceptre to reveal the next room - always included in the route, since you must light it to progress",
		section = resourceSection,
		position = 9
	)
	default boolean highlightRoomNodes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "roomNodeColor",
		name = "Room node color",
		description = "Color used to outline the room-opening node. If nothing highlights at your room's exit, this plugin's guess at the object's name may be wrong - add the correct one under Custom / Advanced -> Custom object names instead",
		section = resourceSection,
		position = 10
	)
	default Color roomNodeColor()
	{
		return Color.PINK;
	}

	@ConfigSection(
		name = "Monsters",
		description = "Demi-bosses, the Hunllef, and the specific weaker monsters worth killing - not a \"highlight every creature\" toggle. Each is only shown while it's actually still useful: see the route phase settings below for exactly when",
		position = 1
	)
	String monsterSection = "monsterSection";

	@ConfigItem(
		keyName = "highlightMonsters",
		name = "Highlight rat/spider/bat and unicorn/scorpion",
		description = "Outlines Corrupted rat/spider/bat only until your first weapon frame drops, and Corrupted unicorn/scorpion only in phase 2 while shards are still needed - not the Corrupted wolf or any other monster, which have no tracked purpose here",
		section = monsterSection,
		position = 0
	)
	default boolean highlightMonsters()
	{
		return true;
	}

	@ConfigItem(
		keyName = "monsterColor",
		name = "Monster color",
		description = "Color used to outline the weapon-frame and shard-farm monsters above",
		section = monsterSection,
		position = 1
	)
	default Color monsterColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(
		keyName = "highlightDemiBosses",
		name = "Highlight demi-bosses",
		description = "Outlines the Corrupted Bear/Dark Beast/Dragon only while you still need their materials or a weapon frame - stops once fully stocked",
		section = monsterSection,
		position = 2
	)
	default boolean highlightDemiBosses()
	{
		return true;
	}

	@ConfigItem(
		keyName = "demiBossColor",
		name = "Demi-boss color",
		description = "Color used to outline demi-bosses",
		section = monsterSection,
		position = 3
	)
	default Color demiBossColor()
	{
		return Color.ORANGE;
	}

	@ConfigItem(
		keyName = "highlightHunllef",
		name = "Highlight the Corrupted Hunllef",
		description = "Highlights the boss itself",
		section = monsterSection,
		position = 4
	)
	default boolean highlightHunllef()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hunllefColor",
		name = "Hunllef color",
		description = "Color used to outline the Corrupted Hunllef",
		section = monsterSection,
		position = 5
	)
	default Color hunllefColor()
	{
		return Color.RED;
	}

	@ConfigSection(
		name = "Ground Items",
		description = "Crystal shards, weapon frames, tier-up materials and boss loot",
		position = 2
	)
	String itemSection = "itemSection";

	@ConfigItem(
		keyName = "highlightGroundItems",
		name = "Highlight valuable ground items",
		description = "Highlights the tile of any valuable dropped item",
		section = itemSection,
		position = 0
	)
	default boolean highlightGroundItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "groundItemColor",
		name = "Ground item color",
		description = "Color used to highlight ground item tiles",
		section = itemSection,
		position = 1
	)
	default Color groundItemColor()
	{
		return Color.GREEN;
	}

	@ConfigItem(
		keyName = "weaponFrameTarget",
		name = "Weapon frame target",
		description = "How many weapon frames you need - once reached, weapon frames stop highlighting on the ground and in the checklist",
		section = itemSection,
		position = 2
	)
	default int weaponFrameTarget()
	{
		return 2;
	}

	@ConfigSection(
		name = "Supply Checklist",
		description = "On-screen panel tracking resource counts against your targets",
		position = 3
	)
	String checklistSection = "checklistSection";

	@ConfigItem(
		keyName = "showChecklist",
		name = "Show supply checklist",
		description = "Shows an on-screen panel tracking Corrupted ore / Phren bark / Linum tirinum / Crystal shards against your targets",
		section = checklistSection,
		position = 0
	)
	default boolean showChecklist()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shardsTarget",
		name = "Corrupted shard target (manual)",
		description = "The checklist's shard row turns green once you reach this many. Only used when \"Dynamic shard target\" below is off",
		section = checklistSection,
		position = 1
	)
	default int shardsTarget()
	{
		return 750;
	}

	@ConfigItem(
		keyName = "useDynamicShardTarget",
		name = "Dynamic shard target",
		description = "Computes the shard target live instead of using the fixed number above: full T3 armor (650) + vials + 50 per distinct weapon type you've actually started or received a matching demi-boss material for. Starts at the 2-weapon estimate and only rises to a 3rd weapon's worth if a demi material turns out not to match a weapon you already started",
		section = checklistSection,
		position = 2
	)
	default boolean useDynamicShardTarget()
	{
		return true;
	}

	@ConfigItem(
		keyName = "vialCount",
		name = "Vials to craft",
		description = "How many vials (10 shards each) to include in the dynamic shard target",
		section = checklistSection,
		position = 3
	)
	default int vialCount()
	{
		return 2;
	}

	@ConfigSection(
		name = "Efficient Route",
		description = "Numbered waypoints and lines connecting still-needed resource nodes, nearest-first",
		position = 4
	)
	String routeSection = "routeSection";

	@ConfigItem(
		keyName = "showRoute",
		name = "Show efficient route",
		description = "Draws a numbered nearest-neighbor route through the resource nodes you still need",
		section = routeSection,
		position = 0
	)
	default boolean showRoute()
	{
		return true;
	}

	@ConfigItem(
		keyName = "routeColor",
		name = "Route color",
		description = "Color used for the route lines and waypoint numbers",
		section = routeSection,
		position = 1
	)
	default Color routeColor()
	{
		return Color.MAGENTA;
	}

	@ConfigItem(
		keyName = "routeLineWidth",
		name = "Route line width",
		description = "Thickness of the route lines",
		section = routeSection,
		position = 2
	)
	default int routeLineWidth()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "maxGatherDistance",
		name = "Max gather distance (tiles)",
		description = "Resource nodes farther than this from where you're currently standing are left off the route - so a node you've walked past/skipped doesn't keep pulling the route back to it as you move further away",
		section = routeSection,
		position = 3
	)
	default int maxGatherDistance()
	{
		return 20;
	}

	@ConfigSection(
		name = "Route Phases",
		description = "Two-phase strategy: gather nearby first, return to craft, then expand to the outer rim",
		position = 5
	)
	String phaseSection = "phaseSection";

	@ConfigItem(
		keyName = "phase1ShardTarget",
		name = "Phase 1 shard target",
		description = "Once you have this many shards AND at least 1 weapon frame, the route switches to guiding you back to the Singing Bowl",
		section = phaseSection,
		position = 0
	)
	default int phase1ShardTarget()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "phase1Radius",
		name = "Phase 1 radius (tiles)",
		description = "During phase 1, the route only suggests resource nodes within this many tiles of where you started - keeping you near the starting room and its neighbours",
		section = phaseSection,
		position = 1
	)
	default int phase1Radius()
	{
		return 35;
	}

	@ConfigItem(
		keyName = "singingBowlArrivalRadius",
		name = "Singing Bowl arrival radius",
		description = "How close you need to get to the Singing Bowl to trigger the switch into phase 2",
		section = phaseSection,
		position = 2
	)
	default int singingBowlArrivalRadius()
	{
		return 4;
	}

	@ConfigItem(
		keyName = "demiMaterialsTarget",
		name = "Demi-boss material target",
		description = "Combined total of Corrupted spike + Corrupted orb + Corrupted bowstring you need",
		section = phaseSection,
		position = 3
	)
	default int demiMaterialsTarget()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "includeDemiBossesInRoute",
		name = "Route to demi-bosses",
		description = "In phase 2, include demi-bosses (Corrupted Bear/Dark Beast/Dragon) in the route while you still need their materials or a weapon frame",
		section = phaseSection,
		position = 4
	)
	default boolean includeDemiBossesInRoute()
	{
		return true;
	}

	@ConfigItem(
		keyName = "doorSearchRadius",
		name = "Door search radius (tiles)",
		description = "While still pushing outward for demi-boss materials, only doors within this many tiles of your current position compete to be picked as \"most outward\" - stops a far-off door from being chosen over a much closer one just because it's technically further from the start",
		section = phaseSection,
		position = 5
	)
	default int doorSearchRadius()
	{
		return 25;
	}

	@ConfigItem(
		keyName = "includeWeakMonstersInRoute",
		name = "Route to weak monsters for weapon frame",
		description = "Killing the first Corrupted rat/spider/bat guarantees a weapon frame - include the nearest one in the route until you've gotten your first frame, then stop",
		section = phaseSection,
		position = 6
	)
	default boolean includeWeakMonstersInRoute()
	{
		return true;
	}

	@ConfigItem(
		keyName = "includeStrongMonstersInRoute",
		name = "Route to unicorns/scorpions for shards",
		description = "In phase 2, include Corrupted unicorns/scorpions in the route while you still need shards - they yield a lot per kill",
		section = phaseSection,
		position = 7
	)
	default boolean includeStrongMonstersInRoute()
	{
		return true;
	}

	@ConfigSection(
		name = "Singing Bowl",
		description = "Highlights what to make in the Singing Bowl's crafting menu",
		position = 6
	)
	String bowlSection = "bowlSection";

	@ConfigItem(
		keyName = "highlightCraftingMenu",
		name = "Highlight crafting menu options",
		description = "In the Singing Bowl's \"What would you like to make?\" menu, outlines the vial and weapon options while they're still needed - so you know which to click without hunting for them",
		section = bowlSection,
		position = 0
	)
	default boolean highlightCraftingMenu()
	{
		return true;
	}

	@ConfigItem(
		keyName = "preferredWeapon",
		name = "Weapon to build",
		description = "Which weapon's crafting option to highlight until it's attuned - the plugin has no way to know which one you want, so this isn't detected automatically",
		section = bowlSection,
		position = 1
	)
	default GauntletWeaponType preferredWeapon()
	{
		return GauntletWeaponType.NONE;
	}

	@ConfigItem(
		keyName = "craftingHighlightColor",
		name = "Crafting menu highlight color",
		description = "Color used to outline the vial/weapon options that still need making",
		section = bowlSection,
		position = 2
	)
	default Color craftingHighlightColor()
	{
		return Color.GREEN;
	}

	@ConfigItem(
		keyName = "blockDuplicateGearCrafts",
		name = "Block duplicate armor/weapon crafts",
		description = "Stops a click on a basic/attuned/perfected armor or weapon recipe you've already made once this run - e.g. clicking \"basic\" again because the first one dropped on a full inventory, instead of picking it up and upgrading it",
		section = bowlSection,
		position = 3
	)
	default boolean blockDuplicateGearCrafts()
	{
		return true;
	}

	@ConfigSection(
		name = "Custom / Advanced",
		description = "Add your own names, or fix a name that doesn't match in-game",
		position = 7
	)
	String customSection = "customSection";

	@ConfigItem(
		keyName = "customObjectNames",
		name = "Custom object names",
		description = "Comma-separated list of extra object names to outline",
		section = customSection,
		position = 0
	)
	default String customObjectNames()
	{
		return "";
	}

	@ConfigItem(
		keyName = "customNpcNames",
		name = "Custom NPC names",
		description = "Comma-separated list of extra NPC names to outline",
		section = customSection,
		position = 1
	)
	default String customNpcNames()
	{
		return "";
	}

	@ConfigItem(
		keyName = "customItemNames",
		name = "Custom item names",
		description = "Comma-separated list of extra ground item names to highlight",
		section = customSection,
		position = 2
	)
	default String customItemNames()
	{
		return "";
	}

	@ConfigItem(
		keyName = "outlineWidth",
		name = "Outline width",
		description = "Thickness of the object/NPC highlight outline",
		section = customSection,
		position = 3
	)
	default int outlineWidth()
	{
		return 2;
	}
}
