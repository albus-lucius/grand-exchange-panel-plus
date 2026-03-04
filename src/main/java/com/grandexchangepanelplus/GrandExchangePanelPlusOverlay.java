package com.grandexchangepanelplus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class GrandExchangePanelPlusOverlay extends Overlay
{
	private static final int GE_OFFERS_INTERFACE = 465;
	private static final int GE_SLOT_FIRST_CHILD = 7;
	private static final int SLOT_TITLE_CHILD = 16;
	private static final int SLOT_PROGRESS_BAR_CHILD = 22;
	private static final int SPRITE_GE_ABORT = 1126;
	private static final int SPRITE_GE_ABORT_HOVER = 1127;
	private static final int SPRITE_GE_MODIFY = 6409;
	private static final int SPRITE_GE_MODIFY_HOVER = 6410;
	private static final Color DEBUG_BORDER_COLOR = new Color(0, 255, 255, 180);
	private static final Color DEBUG_PROGRESS_COLOR = new Color(255, 0, 255, 180);
	private static final Color DEBUG_TITLE_COLOR = new Color(255, 255, 0, 180);
	private static final int LABEL_PADDING = 4;

	private final Client client;
	private final GrandExchangePanelPlusConfig config;
	private final SpriteManager spriteManager;
	private final Map<Integer, Rectangle> progressBarBounds = new HashMap<>();

	@Inject
	public GrandExchangePanelPlusOverlay(Client client, GrandExchangePanelPlusConfig config, SpriteManager spriteManager)
	{
		this.client = client;
		this.config = config;
		this.spriteManager = spriteManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	Rectangle getProgressBarBound(int slot)
	{
		return progressBarBounds.get(slot);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		progressBarBounds.clear();

		if (!config.showSlotStatus())
		{
			return null;
		}

		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return null;
		}

		for (int slot = 0; slot < 8 && slot < offers.length; slot++)
		{
			Widget slotWidget = client.getWidget(GE_OFFERS_INTERFACE, GE_SLOT_FIRST_CHILD + slot);
			if (slotWidget == null || slotWidget.isHidden())
			{
				continue;
			}

			Rectangle bounds = slotWidget.getBounds();
			renderDebugBorder(graphics, bounds, slot);
			renderTitleDebug(graphics, slotWidget);
			renderProgressBarDebug(graphics, slotWidget, slot);
			renderCollectOnBar(graphics, slotWidget);
			trackProgressBarBounds(slotWidget, slot);

			GrandExchangeOffer offer = offers[slot];
			if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
			{
				renderLabel(graphics, bounds, "Empty", Color.GRAY);
				continue;
			}

			GrandExchangeOfferState state = offer.getState();
			renderLabel(graphics, bounds, getStatusText(state), getStatusColor(state));
		}

		return null;
	}

	private void renderDebugBorder(Graphics2D graphics, Rectangle bounds, int slot)
	{
		Stroke prev = graphics.getStroke();
		graphics.setStroke(new BasicStroke(1));
		graphics.setColor(DEBUG_BORDER_COLOR);
		graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

		// slot index label in top-left corner
		graphics.setFont(FontManager.getRunescapeSmallFont());
		graphics.setColor(Color.BLACK);
		graphics.drawString("#" + slot, bounds.x + 3, bounds.y + 12);
		graphics.setColor(Color.CYAN);
		graphics.drawString("#" + slot, bounds.x + 2, bounds.y + 11);
		graphics.setStroke(prev);
	}

	private void renderTitleDebug(Graphics2D graphics, Widget slotWidget)
	{
		Widget title = slotWidget.getChild(SLOT_TITLE_CHILD);
		if (title == null || title.isHidden())
		{
			return;
		}

		Rectangle tb = title.getBounds();
		int thirdWidth = tb.width / 3;

		Rectangle left = new Rectangle(tb.x, tb.y, thirdWidth, tb.height);
		Rectangle center = new Rectangle(tb.x + thirdWidth, tb.y, thirdWidth, tb.height);
		Rectangle right = new Rectangle(tb.x + thirdWidth * 2, tb.y, tb.width - thirdWidth * 2, tb.height);

		Stroke prev = graphics.getStroke();
		graphics.setStroke(new BasicStroke(1));

		// left section - Modify
		graphics.setColor(new Color(0, 200, 0, 180));
		graphics.drawRect(left.x, left.y, left.width, left.height);

		// center section - Title
		graphics.setColor(DEBUG_TITLE_COLOR);
		graphics.drawRect(center.x, center.y, center.width, center.height);

		// right section - Abort
		graphics.setColor(new Color(200, 0, 0, 180));
		graphics.drawRect(right.x, right.y, right.width, right.height);

		graphics.setStroke(prev);

		// center title text
		graphics.setFont(FontManager.getRunescapeSmallFont());
		String titleText = title.getText();
		drawCenteredText(graphics, center, titleText != null ? titleText : "", Color.YELLOW);

		// draw modify sprite (left) and abort sprite (right), swap to hover sprite when mouse is over
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		boolean modifyHover = mouse != null && left.contains(mouse.getX(), mouse.getY());
		boolean abortHover = mouse != null && right.contains(mouse.getX(), mouse.getY());

		drawCenteredSprite(graphics, left, modifyHover ? SPRITE_GE_MODIFY_HOVER : SPRITE_GE_MODIFY);
		drawCenteredSprite(graphics, right, abortHover ? SPRITE_GE_ABORT_HOVER : SPRITE_GE_ABORT);
	}

	private void drawCenteredSprite(Graphics2D graphics, Rectangle bounds, int spriteId)
	{
		BufferedImage sprite = spriteManager.getSprite(spriteId, 0);
		if (sprite == null)
		{
			return;
		}

		int sw = sprite.getWidth();
		int sh = sprite.getHeight();

		// scale down to fit within bounds with 1px padding
		int maxW = bounds.width - 2;
		int maxH = bounds.height - 2;
		if (sw > maxW || sh > maxH)
		{
			double scale = Math.min((double) maxW / sw, (double) maxH / sh);
			sw = (int) (sw * scale);
			sh = (int) (sh * scale);
		}

		int x = bounds.x + (bounds.width - sw) / 2;
		int y = bounds.y + (bounds.height - sh) / 2;
		graphics.drawImage(sprite, x, y, sw, sh, null);
	}

	private void drawCenteredText(Graphics2D graphics, Rectangle bounds, String text, Color color)
	{
		GlyphVector gv = graphics.getFont().createGlyphVector(graphics.getFontRenderContext(), text);
		Rectangle2D vis = gv.getVisualBounds();

		int x = bounds.x + (int) ((bounds.width - vis.getWidth()) / 2 - vis.getX());
		int y = bounds.y + (int) ((bounds.height - vis.getHeight()) / 2 - vis.getY());

		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(color);
		graphics.drawString(text, x, y);
	}

	private void renderProgressBarDebug(Graphics2D graphics, Widget slotWidget, int slot)
	{
		Widget progressBar = slotWidget.getChild(SLOT_PROGRESS_BAR_CHILD);
		if (progressBar == null || progressBar.isHidden())
		{
			return;
		}

		Rectangle pb = progressBar.getBounds();
		Stroke prev = graphics.getStroke();
		graphics.setStroke(new BasicStroke(1));
		graphics.setColor(DEBUG_PROGRESS_COLOR);
		graphics.drawRect(pb.x, pb.y, pb.width, pb.height);
		graphics.setStroke(prev);

		// dimension + color info label to the right of the progress bar
		int textColor = progressBar.getTextColor();
		String info = String.format("%dx%d  color:0x%06X", pb.width, pb.height, textColor);

		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = graphics.getFontMetrics();

		int x = pb.x + pb.width + 4;
		int y = pb.y + fm.getAscent();

		graphics.setColor(Color.BLACK);
		graphics.drawString(info, x + 1, y + 1);
		graphics.setColor(Color.MAGENTA);
		graphics.drawString(info, x, y);
	}

	private void renderLabel(Graphics2D graphics, Rectangle bounds, String text, Color color)
	{
		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = graphics.getFontMetrics();
		int textWidth = fm.stringWidth(text);
		int textHeight = fm.getHeight();

		// position label above the slot widget, centered horizontally
		int x = bounds.x + (bounds.width - textWidth) / 2;
		int y = bounds.y - LABEL_PADDING - fm.getDescent();

		// background box
		int bgX = x - 2;
		int bgY = y - fm.getAscent() - 1;
		graphics.setColor(new Color(0, 0, 0, 160));
		graphics.fillRect(bgX, bgY, textWidth + 4, textHeight + 2);

		// text
		graphics.setColor(color);
		graphics.drawString(text, x, y);
	}

	private void renderCollectOnBar(Graphics2D graphics, Widget slotWidget)
	{
		Widget progressBar = slotWidget.getChild(SLOT_PROGRESS_BAR_CHILD);
		if (progressBar == null || progressBar.isHidden())
		{
			return;
		}

		Rectangle pb = progressBar.getBounds();
		String text = "Collect";

		graphics.setFont(FontManager.getRunescapeSmallFont());
		GlyphVector gv = graphics.getFont().createGlyphVector(graphics.getFontRenderContext(), text);
		Rectangle2D vis = gv.getVisualBounds();

		// center actual visible glyph pixels within the bar
		int x = pb.x + (int) ((pb.width - vis.getWidth()) / 2 - vis.getX());
		int y = pb.y + (int) ((pb.height - vis.getHeight()) / 2 - vis.getY());

		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(Color.WHITE);
		graphics.drawString(text, x, y);
	}

	private void trackProgressBarBounds(Widget slotWidget, int slot)
	{
		Widget progressBar = slotWidget.getChild(SLOT_PROGRESS_BAR_CHILD);
		if (progressBar != null && !progressBar.isHidden())
		{
			progressBarBounds.put(slot, progressBar.getBounds());
		}
	}

	private boolean isCollectable(GrandExchangeOfferState state)
	{
		return state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.SOLD
			|| state == GrandExchangeOfferState.CANCELLED_BUY
			|| state == GrandExchangeOfferState.CANCELLED_SELL;
	}

	private String getStatusText(GrandExchangeOfferState state)
	{
		switch (state)
		{
			case BUYING:
				return "Buying";
			case BOUGHT:
				return "Bought";
			case SELLING:
				return "Selling";
			case SOLD:
				return "Sold";
			case CANCELLED_BUY:
			case CANCELLED_SELL:
				return "Cancelled";
			default:
				return "";
		}
	}

	private Color getStatusColor(GrandExchangeOfferState state)
	{
		switch (state)
		{
			case BUYING:
			case SELLING:
				return Color.YELLOW;
			case BOUGHT:
			case SOLD:
				return Color.GREEN;
			case CANCELLED_BUY:
			case CANCELLED_SELL:
				return Color.RED;
			default:
				return Color.WHITE;
		}
	}
}
