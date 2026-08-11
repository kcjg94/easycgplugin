package com.kcjg94.easycgplugin;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Highlights resource nodes, monsters and valuable ground items inside
 * The Gauntlet / The Corrupted Gauntlet.
 *
 * Matching is done by object/NPC/item NAME rather than numeric ID, since the
 * exact ids are not stable/confirmed across game updates but the names are
 * documented on the OSRS Wiki. Anything that doesn't match your game client
 * can be added via the "Custom / Advanced" config section without touching code.
 */
@Slf4j
@PluginDescriptor(
	name = "Gauntlet Highlighter",
	description = "Highlights resource nodes, monsters and valuable ground items in the (Corrupted) Gauntlet",
	tags = {"gauntlet", "corrupted", "hunllef", "highlight", "overlay", "prifddinas"}
)
public class GauntletHighlighterPlugin extends Plugin
{
	// Confirmed scenery names (OSRS Wiki: Category:The Gauntlet)
	private static final Set<String> RESOURCE_NODE_NAMES = new HashSet<>(Arrays.asList(
		"corrupt deposit",
		"corrupt phren roots",
		"corrupt grym root",
		"corrupt linum tirinum",
		"corrupt fishing spot"
	));

	// The object you use your (Corrupted) sceptre on to light up and reveal
	// the next room. The wiki only ever calls it "node" in prose, not a
	// confirmed page/object name - if this doesn't highlight in-game, add
	// the real name under Custom / Advanced -> Custom object names and it
	// will still be picked up (it goes through the same objectNames set).
	private static final Set<String> ROOM_NODE_NAMES = new HashSet<>(Arrays.asList(
		"node",
		"corrupt node",
		"corrupted node"
	));

	// Confirmed NPC names (OSRS Wiki: Category:The Gauntlet)
	private static final Set<String> DEMI_BOSS_NAMES = new HashSet<>(Arrays.asList(
		"corrupted bear",
		"corrupted dark beast",
		"corrupted dragon"
	));

	private static final Set<String> HUNLLEF_NAMES = new HashSet<>(Arrays.asList(
		"corrupted hunllef"
	));

	// Confirmed via OSRS Wiki: weak monsters (2 points each on kill) - killing
	// the first corrupted rat, spider, or bat guarantees a weapon frame drop.
	private static final Set<String> WEAK_MONSTER_NAMES = new HashSet<>(Arrays.asList(
		"corrupted bat",
		"corrupted rat",
		"corrupted spider"
	));

	// Confirmed via OSRS Wiki: strong monsters (5 points each on kill,
	// spawn in pairs). Unicorns/scorpions are farmed for their shard drops
	// once other resources are handled - see includeStrongMonstersInRoute.
	private static final Set<String> STRONG_MONSTER_NAMES = new HashSet<>(Arrays.asList(
		"corrupted unicorn",
		"corrupted scorpion",
		"corrupted wolf"
	));

	// Which strong monsters are specifically routed to for their shard
	// drops in phase 2 - the wolf isn't included since it wasn't confirmed
	// to share the unicorn/scorpion's shard yield.
	private static final Set<String> SHARD_FARM_MONSTER_NAMES = new HashSet<>(Arrays.asList(
		"corrupted unicorn",
		"corrupted scorpion"
	));

	// Item name constants, matched against ItemComposition names (lowercased).
	// Note: inside the Corrupted Gauntlet specifically, shards are named
	// "Corrupted shards" (the regular Gauntlet's are "Crystal shards").
	private static final String ITEM_ORE = "corrupted ore";
	private static final String ITEM_BARK = "phren bark";
	private static final String ITEM_TIRINUM = "linum tirinum";
	private static final String ITEM_HERB = "grym leaf";
	private static final String ITEM_PADDLEFISH = "raw paddlefish";
	private static final String ITEM_WEAPON_FRAME = "weapon frame";
	private static final String ITEM_SHARDS = "corrupted shards";
	// Note: the Corrupted Gauntlet's demi-boss materials are "Corrupted spike"
	// / "Corrupted orb", NOT the regular Gauntlet's "Crystal spike"/"Crystal orb".
	private static final String ITEM_CRYSTAL_SPIKE = "corrupted spike";
	private static final String ITEM_CRYSTAL_ORB = "corrupted orb";
	private static final String ITEM_BOWSTRING = "corrupted bowstring";

	private static final String SINGING_BOWL_NAME = "singing bowl";

	// Weapon item names at each craftable tier (OSRS Wiki: Attuned/Perfected
	// corrupted equipment). Used only to detect which weapon type(s) have
	// been started, for the dynamic shard target below - once a weapon
	// reaches "attuned" it never needs shards again (upgrading to
	// "perfected" only costs the matching demi-boss material), so either
	// name showing up at any point this run means that type is "started".
	private static final String[][] WEAPON_TYPE_NAMES = {
		{"corrupted halberd (attuned)", "corrupted halberd (perfected)"},
		{"corrupted bow (attuned)", "corrupted bow (perfected)"},
		{"corrupted staff (attuned)", "corrupted staff (perfected)"},
	};

	// Armour item names at each craftable tier, same idea as WEAPON_TYPE_NAMES.
	private static final String[][] ARMOR_TYPE_NAMES = {
		{"corrupted helm (attuned)", "corrupted helm (perfected)"},
		{"corrupted body (attuned)", "corrupted body (perfected)"},
		{"corrupted legs (attuned)", "corrupted legs (perfected)"},
	};
	private static final String[] ARMOR_BASE_NAMES = {"corrupted helm", "corrupted body", "corrupted legs"};

	// Full T3 armour set (helm+body+legs) built entirely from scratch.
	private static final int ARMOR_SHARDS_FROM_SCRATCH = 650;
	// Shards to bring one weapon to "attuned" - the only tier that costs
	// shards; attuned -> perfected is free (just the matching demi material).
	private static final int SHARDS_PER_WEAPON = 50;
	private static final int SHARDS_PER_VIAL = 10;

	private static final String ITEM_VIAL = "vial";

	// Items with a configurable target amount - once your inventory count
	// reaches the target, isItemCompleted() latches true for the rest of the
	// run and never reverts, even if the item is later consumed crafting gear.
	private static final Set<String> TRACKED_ITEM_NAMES = new HashSet<>(Arrays.asList(
		ITEM_ORE, ITEM_BARK, ITEM_TIRINUM, ITEM_HERB, ITEM_PADDLEFISH, ITEM_WEAPON_FRAME, ITEM_SHARDS
	));

	// Which inventory item each resource node produces, so we can check how
	// many you're currently carrying. Only used for the built-in nodes above;
	// custom object names added via config always highlight regardless of
	// inventory count, since we don't know what item they correspond to.
	private static final Map<String, String> RESOURCE_NODE_TO_ITEM = new HashMap<>();
	static
	{
		RESOURCE_NODE_TO_ITEM.put("corrupt deposit", ITEM_ORE);
		RESOURCE_NODE_TO_ITEM.put("corrupt phren roots", ITEM_BARK);
		RESOURCE_NODE_TO_ITEM.put("corrupt grym root", ITEM_HERB);
		RESOURCE_NODE_TO_ITEM.put("corrupt linum tirinum", ITEM_TIRINUM);
		RESOURCE_NODE_TO_ITEM.put("corrupt fishing spot", ITEM_PADDLEFISH);
	}

	// How many of its item a single node yields before depleting (OSRS
	// Wiki: The Gauntlet). Used to predict how many nodes of a given
	// resource are actually still needed, so the route doesn't queue up
	// more of the same resource than it'll take to hit the target.
	private static final Map<String, Integer> RESOURCE_NODE_YIELD = new HashMap<>();
	static
	{
		RESOURCE_NODE_YIELD.put(ITEM_ORE, 3);
		RESOURCE_NODE_YIELD.put(ITEM_BARK, 3);
		RESOURCE_NODE_YIELD.put(ITEM_TIRINUM, 3);
		RESOURCE_NODE_YIELD.put(ITEM_HERB, 1);
		RESOURCE_NODE_YIELD.put(ITEM_PADDLEFISH, 4);
	}

