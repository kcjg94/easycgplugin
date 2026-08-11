package com.kcjg94.easycgplugin;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the suggested route through the resource nodes/stops you still need:
 * a collision-aware walking path (see {@link GauntletHighlighterPlugin#getRoutePath()})
 * from the player through each stop in nearest-neighbor order, with a
 * numbered circular marker at each actual stop and small arrowheads along
 * the way showing direction of travel. Following the collision map means
 * the line naturally routes through doorways/gaps in room walls instead of
 * straight through them.
 */
class GauntletHighlighterRouteOverlay extends Overlay
{
	private static final int MARKER_RADIUS = 11;
	private static final Color OUTLINE_COLOR = new Color(0, 0, 0, 190);
	private static final Font MARKER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
	private static final int ARROW_SPACING = 4;
	private static final int ARROW_SIZE = 6;

	private final Client client;
	private final GauntletHighlighterPlugin plugin;
	private final GauntletHighlighterConfig config;

	@Inject
	private GauntletHighlighterRouteOverlay(Client client, GauntletHighlighterPlugin plugin, GauntletHighlighterConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showRoute() || !plugin.isInCorruptedGauntlet() || plugin.isBossFightActive())
		{
			return null;
		}

		List<GauntletHighlighterPlugin.RouteStop> route = plugin.getRoute();
		if (route.isEmpty())
		{
			return null;
		}

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		int plane = client.getPlane();
		Color routeColor = config.routeColor();
		float lineWidth = config.routeLineWidth();

		List<Point> canvasPath = new ArrayList<>();
		for (LocalPoint lp : plugin.getRoutePath())
		{
			canvasPath.add(Perspective.localToCanvas(client, lp, plane));
		}

		// Dark halo drawn first, then the route color on top, so the line
		// stays legible over both the Gauntlet's bright and dark terrain.
		drawPath(graphics, canvasPath, OUTLINE_COLOR, lineWidth + 3f);
		drawPath(graphics, canvasPath, routeColor, lineWidth);
		drawArrowheads(graphics, canvasPath, routeColor);

		for (int i = 0; i < route.size(); i++)
		{
			GauntletHighlighterPlugin.RouteStop stop = route.get(i);

			LocalPoint lp = LocalPoint.fromWorld(client, stop.getPoint());
			if (lp == null)
			{
				continue;
			}

			Point canvasPoint = Perspective.localToCanvas(client, lp, stop.getPoint().getPlane());
			if (canvasPoint == null)
			{
				continue;
			}

			drawMarker(graphics, canvasPoint, String.valueOf(i + 1), routeColor);
		}

		return null;
	}

	private void drawPath(Graphics2D graphics, List<Point> canvasPath, Color color, float width)
	{
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		Point previous = null;
		for (Point canvasPoint : canvasPath)
		{
			if (canvasPoint == null)
			{
				previous = null;
				continue;
			}
			if (previous != null)
			{
				graphics.drawLine(previous.getX(), previous.getY(), canvasPoint.getX(), canvasPoint.getY());
			}
			previous = canvasPoint;
		}
	}

	private void drawArrowheads(Graphics2D graphics, List<Point> canvasPath, Color color)
	{
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		for (int i = ARROW_SPACING; i < canvasPath.size(); i += ARROW_SPACING)
		{
			Point from = canvasPath.get(i - 1);
			Point to = canvasPath.get(i);
			if (from == null || to == null)
			{
				continue;
			}
			drawArrowhead(graphics, from, to);
		}
	}

	private void drawArrowhead(Graphics2D graphics, Point from, Point to)
	{
		double angle = Math.atan2(to.getY() - from.getY(), to.getX() - from.getX());
		int midX = (from.getX() + to.getX()) / 2;
		int midY = (from.getY() + to.getY()) / 2;

		int x1 = (int) (midX - ARROW_SIZE * Math.cos(angle - Math.PI / 6));
		int y1 = (int) (midY - ARROW_SIZE * Math.sin(angle - Math.PI / 6));
		int x2 = (int) (midX - ARROW_SIZE * Math.cos(angle + Math.PI / 6));
		int y2 = (int) (midY - ARROW_SIZE * Math.sin(angle + Math.PI / 6));

		graphics.drawLine(midX, midY, x1, y1);
		graphics.drawLine(midX, midY, x2, y2);
	}

	private void drawMarker(Graphics2D graphics, Point canvasPoint, String label, Color color)
	{
		int x = canvasPoint.getX();
		int y = canvasPoint.getY();

		Ellipse2D circle = new Ellipse2D.Double(x - MARKER_RADIUS, y - MARKER_RADIUS, MARKER_RADIUS * 2, MARKER_RADIUS * 2);

		graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 210));
		graphics.fill(circle);

		graphics.setColor(OUTLINE_COLOR);
		graphics.setStroke(new BasicStroke(2f));
		graphics.draw(circle);

		graphics.setFont(MARKER_FONT);
		FontMetrics fm = graphics.getFontMetrics();
		int textX = x - fm.stringWidth(label) / 2;
		int textY = y + fm.getAscent() / 2 - 2;

		graphics.setColor(Color.BLACK);
		graphics.drawString(label, textX + 1, textY + 1);
		graphics.setColor(Color.WHITE);
		graphics.drawString(label, textX, textY);
	}
}
