package com.kcjg94.easycgplugin;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * A small draggable panel listing your current count of each key Gauntlet
 * supply against its target. Once a row hits its target it's marked
 * complete and stays that way (green, "Complete") for the rest of the run,
 * even if the item is later consumed crafting gear.
 */
class GauntletHighlighterCounterOverlay extends OverlayPanel
{
	private static final Color NOT_MET_COLOR = Color.WHITE;
	private static final Color MET_COLOR = Color.GREEN;

	private static final String ITEM_ORE = "corrupted ore";
	private static final String ITEM_BARK = "phren bark";
	private static final String ITEM_TIRINUM = "linum tirinum";
	private static final String ITEM_HERB = "grym leaf";
	private static final String ITEM_PADDLEFISH = "raw paddlefish";
	private static final String ITEM_WEAPON_FRAME = "weapon frame";
	private static final String ITEM_SHARDS = "corrupted shards";

	private final GauntletHighlighterPlugin plugin;
	private final GauntletHighlighterConfig config;

	@Inject
	private GauntletHighlighterCounterOverlay(GauntletHighlighterPlugin plugin, GauntletHighlighterConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		panelComponent.setPreferredSize(new Dimension(190, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showChecklist() || !plugin.isInCorruptedGauntlet() || plugin.isBossFightActive())
		{
			return null;
		}

		panelComponent.getChildren().clear();

		boolean allMet = plugin.isFullyStocked();

		panelComponent.getChildren().add(TitleComponent.builder()
			.text(allMet ? "Supplies Ready!" : "Gauntlet Supplies")
			.color(allMet ? MET_COLOR : Color.WHITE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left(plugin.getPhaseLabel())
			.build());

		addRow("Corrupted ore", ITEM_ORE);
		addRow("Phren bark", ITEM_BARK);
		addRow("Linum tirinum", ITEM_TIRINUM);
		addRow("Grym leaf", ITEM_HERB);
		addRow("Paddlefish", ITEM_PADDLEFISH);
		addRow("Weapon frames", ITEM_WEAPON_FRAME);
		addRow("Corrupted shards", ITEM_SHARDS);
		addDemiMaterialsRow();

		return panelComponent.render(graphics);
	}

	private void addDemiMaterialsRow()
	{
		boolean complete = plugin.isDemiMaterialsCompleted();
		int current = plugin.getDemiMaterialsCount();
		int target = plugin.getDemiMaterialsTarget();

		String right = complete ? "\u2713 " + target + "/" + target : current + "/" + target;
		Color color = complete ? MET_COLOR : NOT_MET_COLOR;

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Demi materials")
			.right(right)
			.rightColor(color)
			.build());
	}

	private void addRow(String label, String itemName)
	{
		boolean complete = plugin.isItemCompleted(itemName);
		int current = plugin.getItemCount(itemName);
		int target = plugin.getItemTarget(itemName);

		String right = complete ? "\u2713 " + target + "/" + target : current + "/" + target;
		Color color = complete ? MET_COLOR : NOT_MET_COLOR;

		panelComponent.getChildren().add(LineComponent.builder()
			.left(label)
			.right(right)
			.rightColor(color)
			.build());
	}
}

