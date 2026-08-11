package com.kcjg94.easycgplugin;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Outlines the vial/weapon/armor options in the Singing Bowl's "What would
 * you like to make?" menu while they're still needed, so there's no need to
 * hunt through the list for the right one. Two phases: early on, just the
 * vial and whichever weapon you're building toward attuned; once every
 * requirement for the run is met ({@link GauntletHighlighterPlugin#isFinalCraftingPhase()}),
 * every armor piece and any started weapon still short of perfected.
 * <p>
 * The menu is {@link InterfaceID.Skillmulti} (widget group 270) - the same
 * shared "make X" interface used for many production menus across the
 * game, not a Gauntlet-specific one, so rendering is gated to inside an
 * instanced region as a safety check against highlighting an unrelated
 * Skillmulti menu elsewhere.
 * <p>
 * Each of the top-level slots (A-R) is just a container - the actual item
 * icon lives on a nested child widget, and only one tier of a given item is
 * ever offered at a time (e.g. "Corrupted staff (basic)" until you own one,
 * then "(attuned)" next), so items are matched by their bare name/prefix
 * rather than a specific tier's exact name.
 */
class GauntletHighlighterCraftingOverlay extends Overlay
{
	private static final int[] SLOT_IDS = {
		InterfaceID.Skillmulti.A,
		InterfaceID.Skillmulti.B,
		InterfaceID.Skillmulti.C,
		InterfaceID.Skillmulti.D,
		InterfaceID.Skillmulti.E,
		InterfaceID.Skillmulti.F,
		InterfaceID.Skillmulti.G,
		InterfaceID.Skillmulti.H,
		InterfaceID.Skillmulti.I,
		InterfaceID.Skillmulti.J,
		InterfaceID.Skillmulti.K,
		InterfaceID.Skillmulti.L,
		InterfaceID.Skillmulti.M,
		InterfaceID.Skillmulti.N,
		InterfaceID.Skillmulti.O,
		InterfaceID.Skillmulti.P,
		InterfaceID.Skillmulti.Q,
		InterfaceID.Skillmulti.R,
	};

	private static final String ITEM_VIAL = "vial";

	private final Client client;
	private final GauntletHighlighterPlugin plugin;
	private final GauntletHighlighterConfig config;

	@Inject
	private GauntletHighlighterCraftingOverlay(Client client, GauntletHighlighterPlugin plugin, GauntletHighlighterConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.highlightCraftingMenu() || !plugin.isInCorruptedGauntlet() || plugin.isBossFightActive())
		{
			return null;
		}

		Widget universe = client.getWidget(InterfaceID.Skillmulti.UNIVERSE);
		if (universe == null || universe.isHidden())
		{
			return null;
		}

		boolean vialNeeded = plugin.isVialCraftingNeeded();

		// Regardless of phase or the configured preferred weapon: holding a
		// demi-boss material is always a reason to highlight the weapon it
		// belongs to, since there's an immediate use for it.
		List<String> targetBaseNames = new ArrayList<>(plugin.getWeaponBaseNamesForHeldMaterials());
		if (plugin.isFinalCraftingPhase())
		{
			// Everything's gathered - the only thing left is turning it
			// into gear, so highlight the full armor set plus any weapon
			// that was actually started, until each reaches perfected.
			targetBaseNames.addAll(plugin.getArmorBaseNamesNeeded());
			targetBaseNames.addAll(plugin.getWeaponBaseNamesNeeded());
		}
		else
		{
			String weaponBaseName = plugin.getPreferredWeaponBaseName();
			if (weaponBaseName != null && plugin.isWeaponCraftingNeeded())
			{
				targetBaseNames.add(weaponBaseName);
			}
		}

		if (!vialNeeded && targetBaseNames.isEmpty())
		{
			return null;
		}

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Color color = config.craftingHighlightColor();

		for (int slotId : SLOT_IDS)
		{
			Widget slot = client.getWidget(slotId);
			if (slot == null || slot.isHidden())
			{
				continue;
			}

			String name = getSlotItemName(slot);
			if (name == null)
			{
				continue;
			}

			boolean isTarget = (vialNeeded && name.equals(ITEM_VIAL)) || matchesAnyBaseName(name, targetBaseNames);
			if (isTarget)
			{
				highlightSlot(graphics, slot.getBounds(), color);
			}
		}

		return null;
	}

	private static boolean matchesAnyBaseName(String name, List<String> baseNames)
	{
		for (String baseName : baseNames)
		{
			if (name.startsWith(baseName))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The slot container itself has no item ID - the icon is on a nested
	 * child widget, so this finds whichever child actually holds one.
	 */
	private String getSlotItemName(Widget slot)
	{
		Widget[] children = slot.getDynamicChildren();
		if (children == null)
		{
			return null;
		}

		for (Widget child : children)
		{
			int itemId = child.getItemId();
			if (itemId <= 0)
			{
				continue;
			}
			ItemComposition comp = client.getItemDefinition(itemId);
			if (comp != null)
			{
				return comp.getName().toLowerCase();
			}
		}

		return null;
	}

	private void highlightSlot(Graphics2D graphics, Rectangle bounds, Color color)
	{
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
		{
			return;
		}

		graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
		graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(2f));
		graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
	}
}