	// Tier-up materials and end-of-fight loot. Crystal/corrupted shards are
	// deliberately excluded - they're auto-collected straight into your
	// inventory and never actually appear as a ground item to highlight.
	private static final Set<String> GROUND_ITEM_NAMES = new HashSet<>(Arrays.asList(
		ITEM_WEAPON_FRAME,
		"corrupted spike",
		"corrupted orb",
		"corrupted bowstring",
		"crystal armour seed",
		"enhanced crystal weapon seed",
		"blade of saeldor",
		"bow of faerdhinen"
	));

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private GauntletHighlighterOverlay overlay;

	@Inject
	private GauntletHighlighterCounterOverlay counterOverlay;

	@Inject
	private GauntletHighlighterRouteOverlay routeOverlay;

	@Inject
	private GauntletHighlighterCraftingOverlay craftingOverlay;

	@Inject
	private GauntletHighlighterConfig config;

	// Value is the matched node/NPC name, not a baked color, so visibility
	// and color can be re-evaluated live against current run state each
	// frame - e.g. a rat stops being highlighted the instant the first
	// weapon frame is obtained, without waiting for it to despawn.
	private final Map<GameObject, String> highlightedObjects = new HashMap<>();
	private final Map<NPC, String> highlightedNpcs = new HashMap<>();
	private final Map<TileItem, ItemHighlight> highlightedItems = new HashMap<>();
	private final Map<String, Integer> inventoryCounts = new HashMap<>();
	private final Map<String, Integer> cumulativeCounts = new HashMap<>();
	private final Map<String, Boolean> completedFlags = new HashMap<>();
	private List<RouteStop> route = Collections.emptyList();
	private List<LocalPoint> routePath = Collections.emptyList();
	private boolean wasInInstance = false;
	private WorldPoint startLocation;
	private WorldPoint singingBowlLocation;
	private boolean phase1ReturnTriggered = false;
	private boolean phase2Started = false;
	private boolean demiMaterialsCompleted = false;
	private WorldPoint activeRoomNode;

	private Set<String> objectNames = new HashSet<>();
	private Set<String> npcNames = new HashSet<>();
	private Set<String> itemNames = new HashSet<>();

