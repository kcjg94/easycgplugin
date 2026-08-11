package com.kcjg94.easycgplugin;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

class GauntletHighlighterOverlay extends Overlay
{
	private final Client client;
	private final GauntletHighlighterPlugin plugin;
	private final GauntletHighlighterConfig config;
	private final ModelOutlineRenderer modelOutlineRenderer;

	@Inject
	private GauntletHighlighterOverlay(
		Client client,
		GauntletHighlighterPlugin plugin,
		GauntletHighlighterConfig config,
		ModelOutlineRenderer modelOutlineRenderer)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.modelOutlineRenderer = modelOutlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isInCorruptedGauntlet() || plugin.isBossFightActive())
		{
			return null;
		}

		int width = config.outlineWidth();

		WorldPoint activeRoomNode = plugin.getActiveRoomNode();
		for (Map.Entry<GameObject, String> entry : plugin.getHighlightedObjects().entrySet())
		{
			if (!plugin.isNodeVisible(entry.getValue()))
			{
				continue;
			}

			if (plugin.isRoomNodeName(entry.getValue()))
			{
				// Every node currently loaded in the scene matches this name -
				// only outline the single nearest/active one (see
				// GauntletHighlighterPlugin#recomputeRoute), not all of them.
				WorldPoint wp = entry.getKey().getWorldLocation();
				if (activeRoomNode == null || wp == null || !wp.equals(activeRoomNode))
				{
					continue;
				}
			}

			modelOutlineRenderer.drawOutline(entry.getKey(), width, plugin.getObjectColor(entry.getValue()), 0);
		}

		for (Map.Entry<NPC, String> entry : plugin.getHighlightedNpcs().entrySet())
		{
			if (!plugin.isNpcVisible(entry.getValue()))
			{
				continue;
			}
			modelOutlineRenderer.drawOutline(entry.getKey(), width, plugin.getNpcColor(entry.getValue()), 0);
		}

		for (Map.Entry<TileItem, GauntletHighlighterPlugin.ItemHighlight> entry : plugin.getHighlightedItems().entrySet())
		{
			renderItemTile(graphics, entry.getValue());
		}

		return null;
	}

	private void renderItemTile(Graphics2D graphics, GauntletHighlighterPlugin.ItemHighlight highlight)
	{
		Tile tile = highlight.getTile();
		if (tile == null)
		{
			return;
		}

		LocalPoint lp = tile.getLocalLocation();
		if (lp == null)
		{
			return;
		}

		Shape poly = Perspective.getCanvasTilePoly(client, lp);
		if (poly != null)
		{
			OverlayUtil.renderPolygon(graphics, poly, highlight.getColor());
		}
	}
}