	@Provides
	GauntletHighlighterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GauntletHighlighterConfig.class);
	}

	@Override
	protected void startUp()
	{
		rebuildNameSets();
		overlayManager.add(overlay);
		overlayManager.add(counterOverlay);
		overlayManager.add(routeOverlay);
		overlayManager.add(craftingOverlay);
		clientThread.invokeLater(() -> recomputeInventoryCounts(client.getItemContainer(InventoryID.INVENTORY)));
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(counterOverlay);
		overlayManager.remove(routeOverlay);
		overlayManager.remove(craftingOverlay);
		highlightedObjects.clear();
		highlightedNpcs.clear();
		highlightedItems.clear();
		inventoryCounts.clear();
		cumulativeCounts.clear();
		completedFlags.clear();
		route = Collections.emptyList();
		routePath = Collections.emptyList();
		wasInInstance = false;
		startLocation = null;
		singingBowlLocation = null;
		phase1ReturnTriggered = false;
		phase2Started = false;
		demiMaterialsCompleted = false;
		activeRoomNode = null;
	}

	/**
	 * Whether the player is currently in a Corrupted Gauntlet instance
	 * specifically - not just any instanced region. Every overlay is gated
	 * on this so nothing lingers or misfires outside the minigame.
	 */
	boolean isInCorruptedGauntlet()
	{
		return client.getVarbitValue(VarbitID.PLAYER_IN_GAUNTLET) == 1
			&& client.getVarbitValue(VarbitID.GAUNTLET_CORRUPTED) == 1;
	}

	/**
	 * Whether the Hunllef fight has started - once it has, every
	 * overlay/route is suppressed, since the barrier's already closed
	 * behind you and there's nothing left to gather or craft for. Read
	 * directly from Jagex's own varbit rather than inferred (e.g. from
	 * combat interaction), since the Hunllef itself spawns at the very
	 * start of the instance, not on boss-room entry, so its mere presence
	 * or even engaging it briefly isn't a reliable signal on its own.
	 */
	boolean isBossFightActive()
	{
		return client.getVarbitValue(VarbitID.GAUNTLET_BOSS_STARTED) == 1;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		checkForNewInstance();
		checkForPhase2Start();
		recomputeRoute();
	}

	/**
	 * Whether every requirement for the whole run - not just phase 1's
	 * nearby resource targets - has been met: the five gatherable resources,
	 * shards, a weapon frame, and the demi-boss materials. Mirrors the
	 * checklist panel's "Supplies Ready!" condition.
	 */
	boolean isFullyStocked()
	{
		return isItemCompleted(ITEM_ORE) && isItemCompleted(ITEM_BARK) && isItemCompleted(ITEM_TIRINUM)
			&& isItemCompleted(ITEM_HERB) && isItemCompleted(ITEM_PADDLEFISH) && isItemCompleted(ITEM_WEAPON_FRAME)
			&& isItemCompleted(ITEM_SHARDS) && demiMaterialsCompleted;
	}

	/**
	 * Once phase 1's requirements are met (first weapon frame + shard target),
	 * the route points back to the Singing Bowl. Once you actually arrive
	 * there, switch permanently into phase 2 (full-map routing, including
	 * demi-bosses) - on the assumption that reaching the bowl after meeting
	 * phase 1's requirements means you've made your trip back to craft.
	 */
	private void checkForPhase2Start()
	{
		if (!phase1ReturnTriggered || phase2Started || singingBowlLocation == null)
		{
			return;
		}
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}
		int radius = config.singingBowlArrivalRadius();
		if (distanceSquared(localPlayer.getWorldLocation(), singingBowlLocation) <= radius * radius)
		{
			phase2Started = true;
		}
	}

	/**
	 * GameState.LOADING fires every time a new room's map region streams in
	 * inside the Gauntlet, not just on logout/hop - so we can't reset
	 * progress there without wiping "completed" status every time you walk
	 * into a new room. Instead, detect the transition into a freshly
	 * instanced region (i.e. actually starting a new Gauntlet attempt) and
	 * reset only then. You stay "in an instance" for the whole run, so this
	 * only fires once per attempt.
	 */
	private void checkForNewInstance()
	{
		boolean inInstance = client.isInInstancedRegion();
		if (inInstance && !wasInInstance)
		{
			completedFlags.clear();
			cumulativeCounts.clear();
			singingBowlLocation = null;
			phase1ReturnTriggered = false;
			phase2Started = false;
			demiMaterialsCompleted = false;
			Player localPlayer = client.getLocalPlayer();
			startLocation = localPlayer != null ? localPlayer.getWorldLocation() : null;
		}
		wasInInstance = inInstance;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INVENTORY.getId())
		{
			return;
		}
		recomputeInventoryCounts(event.getItemContainer());
	}

	private void recomputeInventoryCounts(ItemContainer container)
	{
		Map<String, Integer> newCounts = new HashMap<>();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() <= 0 || item.getQuantity() <= 0)
				{
					continue;
				}
				ItemComposition comp = client.getItemDefinition(item.getId());
				if (comp == null)
				{
					continue;
				}
				String name = comp.getName().toLowerCase();
				newCounts.merge(name, item.getQuantity(), Integer::sum);
			}
		}

		// Track the running total ever gathered per item, not just what's
		// currently held - items like weapon frames/ore/bark/shards are
		// consumed crafting gear (or a frame is used the moment you get one
		// rather than banking two at once), which would otherwise make the
		// checklist count back down even though it was already collected.
		// Only positive deltas count; drops in count (usage) are ignored.
		Set<String> allNames = new HashSet<>(inventoryCounts.keySet());
		allNames.addAll(newCounts.keySet());
		for (String name : allNames)
		{
			int delta = newCounts.getOrDefault(name, 0) - inventoryCounts.getOrDefault(name, 0);
			if (delta > 0)
			{
				cumulativeCounts.merge(name, delta, Integer::sum);
			}
		}

		inventoryCounts.clear();
		inventoryCounts.putAll(newCounts);

		updateCompletionFlags();
	}

	private void updateCompletionFlags()
	{
		for (String itemName : TRACKED_ITEM_NAMES)
		{
			if (itemName.equals(ITEM_SHARDS) && config.useDynamicShardTarget())
			{
				// The dynamic shard target can grow mid-run as it becomes
				// clear a demi material didn't match an already-started
				// weapon (see getDynamicShardTarget) - so unlike the other
				// targets, this one is never latched sticky-true. It's
				// recomputed live every time so it can un-complete if the
				// target rises past what's already been gathered.
				int count = cumulativeCounts.getOrDefault(itemName, 0);
				completedFlags.put(itemName, count >= getTargetFor(itemName));
				continue;
			}

			if (Boolean.TRUE.equals(completedFlags.get(itemName)))
			{
				// sticky - once complete, stays complete for the rest of the run
				continue;
			}
			int count = cumulativeCounts.getOrDefault(itemName, 0);
			if (count >= getTargetFor(itemName))
			{
				completedFlags.put(itemName, true);
			}
		}

		if (!demiMaterialsCompleted)
		{
			int demiCount = cumulativeCounts.getOrDefault(ITEM_CRYSTAL_SPIKE, 0)
				+ cumulativeCounts.getOrDefault(ITEM_CRYSTAL_ORB, 0)
				+ cumulativeCounts.getOrDefault(ITEM_BOWSTRING, 0);
			if (demiCount >= config.demiMaterialsTarget())
			{
				demiMaterialsCompleted = true;
			}
		}

		if (!phase1ReturnTriggered)
		{
			int frames = cumulativeCounts.getOrDefault(ITEM_WEAPON_FRAME, 0);
			int shards = cumulativeCounts.getOrDefault(ITEM_SHARDS, 0);
			if (frames >= 1 && shards >= config.phase1ShardTarget())
			{
				phase1ReturnTriggered = true;
			}
		}
	}

	private int getTargetFor(String itemName)
	{
		switch (itemName)
		{
			case ITEM_ORE:
				return config.oreTarget();
			case ITEM_BARK:
				return config.barkTarget();
			case ITEM_TIRINUM:
				return config.tirinumTarget();
			case ITEM_HERB:
				return config.herbTarget();
			case ITEM_PADDLEFISH:
				return config.paddlefishTarget();
			case ITEM_WEAPON_FRAME:
				return config.weaponFrameTarget();
			case ITEM_SHARDS:
				return config.useDynamicShardTarget() ? getDynamicShardTarget() : config.shardsTarget();
			default:
				return Integer.MAX_VALUE;
		}
	}

	/**
	 * How many distinct weapon types (halberd/bow/staff) have been started
	 * this run - i.e. brought to at least "attuned" at any point, tracked
	 * via the sticky {@link #cumulativeCounts} so it doesn't un-count once
	 * an attuned weapon is consumed upgrading to perfected.
	 */
	private int countStartedWeaponTypes()
	{
		int count = 0;
		for (String[] namesForType : WEAPON_TYPE_NAMES)
		{
			for (String name : namesForType)
			{
				if (cumulativeCounts.getOrDefault(name, 0) > 0)
				{
					count++;
					break;
				}
			}
		}
		return count;
	}

	// GauntletWeaponType.HALBERD/BOW/STAFF ordinal-1 indexes into
	// WEAPON_TYPE_NAMES/WEAPON_BASE_NAMES (NONE has no entry).
	private static final int[] WEAPON_TYPE_INDEX = {-1, 0, 1, 2};

	// The crafting menu only ever shows one tier at a time per weapon - the
	// next one actually craftable - starting with "(basic)", so matching
	// needs the bare name (no tier suffix) rather than a specific tier.
	private static final String[] WEAPON_BASE_NAMES = {
		"corrupted halberd",
		"corrupted bow",
		"corrupted staff",
	};

	/**
	 * The bare item name (no tier suffix) for {@link GauntletHighlighterConfig#preferredWeapon()},
	 * or null if it's set to NONE. Used by the crafting-menu overlay to
	 * find the matching recipe entry, whichever tier it's currently on.
	 */
	String getPreferredWeaponBaseName()
	{
		GauntletWeaponType weapon = config.preferredWeapon();
		int index = WEAPON_TYPE_INDEX[weapon.ordinal()];
		return index < 0 ? null : WEAPON_BASE_NAMES[index];
	}

	/**
	 * Whether the preferred weapon (see {@link GauntletHighlighterConfig#preferredWeapon()})
	 * still needs to be brought to attuned - i.e. it hasn't been started at
	 * all yet this run. False if no preferred weapon is configured.
	 */
	boolean isWeaponCraftingNeeded()
	{
		GauntletWeaponType weapon = config.preferredWeapon();
		int index = WEAPON_TYPE_INDEX[weapon.ordinal()];
		if (index < 0)
		{
			return false;
		}
		for (String name : WEAPON_TYPE_NAMES[index])
		{
			if (cumulativeCounts.getOrDefault(name, 0) > 0)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether more vials still need making, per {@link GauntletHighlighterConfig#vialCount()}.
	 */
	boolean isVialCraftingNeeded()
	{
		return getItemCount(ITEM_VIAL) < config.vialCount();
	}

	/**
	 * Whether the final crafting pass should be highlighted: once every
	 * requirement for the run is met, there's nothing left to do but turn
	 * it all into gear at the bowl - full T3 armor plus whichever weapons
	 * were actually started.
	 */
	boolean isFinalCraftingPhase()
	{
		return isFullyStocked();
	}

	/**
	 * Bare names (helm/body/legs) of armor pieces not yet perfected -
	 * whichever tier the crafting menu is currently offering for each is
	 * still worth highlighting during the final crafting phase.
	 */
	List<String> getArmorBaseNamesNeeded()
	{
		List<String> needed = new ArrayList<>();
		for (int i = 0; i < ARMOR_BASE_NAMES.length; i++)
		{
			if (cumulativeCounts.getOrDefault(ARMOR_TYPE_NAMES[i][1], 0) == 0)
			{
				needed.add(ARMOR_BASE_NAMES[i]);
			}
		}
		return needed;
	}

	/**
	 * Bare names of weapon types that were actually started (an attuned or
	 * perfected copy has been seen this run) but haven't reached perfected
	 * yet - the ones actually worth pushing to T3, as opposed to a weapon
	 * type that was never picked up a demi material for.
	 */
	List<String> getWeaponBaseNamesNeeded()
	{
		List<String> needed = new ArrayList<>();
		for (int i = 0; i < WEAPON_BASE_NAMES.length; i++)
		{
			boolean started = cumulativeCounts.getOrDefault(WEAPON_TYPE_NAMES[i][0], 0) > 0
				|| cumulativeCounts.getOrDefault(WEAPON_TYPE_NAMES[i][1], 0) > 0;
			boolean perfected = cumulativeCounts.getOrDefault(WEAPON_TYPE_NAMES[i][1], 0) > 0;
			if (started && !perfected)
			{
				needed.add(WEAPON_BASE_NAMES[i]);
			}
		}
		return needed;
	}

	// Which weapon a demi-boss material is used on: spike->halberd,
	// bowstring->bow, orb->staff. Index matches WEAPON_BASE_NAMES.
	private static final String[] DEMI_MATERIAL_NAMES = {ITEM_CRYSTAL_SPIKE, ITEM_BOWSTRING, ITEM_CRYSTAL_ORB};

	/**
	 * Bare names of weapon types whose matching demi-boss material is
	 * currently sitting in your inventory - worth highlighting regardless
	 * of run phase or the configured preferred weapon, since holding the
	 * material means there's an immediate reason to work on that weapon.
	 * Uses the live {@link #inventoryCounts}, not the sticky cumulative
	 * count, since this should stop once the material is actually used.
	 * <p>
	 * Skips a weapon type that's already been perfected, even if you're
	 * still holding a spare of its material (e.g. a second bowstring from
	 * killing two dark beasts) - the crafting menu keeps offering
	 * "(basic)" for that type as long as you hold a spare weapon frame,
	 * regardless of already having a perfected one from a different frame,
	 * so without this check a leftover material would highlight a second,
	 * unwanted "(basic)" craft right after finishing the weapon.
	 */
	List<String> getWeaponBaseNamesForHeldMaterials()
	{
		List<String> result = new ArrayList<>();
		for (int i = 0; i < DEMI_MATERIAL_NAMES.length; i++)
		{
			boolean perfected = cumulativeCounts.getOrDefault(WEAPON_TYPE_NAMES[i][1], 0) > 0;
			if (inventoryCounts.getOrDefault(DEMI_MATERIAL_NAMES[i], 0) > 0 && !perfected)
			{
				result.add(WEAPON_BASE_NAMES[i]);
			}
		}
		return result;
	}

	/**
	 * Whether {@code itemName} (an exact tier name, e.g. "corrupted staff
	 * (basic)") is an armor/weapon piece that's already been crafted once
	 * this run. Only armor and weapons are covered - each of those is only
	 * ever needed once per tier per run, unlike vials/paddlefish/teleport
	 * crystals, which are legitimately made more than once. Uses the sticky
	 * {@link #cumulativeCounts}, so it still catches the case where the
	 * item was made, then dropped on a full inventory - the crafting menu
	 * reverts to offering that same tier again since it only looks at
	 * current inventory, but this remembers it was already made.
	 */
	boolean isDuplicateGearCraft(String itemName)
	{
		boolean isGearItem = false;
		for (String base : ARMOR_BASE_NAMES)
		{
			if (itemName.startsWith(base))
			{
				isGearItem = true;
				break;
			}
		}
		if (!isGearItem)
		{
			for (String base : WEAPON_BASE_NAMES)
			{
				if (itemName.startsWith(base))
				{
					isGearItem = true;
					break;
				}
			}
		}
		if (!isGearItem)
		{
			return false;
		}
		return cumulativeCounts.getOrDefault(itemName, 0) > 0;
	}

	/**
	 * Live shard target: full T3 armor + vials + 50 per weapon actually
	 * needed. Starts at the planned {@link GauntletHighlighterConfig#demiMaterialsTarget()}-weapon
	 * estimate (2 by default) and only grows past that once a demi material
	 * turns out not to match a weapon already started - e.g. you started a
	 * staff, but your first material is a spike (halberd) - which needs an
	 * extra weapon built from scratch and so an extra 50 shards. Recomputed
	 * live, so it corrects itself as materials actually drop rather than
	 * guessing the worst case up front.
	 */
	int getDynamicShardTarget()
	{
		int weaponsNeeded = Math.max(config.demiMaterialsTarget(), countStartedWeaponTypes());
		return ARMOR_SHARDS_FROM_SCRATCH
			+ config.vialCount() * SHARDS_PER_VIAL
			+ weaponsNeeded * SHARDS_PER_WEAPON;
	}

	/**
	 * Whether the target for this item has been reached at any point this run.
	 * Sticky: once true, stays true even if the item is later consumed
	 * (e.g. crafting armour uses up ore/bark/tirinum).
	 */
	boolean isItemCompleted(String itemName)
	{
		return Boolean.TRUE.equals(completedFlags.get(itemName));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("gauntlethighlighter"))
		{
			rebuildNameSets();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOADING || state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			highlightedObjects.clear();
			highlightedNpcs.clear();
			highlightedItems.clear();
			route = Collections.emptyList();
			routePath = Collections.emptyList();
		}
	}

	// Matches the <col=...> / </col> tags Jagex wraps menu target text in.
	private static final Pattern MENU_TARGET_TAGS = Pattern.compile("<[^>]+>");

	/**
	 * Blocks clicking a "make X" option in the Singing Bowl's crafting menu
	 * (widget group 270, {@link InterfaceID#SKILLMULTI}) for an armor/weapon
	 * tier already crafted once this run - see {@link #isDuplicateGearCraft}
	 * for why this catches the "dropped on a full inventory, then
	 * re-crafted by accident" case that a purely visual warning wouldn't.
	 * <p>
	 * The item's identity isn't available as an item ID for this click -
	 * {@code event.getItemId()} and the widget's own item ID both come back
	 * empty for this interface (confirmed by logging real clicks) - but the
	 * plain item name is right there in the menu's target text (e.g.
	 * {@code <col=ff9040>Corrupted bow (basic)</col>}), so that's parsed
	 * directly instead.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!config.blockDuplicateGearCrafts() || !isInCorruptedGauntlet())
		{
			return;
		}

		Widget widget = event.getWidget();
		if (widget == null || WidgetUtil.componentToInterface(widget.getId()) != InterfaceID.SKILLMULTI)
		{
			return;
		}

		if (!"Make".equals(event.getMenuOption()))
		{
			return;
		}

		String rawTarget = event.getMenuTarget();
		if (rawTarget == null)
		{
			return;
		}

		String displayName = MENU_TARGET_TAGS.matcher(rawTarget).replaceAll("").trim();
		String name = displayName.toLowerCase();
		if (isDuplicateGearCraft(name))
		{
			event.consume();
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"Gauntlet Highlighter: blocked - you already made a " + displayName + " this run.", null);
		}
	}

	private void rebuildNameSets()
	{
		objectNames = new HashSet<>(RESOURCE_NODE_NAMES);
		addCustom(objectNames, config.customObjectNames());

		npcNames = new HashSet<>();
		npcNames.addAll(WEAK_MONSTER_NAMES);
		npcNames.addAll(STRONG_MONSTER_NAMES);
		npcNames.addAll(DEMI_BOSS_NAMES);
		npcNames.addAll(HUNLLEF_NAMES);
		addCustom(npcNames, config.customNpcNames());

		itemNames = new HashSet<>(GROUND_ITEM_NAMES);
		addCustom(itemNames, config.customItemNames());
	}

	private void addCustom(Set<String> set, String csv)
	{
		if (csv == null || csv.trim().isEmpty())
		{
			return;
		}
		for (String s : csv.split(","))
		{
			String trimmed = s.trim().toLowerCase();
			if (!trimmed.isEmpty())
			{
				set.add(trimmed);
			}
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject gameObject = event.getGameObject();

		String rawName = getObjectName(gameObject);

		if (singingBowlLocation == null)
		{
			if (rawName != null && rawName.equals(SINGING_BOWL_NAME))
			{
				singingBowlLocation = gameObject.getWorldLocation();
			}
		}

		if (rawName != null && ROOM_NODE_NAMES.contains(rawName))
		{
			// Handled separately from the resource-node toggle/color below -
			// this gates room-to-room progression, so it stays on and gets
			// included in the route regardless of the resource settings.
			if (config.highlightRoomNodes())
			{
				highlightedObjects.put(gameObject, rawName);
			}
			return;
		}

		if (!config.highlightResources())
		{
			return;
		}

		String name = rawName;
		if (name == null)
		{
			return;
		}

		boolean depleted = name.contains("depleted");
		if (depleted && !config.highlightDepleted())
		{
			return;
		}

		String stripped = depleted ? name.replace("(depleted)", "").trim() : name;
		if (objectNames.contains(stripped))
		{
			highlightedObjects.put(gameObject, stripped);
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		highlightedObjects.remove(event.getGameObject());
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		String name = npc.getName();
		if (name == null)
		{
			return;
		}
		name = name.toLowerCase();

		if (npcNames.contains(name))
		{
			highlightedNpcs.put(npc, name);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		highlightedNpcs.remove(event.getNpc());
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		if (!config.highlightGroundItems())
		{
			return;
		}

		TileItem item = event.getItem();
		String name = getItemName(item);
		if (name == null || !itemNames.contains(name))
		{
			return;
		}

		if (name.equals(ITEM_WEAPON_FRAME) && config.limitByInventory() && isItemCompleted(ITEM_WEAPON_FRAME))
		{
			return;
		}

		highlightedItems.put(item, new ItemHighlight(event.getTile(), config.groundItemColor()));
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		highlightedItems.remove(event.getItem());
	}

	private String getObjectName(GameObject gameObject)
	{
		ObjectComposition comp = client.getObjectDefinition(gameObject.getId());
		if (comp == null)
		{
			return null;
		}
		if (comp.getImpostorIds() != null)
		{
			ObjectComposition impostor = comp.getImpostor();
			if (impostor != null)
			{
				comp = impostor;
			}
		}
		String name = comp.getName();
		if (name == null || name.equalsIgnoreCase("null"))
		{
			return null;
		}
		return name.toLowerCase();
	}

	private String getItemName(TileItem item)
	{
		ItemComposition comp = client.getItemDefinition(item.getId());
		if (comp == null)
		{
			return null;
		}
		return comp.getName().toLowerCase();
	}

	Map<GameObject, String> getHighlightedObjects()
	{
		return highlightedObjects;
	}

	Color getResourceColor()
	{
		return config.resourceColor();
	}

	Color getObjectColor(String nodeName)
	{
		if (ROOM_NODE_NAMES.contains(nodeName))
		{
			return config.roomNodeColor();
		}
		return config.resourceColor();
	}

	boolean isRoomNodeName(String name)
	{
		return ROOM_NODE_NAMES.contains(name);
	}

	WorldPoint getActiveRoomNode()
	{
		return activeRoomNode;
	}

	/**
	 * Whether a resource node (identified by its matched scenery name) should
	 * currently be drawn. If "stop once target reached" is on and the node
	 * maps to a known item, it's hidden once that item's target has been
	 * reached (permanently, for the rest of the run). Nodes with no known
	 * item mapping (e.g. custom names) are always shown.
	 */
	boolean isNodeVisible(String nodeName)
	{
		if (!config.limitByInventory())
		{
			return true;
		}

		String itemName = RESOURCE_NODE_TO_ITEM.get(nodeName);
		if (itemName == null)
		{
			return true;
		}

		return !isItemCompleted(itemName);
	}

	int getItemCount(String itemNameLower)
	{
		return cumulativeCounts.getOrDefault(itemNameLower, 0);
	}

	int getItemTarget(String itemNameLower)
	{
		return getTargetFor(itemNameLower);
	}

	Map<NPC, String> getHighlightedNpcs()
	{
		return highlightedNpcs;
	}

	Map<TileItem, ItemHighlight> getHighlightedItems()
	{
		return highlightedItems;
	}

	private boolean isStillHuntingDemiBoss()
	{
		return !demiMaterialsCompleted || !isItemCompleted(ITEM_WEAPON_FRAME);
	}

	/**
	 * Whether an NPC (identified by its matched name) should currently be
	 * outlined - this is a routing plugin, not a "highlight every creature"
	 * one, so each category is only shown while it's actually still useful:
	 * demi-bosses while their drops are still needed, weak monsters
	 * (rat/spider/bat) until the first weapon frame is obtained, and
	 * unicorns/scorpions only in phase 2 while shards are still needed.
	 * Monsters with no tracked purpose (e.g. the wolf) are never
	 * highlighted. The Hunllef has no "need" condition - it's always shown
	 * while its toggle is on. Custom name entries have no known category,
	 * so they fall back to always-visible while the monster toggle is on.
	 */
	boolean isNpcVisible(String name)
	{
		if (HUNLLEF_NAMES.contains(name))
		{
			return config.highlightHunllef();
		}
		if (DEMI_BOSS_NAMES.contains(name))
		{
			return config.highlightDemiBosses() && isStillHuntingDemiBoss();
		}
		if (WEAK_MONSTER_NAMES.contains(name))
		{
			return config.highlightMonsters() && getItemCount(ITEM_WEAPON_FRAME) < 1;
		}
		if (SHARD_FARM_MONSTER_NAMES.contains(name))
		{
			return config.highlightMonsters() && phase2Started && !isItemCompleted(ITEM_SHARDS);
		}
		if (STRONG_MONSTER_NAMES.contains(name))
		{
			// e.g. the wolf - no tracked need for it, so nothing to show
			return false;
		}
		// matched only via a custom name entry - no known category/need,
		// so just respect the general monster toggle
		return config.highlightMonsters();
	}

	Color getNpcColor(String name)
	{
		if (HUNLLEF_NAMES.contains(name))
		{
			return config.hunllefColor();
		}
		if (DEMI_BOSS_NAMES.contains(name))
		{
			return config.demiBossColor();
		}
		return config.monsterColor();
	}

	private void recomputeRoute()
	{
		if (!isInCorruptedGauntlet() || isBossFightActive())
		{
			route = Collections.emptyList();
			routePath = Collections.emptyList();
			activeRoomNode = null;
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			route = Collections.emptyList();
			routePath = Collections.emptyList();
			activeRoomNode = null;
			return;
		}

		WorldPoint playerLocation = localPlayer.getWorldLocation();

		// Once shard/frame targets are hit, the route heads back to the
		// Singing Bowl - but not before finishing whatever's still sitting
		// nearby in the current room. The bowl is appended as the final
		// stop below, same as the door, rather than replacing the route
		// outright and yanking the player away mid-room.
		boolean returningToBowl = phase1ReturnTriggered && !phase2Started && singingBowlLocation != null;

		List<RouteStop> remaining = new ArrayList<>();
		boolean phase1Gathering = !phase1ReturnTriggered;

		// Candidate resource nodes, grouped by the item they produce so the
		// group can be capped below to only as many as are actually needed.
		Map<String, List<RouteStop>> candidatesByItem = new HashMap<>();

		for (Map.Entry<GameObject, String> entry : highlightedObjects.entrySet())
		{
			if (ROOM_NODE_NAMES.contains(entry.getValue()))
			{
				// Room nodes are handled separately below - only the single
				// nearest one is routed to, not every node currently loaded.
				continue;
			}
			if (!isNodeVisible(entry.getValue()))
			{
				continue;
			}
			WorldPoint wp = entry.getKey().getWorldLocation();
			if (wp == null)
			{
				continue;
			}
			if (distanceSquared(playerLocation, wp) > config.maxGatherDistance() * config.maxGatherDistance())
			{
				// Too far from where you're actually standing right now -
				// you've clearly moved on/skipped it, so don't drag the
				// route back to it just because it's still loaded in memory.
				continue;
			}
			if (phase1Gathering && startLocation != null
				&& distanceSquared(startLocation, wp) > config.phase1Radius() * config.phase1Radius())
			{
				// Phase 1: stick to the starting room and its neighbours -
				// don't suggest a trip across the map yet.
				continue;
			}

			String itemName = RESOURCE_NODE_TO_ITEM.get(entry.getValue());
			RouteStop candidate = new RouteStop(wp, entry.getValue());
			if (itemName == null)
			{
				// Custom/unmapped node name - no known item/yield to
				// predict against, so just include it directly.
				remaining.add(candidate);
				continue;
			}
			candidatesByItem.computeIfAbsent(itemName, k -> new ArrayList<>()).add(candidate);
		}

		// Each node only yields a fixed amount before depleting (e.g. 3 ore
		// per deposit), so cap how many nodes of a given resource are
		// queued to however many are actually needed to reach the target -
		// nearest first - rather than every currently-loaded node of that
		// type. If inventory limiting is off, every candidate is kept.
		for (Map.Entry<String, List<RouteStop>> group : candidatesByItem.entrySet())
		{
			String itemName = group.getKey();
			List<RouteStop> candidates = group.getValue();

			if (!config.limitByInventory())
			{
				remaining.addAll(candidates);
				continue;
			}

			candidates.sort(Comparator.comparingInt(c -> distanceSquared(playerLocation, c.getPoint())));

			int stillNeeded = getTargetFor(itemName) - getItemCount(itemName);
			int yieldPerNode = RESOURCE_NODE_YIELD.getOrDefault(itemName, 1);
			int nodesNeeded = stillNeeded <= 0 ? 0 : (stillNeeded + yieldPerNode - 1) / yieldPerNode;

			for (int i = 0; i < Math.min(nodesNeeded, candidates.size()); i++)
			{
				remaining.add(candidates.get(i));
			}
		}

		// Phase 2: also route to demi-bosses while their materials/weapon
		// frames are still needed, so you can find them on the outer rim.
		if (phase2Started && config.includeDemiBossesInRoute() && isStillHuntingDemiBoss())
		{
			for (Map.Entry<NPC, String> entry : highlightedNpcs.entrySet())
			{
				if (!DEMI_BOSS_NAMES.contains(entry.getValue()))
				{
					continue;
				}
				WorldPoint wp = entry.getKey().getWorldLocation();
				if (wp == null)
				{
					continue;
				}
				remaining.add(new RouteStop(wp, "demi-boss"));
			}
		}

		// Weak monsters (rat/spider/bat): the first kill among these three
		// guarantees a weapon frame drop, so route to the single nearest
		// one until that first frame is actually obtained - after that,
		// killing more of them isn't a reliable source of extra frames.
		if (config.includeWeakMonstersInRoute() && getItemCount(ITEM_WEAPON_FRAME) < 1)
		{
			RouteStop nearestWeak = null;
			int bestWeakDistSq = Integer.MAX_VALUE;
			for (Map.Entry<NPC, String> entry : highlightedNpcs.entrySet())
			{
				if (!WEAK_MONSTER_NAMES.contains(entry.getValue()))
				{
					continue;
				}
				WorldPoint wp = entry.getKey().getWorldLocation();
				if (wp == null)
				{
					continue;
				}
				int distSq = distanceSquared(playerLocation, wp);
				if (distSq > config.maxGatherDistance() * config.maxGatherDistance())
				{
					continue;
				}
				if (phase1Gathering && startLocation != null
					&& distanceSquared(startLocation, wp) > config.phase1Radius() * config.phase1Radius())
				{
					continue;
				}
				if (distSq < bestWeakDistSq)
				{
					bestWeakDistSq = distSq;
					nearestWeak = new RouteStop(wp, "weapon frame kill");
				}
			}
			if (nearestWeak != null)
			{
				remaining.add(nearestWeak);
			}
		}

		// Strong monsters (unicorn/scorpion): once in phase 2, route to
		// them while shards are still needed - they yield a lot per kill.
		if (phase2Started && config.includeStrongMonstersInRoute() && !isItemCompleted(ITEM_SHARDS))
		{
			for (Map.Entry<NPC, String> entry : highlightedNpcs.entrySet())
			{
				if (!SHARD_FARM_MONSTER_NAMES.contains(entry.getValue()))
				{
					continue;
				}
				WorldPoint wp = entry.getKey().getWorldLocation();
				if (wp == null || distanceSquared(playerLocation, wp) > config.maxGatherDistance() * config.maxGatherDistance())
				{
					continue;
				}
				remaining.add(new RouteStop(wp, "shard farm"));
			}
		}

		List<RouteStop> ordered = new ArrayList<>(remaining.size() + 1);
		WorldPoint current = playerLocation;

		// Both the greedy build below and the 2-opt cleanup rank candidates
		// by actual walking distance (see pathDistance), not straight-line
		// distance - a stop that's close as the crow flies can still be a
		// long detour around a wall, so straight-line ranking can pick the
		// wrong "nearest" stop or leave a genuinely closer one for later.
		// The same point pairs recur a lot across both passes, so results
		// are cached for the duration of this recompute.
		Map<String, Integer> pathDistanceCache = new HashMap<>();

		while (!remaining.isEmpty())
		{
			RouteStop nearest = null;
			int bestDist = Integer.MAX_VALUE;
			for (RouteStop candidate : remaining)
			{
				if (candidate.getPoint().getPlane() != current.getPlane())
				{
					continue;
				}
				int dist = pathDistance(current, candidate.getPoint(), pathDistanceCache);
				if (dist < bestDist)
				{
					bestDist = dist;
					nearest = candidate;
				}
			}

			if (nearest == null)
			{
				// nothing left on the current plane
				break;
			}

			ordered.add(nearest);
			remaining.remove(nearest);
			current = nearest.getPoint();
		}

		// Greedy nearest-neighbor alone can zigzag (skip past a close stop
		// to reach a farther one, then double back for it later) - clean
		// that up before the door/bowl gets appended below, so it stays
		// the final stop rather than being reordered by this pass.
		twoOptImprove(playerLocation, ordered, pathDistanceCache);

		if (returningToBowl)
		{
			// Everything nearby is already queued above - the bowl is the
			// very last stop, once the current room is actually finished.
			activeRoomNode = null;
			ordered.add(new RouteStop(singingBowlLocation, SINGING_BOWL_NAME));
		}
		else
		{
			// The door comes last, after every item that's actually
			// reachable right now. Suppressed once everything for the whole
			// run is stocked (not just phase 1's nearby resources) and
			// you've reached phase 2 - at that point there's nothing left
			// to explore new rooms for.
			boolean suppressDoor = phase2Started && isFullyStocked();
			RouteStop doorStop;
			if (suppressDoor)
			{
				doorStop = null;
			}
			else if (phase2Started && isStillHuntingDemiBoss())
			{
				// Still need a demi-boss drop: push toward the rim, but
				// findOutermostRoomNodeStop weighs travel cost against
				// outward gain rather than just grabbing the single
				// farthest door, so this still favors a close, newly
				// discoverable room over a distant detour.
				doorStop = findOutermostRoomNodeStop(current);
			}
			else
			{
				// Either still in phase 1, phase 2 but still need more
				// resources, or phase 2 with the demi-boss materials/weapon
				// frame already done - favor the nearest door so rooms get
				// explored (and gathered from) room-by-room instead of
				// skipped past on the way to somewhere farther.
				doorStop = findNearestRoomNodeStop(current);

				if (doorStop != null && phase1Gathering && startLocation != null
					&& distanceSquared(startLocation, doorStop.getPoint()) > config.phase1Radius() * config.phase1Radius())
				{
					// Phase 1: the same "stick to the starting room and its
					// neighbours" boundary already applied to resource nodes
					// and weak monsters also applies to the door - otherwise
					// it keeps nudging you into fresh rooms every time the
					// current one is cleared, well past the intended zone.
					doorStop = null;
				}
			}
			activeRoomNode = doorStop == null ? null : doorStop.getPoint();
			if (activeRoomNode != null)
			{
				ordered.add(new RouteStop(activeRoomNode, "room node"));
			}
		}

		if (ordered.isEmpty())
		{
			route = Collections.emptyList();
			routePath = Collections.emptyList();
			return;
		}

		if (!config.showRoute())
		{
			route = Collections.emptyList();
			routePath = Collections.emptyList();
			return;
		}

		route = ordered;
		routePath = buildRoutePath(playerLocation, ordered);
	}

	/**
	 * Builds the polyline drawn on screen: the player's position, followed by
	 * a collision-aware walking path (see {@link #findPath}) through each
	 * stop in order. Pathing through real collision data - rather than a
	 * straight line - means the route naturally passes through whatever gap
	 * in a room's walls actually connects to the next room, instead of
	 * cutting through the wall.
	 */
	private List<LocalPoint> buildRoutePath(WorldPoint from, List<RouteStop> stops)
	{
		List<LocalPoint> path = new ArrayList<>();
		LocalPoint startLp = LocalPoint.fromWorld(client, from);
		if (startLp != null)
		{
			path.add(startLp);
		}

		WorldPoint cursor = from;
		for (RouteStop stop : stops)
		{
			List<LocalPoint> leg = findPath(cursor, stop.getPoint());
			if (leg != null)
			{
				path.addAll(leg);
			}
			else
			{
				// Unreachable via the collision map (e.g. destination room not
				// loaded yet) - fall back to a direct line for this leg only.
				LocalPoint lp = LocalPoint.fromWorld(client, stop.getPoint());
				if (lp != null)
				{
					path.add(lp);
				}
			}
			cursor = stop.getPoint();
		}
		return path;
	}

	// Upper bound on tiles visited per leg, so a leg with no route (e.g. an
	// unloaded destination room) can't stall the client thread searching the
	// full 104x104 scene every tick.
	private static final int PATH_SEARCH_LIMIT = 8000;

	/**
	 * Breadth-first search over the loaded scene's collision flags, so the
	 * drawn route walks through actual doorways/gaps in room walls rather
	 * than a straight line drawn through them. Returns waypoints from the
	 * tile after {@code startWp} up to and including {@code endWp}, or null
	 * if unreachable (different planes, outside the loaded scene, or no path
	 * found within {@link #PATH_SEARCH_LIMIT} tiles).
	 */
	private List<LocalPoint> findPath(WorldPoint startWp, WorldPoint endWp)
	{
		if (startWp.getPlane() != endWp.getPlane())
		{
			return null;
		}

		WorldView wv = client.getTopLevelWorldView();
		CollisionData[] maps = wv.getCollisionMaps();
		if (maps == null)
		{
			return null;
		}
		CollisionData collisionData = maps[startWp.getPlane()];
		if (collisionData == null)
		{
			return null;
		}
		int[][] flags = collisionData.getFlags();

		LocalPoint startLp = LocalPoint.fromWorld(wv, startWp);
		LocalPoint endLp = LocalPoint.fromWorld(wv, endWp);
		if (startLp == null || endLp == null)
		{
			return null;
		}

		int startX = startLp.getSceneX();
		int startY = startLp.getSceneY();
		int endX = endLp.getSceneX();
		int endY = endLp.getSceneY();

		if (startX == endX && startY == endY)
		{
			return Collections.emptyList();
		}

		int size = Constants.SCENE_SIZE;
		boolean[][] visited = new boolean[size][size];
		int[][] parentX = new int[size][size];
		int[][] parentY = new int[size][size];
		Deque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[]{startX, startY});
		visited[startX][startY] = true;

		int[] dx = {0, 1, 0, -1};
		int[] dy = {1, 0, -1, 0};
		int[] blockFlag = {
			CollisionDataFlag.BLOCK_MOVEMENT_NORTH,
			CollisionDataFlag.BLOCK_MOVEMENT_EAST,
			CollisionDataFlag.BLOCK_MOVEMENT_SOUTH,
			CollisionDataFlag.BLOCK_MOVEMENT_WEST
		};

		boolean found = false;
		int visitedCount = 1;

		while (!queue.isEmpty())
		{
			int[] cur = queue.poll();
			int cx = cur[0];
			int cy = cur[1];

			if (cx == endX && cy == endY)
			{
				found = true;
				break;
			}

			if (visitedCount > PATH_SEARCH_LIMIT)
			{
				break;
			}

			int curFlags = flags[cx][cy];
			for (int dir = 0; dir < 4; dir++)
			{
				int nx = cx + dx[dir];
				int ny = cy + dy[dir];
				if (nx < 0 || nx >= size || ny < 0 || ny >= size || visited[nx][ny])
				{
					continue;
				}
				if ((curFlags & blockFlag[dir]) != 0 || (flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0)
				{
					continue;
				}
				visited[nx][ny] = true;
				parentX[nx][ny] = cx;
				parentY[nx][ny] = cy;
				visitedCount++;
				queue.add(new int[]{nx, ny});
			}
		}

		if (!found)
		{
			return null;
		}

		List<LocalPoint> path = new ArrayList<>();
		int cx = endX;
		int cy = endY;
		while (!(cx == startX && cy == startY))
		{
			path.add(LocalPoint.fromScene(cx, cy, wv));
			int pcx = parentX[cx][cy];
			int pcy = parentY[cx][cy];
			cx = pcx;
			cy = pcy;
		}
		Collections.reverse(path);
		return path;
	}

	// How close two room-node objects need to be to count as the same
	// doorway's redundant pair, rather than two separate doorways.
	private static final int ROOM_NODE_PAIR_DISTANCE_SQ = 9; // 3 tiles

	/**
	 * Collapses every currently-loaded room node down to one representative
	 * per doorway - each doorway spawns two redundant nodes (lighting either
	 * one opens it), so within a pair only whichever is closer to
	 * {@code pairReference} is kept, since either is equally valid to walk to.
	 */
	private List<WorldPoint> collapseRoomNodePairs(WorldPoint pairReference)
	{
		List<WorldPoint> nodePoints = new ArrayList<>();
		for (Map.Entry<GameObject, String> entry : highlightedObjects.entrySet())
		{
			if (!ROOM_NODE_NAMES.contains(entry.getValue()))
			{
				continue;
			}
			WorldPoint wp = entry.getKey().getWorldLocation();
			if (wp != null && wp.getPlane() == pairReference.getPlane())
			{
				nodePoints.add(wp);
			}
		}

		List<WorldPoint> representatives = new ArrayList<>();
		boolean[] used = new boolean[nodePoints.size()];
		for (int i = 0; i < nodePoints.size(); i++)
		{
			if (used[i])
			{
				continue;
			}
			WorldPoint representative = nodePoints.get(i);
			int representativeDistSq = distanceSquared(pairReference, representative);
			used[i] = true;
			for (int j = i + 1; j < nodePoints.size(); j++)
			{
				if (used[j] || distanceSquared(nodePoints.get(i), nodePoints.get(j)) > ROOM_NODE_PAIR_DISTANCE_SQ)
				{
					continue;
				}
				used[j] = true;
				int distSq = distanceSquared(pairReference, nodePoints.get(j));
				if (distSq < representativeDistSq)
				{
					representativeDistSq = distSq;
					representative = nodePoints.get(j);
				}
			}
			representatives.add(representative);
		}

		return representatives;
	}

	/**
	 * Picks the still-unlit room node nearest to {@code from} - normally the
	 * last stop in the ordered gathering route (or the player, if there are
	 * no other stops), so the door picked is the one nearest to wherever
	 * gathering actually finishes. Used during phase 1.
	 */
	private RouteStop findNearestRoomNodeStop(WorldPoint from)
	{
		List<WorldPoint> representatives = collapseRoomNodePairs(from);

		WorldPoint nearest = null;
		int bestDistSq = Integer.MAX_VALUE;
		for (WorldPoint wp : representatives)
		{
			int distSq = distanceSquared(from, wp);
			if (distSq < bestDistSq)
			{
				bestDistSq = distSq;
				nearest = wp;
			}
		}

		return nearest == null ? null : new RouteStop(nearest, "room node");
	}

	/**
	 * Among the still-unlit room nodes within {@link GauntletHighlighterConfig#doorSearchRadius()}
	 * of {@code from}, picks whichever is farthest from {@link #startLocation}
	 * - i.e. pushes furthest out toward the map's edge among the nearby,
	 * practical options - rather than the single farthest node loaded
	 * anywhere in the scene, which could mean a costly detour back across
	 * already-explored ground just because it happens to sit further from
	 * the start in a straight line. Doorway pairs are still collapsed
	 * relative to {@code from} first, so within a pair the closer of the two
	 * (equally valid) nodes is the one considered. Used during phase 2 while
	 * still hunting demi-boss materials, where demi-bosses spawn on the rim.
	 */
	/**
	 * Picks the still-unlit room node within {@link GauntletHighlighterConfig#doorSearchRadius()}
	 * of {@code from} that best trades off "how much further out does this
	 * push me" against "how far do I have to walk to get there" - not
	 * simply whichever candidate sits farthest from {@link #startLocation}.
	 * Each candidate scores {@code outwardGain - travelCost}, where
	 * outwardGain is how much farther from start the door is than
	 * {@code from} already is (negative if it'd actually be a step back
	 * inward) and travelCost is the distance to reach it - so a door a few
	 * tiles away that's nearly as outward beats one on the far side of the
	 * search radius that's only marginally more outward, keeping newly
	 * discoverable nearby rooms in the running instead of always chasing
	 * the single most distant option.
	 */
	private RouteStop findOutermostRoomNodeStop(WorldPoint from)
	{
		List<WorldPoint> representatives = collapseRoomNodePairs(from);
		if (representatives.isEmpty() || startLocation == null)
		{
			return findNearestRoomNodeStop(from);
		}

		int radiusSq = config.doorSearchRadius() * config.doorSearchRadius();
		List<WorldPoint> nearby = new ArrayList<>();
		for (WorldPoint wp : representatives)
		{
			if (distanceSquared(from, wp) <= radiusSq)
			{
				nearby.add(wp);
			}
		}
		if (nearby.isEmpty())
		{
			// Nothing within a practical distance right now - fall back to
			// whichever is nearest overall rather than stranding the route
			// on a distant "most outward" pick.
			return findNearestRoomNodeStop(from);
		}

		double currentDistFromStart = distance(startLocation, from);
		WorldPoint best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (WorldPoint wp : nearby)
		{
			double outwardGain = distance(startLocation, wp) - currentDistFromStart;
			double travelCost = distance(from, wp);
			double score = outwardGain - travelCost;
			if (score > bestScore)
			{
				bestScore = score;
				best = wp;
			}
		}

		return best == null ? null : new RouteStop(best, "room node");
	}

	private static int distanceSquared(WorldPoint a, WorldPoint b)
	{
		int dx = a.getX() - b.getX();
		int dy = a.getY() - b.getY();
		return dx * dx + dy * dy;
	}

	private static double distance(WorldPoint a, WorldPoint b)
	{
		return Math.sqrt(distanceSquared(a, b));
	}

	/**
	 * Walking distance from {@code a} to {@code b} in tiles, via the same
	 * collision-aware BFS used to draw the route (see {@link #findPath}),
	 * so ranking "which stop is closer" accounts for walls/doors instead of
	 * cutting through them. Falls back to straight-line distance if no path
	 * is found (different plane, or outside the loaded scene) so ordering
	 * still degrades gracefully rather than breaking. Results are cached
	 * per-recompute since the same pairs recur often across the greedy
	 * build and the 2-opt cleanup.
	 */
	private int pathDistance(WorldPoint a, WorldPoint b, Map<String, Integer> cache)
	{
		String key = pathDistanceKey(a, b);
		Integer cached = cache.get(key);
		if (cached != null)
		{
			return cached;
		}

		List<LocalPoint> path = findPath(a, b);
		int result = path == null ? (int) Math.round(distance(a, b)) : path.size();
		cache.put(key, result);
		return result;
	}

	private static String pathDistanceKey(WorldPoint a, WorldPoint b)
	{
		// Order-independent, since walking distance a->b equals b->a.
		if (a.getX() < b.getX() || (a.getX() == b.getX() && a.getY() <= b.getY()))
		{
			return a.getX() + "," + a.getY() + "-" + b.getX() + "," + b.getY();
		}
		return b.getX() + "," + b.getY() + "-" + a.getX() + "," + a.getY();
	}

	// Upper bound on full sweeps over the stop list, so a pathological case
	// can't loop indefinitely - in practice this converges in 1-2 passes
	// given how few stops are ever queued at once.
	private static final int MAX_TWO_OPT_PASSES = 20;

	/**
	 * Local-search cleanup on the greedy nearest-neighbor order in {@code stops}:
	 * repeatedly reverses a segment between two stops if doing so shortens
	 * the total walking distance from {@code start} through all of them,
	 * until no such swap helps. Greedy nearest-neighbor alone can zigzag -
	 * skipping a close stop to chase a farther one, then doubling back for
	 * it later - since it only ever looks one step ahead; this removes most
	 * of that without the cost of solving the ordering optimally.
	 */
	private void twoOptImprove(WorldPoint start, List<RouteStop> stops, Map<String, Integer> pathDistanceCache)
	{
		int n = stops.size();
		if (n < 3)
		{
			return;
		}

		boolean improved = true;
		int pass = 0;
		while (improved && pass++ < MAX_TWO_OPT_PASSES)
		{
			improved = false;
			for (int i = 0; i < n - 1; i++)
			{
				WorldPoint before = i == 0 ? start : stops.get(i - 1).getPoint();
				for (int j = i + 1; j < n; j++)
				{
					WorldPoint a = stops.get(i).getPoint();
					WorldPoint b = stops.get(j).getPoint();
					WorldPoint after = j + 1 < n ? stops.get(j + 1).getPoint() : null;

					int currentCost = pathDistance(before, a, pathDistanceCache)
						+ (after == null ? 0 : pathDistance(b, after, pathDistanceCache));
					int swappedCost = pathDistance(before, b, pathDistanceCache)
						+ (after == null ? 0 : pathDistance(a, after, pathDistanceCache));

					if (swappedCost < currentCost)
					{
						Collections.reverse(stops.subList(i, j + 1));
						improved = true;
					}
				}
			}
		}
	}

	List<RouteStop> getRoute()
	{
		return route;
	}

	List<LocalPoint> getRoutePath()
	{
		return routePath;
	}

	boolean isDemiMaterialsCompleted()
	{
		return demiMaterialsCompleted;
	}

	int getDemiMaterialsCount()
	{
		return cumulativeCounts.getOrDefault(ITEM_CRYSTAL_SPIKE, 0)
			+ cumulativeCounts.getOrDefault(ITEM_CRYSTAL_ORB, 0)
			+ cumulativeCounts.getOrDefault(ITEM_BOWSTRING, 0);
	}

	int getDemiMaterialsTarget()
	{
		return config.demiMaterialsTarget();
	}

	/**
	 * Human-readable label for the current phase, for display in the checklist panel.
	 */
	String getPhaseLabel()
	{
		if (!phase1ReturnTriggered)
		{
			return "Phase 1: Gathering nearby";
		}
		if (!phase2Started)
		{
			return "Return to Singing Bowl";
		}
		return "Phase 2: Expansion";
	}

	static final class RouteStop
	{
		private final WorldPoint point;
		private final String nodeType;

		RouteStop(WorldPoint point, String nodeType)
		{
			this.point = point;
			this.nodeType = nodeType;
		}

		WorldPoint getPoint()
		{
			return point;
		}

		String getNodeType()
		{
			return nodeType;
		}
	}

	static final class ItemHighlight
	{
		private final Tile tile;
		private final Color color;

		ItemHighlight(Tile tile, Color color)
		{
			this.tile = tile;
			this.color = color;
		}

		Tile getTile()
		{
			return tile;
		}

		Color getColor()
		{
			return color;
		}
	}
}
