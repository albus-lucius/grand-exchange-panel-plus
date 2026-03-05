package com.grandexchangepanelplus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class GrandExchangePanelPlusOverlay extends Overlay
{
	private static final int GE_OFFERS_INTERFACE = 465;
	private static final int GE_ANNOTATION_CHILD = 33;
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
	private final GrandExchangePanelPlusPlugin plugin;
	private final SpriteManager spriteManager;
	private final Map<Integer, Rectangle> progressBarBounds = new HashMap<>();
	private final Map<Integer, Rectangle> modifyBounds = new HashMap<>();
	private final Map<Integer, Rectangle> abortBounds = new HashMap<>();

	@Inject
	public GrandExchangePanelPlusOverlay(Client client, GrandExchangePanelPlusConfig config, GrandExchangePanelPlusPlugin plugin, SpriteManager spriteManager)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		this.spriteManager = spriteManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		drawAfterInterface(GE_OFFERS_INTERFACE);
	}

	Rectangle getProgressBarBound(int slot)
	{
		return progressBarBounds.get(slot);
	}

	Rectangle getModifyBound(int slot)
	{
		return modifyBounds.get(slot);
	}

	Rectangle getAbortBound(int slot)
	{
		return abortBounds.get(slot);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		progressBarBounds.clear();
		modifyBounds.clear();
		abortBounds.clear();

		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return null;
		}

		boolean debug = config.showDebug();

		// Hide buttons when not on the overview screen
		boolean pleaseWait = isPleaseWaitVisible();
		boolean inSubView = client.getVarbitValue(4439) != 0 || pleaseWait || plugin.suppressButtons;
		if (!pleaseWait && client.getVarbitValue(4439) == 0)
		{
			plugin.suppressButtons = false;
		}

		// Annotation tooltip that appears on progress bar hover
		// The parent widget 465:33 always exists when GE is open;
		// the actual annotation content is dynamic child index 2
		Rectangle annotationBounds = null;
		Widget annotationParent = client.getWidget(GE_OFFERS_INTERFACE, GE_ANNOTATION_CHILD);
		if (annotationParent != null)
		{
			Widget annotationText = annotationParent.getChild(2);
			if (annotationText != null && !annotationText.isHidden())
			{
				annotationBounds = annotationParent.getBounds();
			}
		}

		for (int slot = 0; slot < 8 && slot < offers.length; slot++)
		{
			Widget slotWidget = client.getWidget(GE_OFFERS_INTERFACE, GE_SLOT_FIRST_CHILD + slot);
			if (slotWidget == null || slotWidget.isHidden())
			{
				continue;
			}

			Rectangle bounds = slotWidget.getBounds();
			GrandExchangeOffer offer = offers[slot];

			// Debug overlays
			if (debug)
			{
				renderDebugBorder(graphics, bounds, slot);
				renderProgressBarDebug(graphics, slotWidget, slot);
				renderTitleDebugBorders(graphics, slotWidget);
			}

			if (!inSubView)
			{
				boolean activeOffer = offer != null
					&& (offer.getState() == GrandExchangeOfferState.BUYING
					|| offer.getState() == GrandExchangeOfferState.SELLING);
				renderTitleButtons(graphics, slotWidget, slot, activeOffer, annotationBounds);
				boolean collectable = hasItemsToCollect(slot);
				if (config.collectDisplay() && collectable)
				{
					renderCollectOnBar(graphics, slotWidget, annotationBounds);
				}
				if (config.collectEnable() && collectable)
				{
					trackProgressBarBounds(slotWidget, slot);
				}
			}

			// Status labels (debug only)
			if (debug)
			{
				if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
				{
					renderLabel(graphics, bounds, "Empty", Color.GRAY);
					continue;
				}

				GrandExchangeOfferState state = offer.getState();
				renderLabel(graphics, bounds, getStatusText(state), getStatusColor(state));
			}
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

	private Rectangle[] getTitleRegions(Widget slotWidget)
	{
		Widget title = slotWidget.getChild(SLOT_TITLE_CHILD);
		if (title == null || title.isHidden())
		{
			return null;
		}

		Rectangle tb = title.getBounds();
		int thirdWidth = tb.width / 3;

		return new Rectangle[] {
			new Rectangle(tb.x, tb.y, thirdWidth, tb.height),
			new Rectangle(tb.x + thirdWidth, tb.y, thirdWidth, tb.height),
			new Rectangle(tb.x + thirdWidth * 2, tb.y, tb.width - thirdWidth * 2, tb.height)
		};
	}

	private void renderTitleDebugBorders(Graphics2D graphics, Widget slotWidget)
	{
		Rectangle[] regions = getTitleRegions(slotWidget);
		if (regions == null)
		{
			return;
		}

		Stroke prev = graphics.getStroke();
		graphics.setStroke(new BasicStroke(1));

		graphics.setColor(new Color(0, 200, 0, 180));
		graphics.drawRect(regions[0].x, regions[0].y, regions[0].width, regions[0].height);

		graphics.setColor(DEBUG_TITLE_COLOR);
		graphics.drawRect(regions[1].x, regions[1].y, regions[1].width, regions[1].height);

		graphics.setColor(new Color(200, 0, 0, 180));
		graphics.drawRect(regions[2].x, regions[2].y, regions[2].width, regions[2].height);

		graphics.setStroke(prev);

		// center title text
		Widget title = slotWidget.getChild(SLOT_TITLE_CHILD);
		graphics.setFont(FontManager.getRunescapeSmallFont());
		String titleText = title.getText();
		drawCenteredText(graphics, regions[1], titleText != null ? titleText : "", Color.YELLOW);
	}

	private void renderTitleButtons(Graphics2D graphics, Widget slotWidget, int slot, boolean activeOffer, Rectangle annotationBounds)
	{
		if (!activeOffer)
		{
			return;
		}

		boolean showModify = config.modifyDisplay();
		boolean showAbort = config.abortDisplay();
		boolean enableModify = config.modifyEnable();
		boolean enableAbort = config.abortEnable();

		if (!showModify && !showAbort && !enableModify && !enableAbort)
		{
			return;
		}

		Rectangle[] regions = getTitleRegions(slotWidget);
		if (regions == null)
		{
			return;
		}

		net.runelite.api.Point mouse = client.getMouseCanvasPosition();

		if (showModify)
		{
			boolean hover = mouse != null && regions[0].contains(mouse.getX(), mouse.getY());
			drawClippedSprite(graphics, regions[0], hover ? SPRITE_GE_MODIFY_HOVER : SPRITE_GE_MODIFY, annotationBounds);
		}
		if (enableModify)
		{
			modifyBounds.put(slot, regions[0]);
		}

		if (showAbort)
		{
			boolean hover = mouse != null && regions[2].contains(mouse.getX(), mouse.getY());
			drawClippedSprite(graphics, regions[2], hover ? SPRITE_GE_ABORT_HOVER : SPRITE_GE_ABORT, annotationBounds);
		}
		if (enableAbort)
		{
			abortBounds.put(slot, regions[2]);
		}
	}

	private static boolean overlapExceeds(Rectangle a, Rectangle b, double threshold)
	{
		if (a == null)
		{
			return false;
		}
		Rectangle overlap = a.intersection(b);
		if (overlap.isEmpty())
		{
			return false;
		}
		double overlapArea = (double) overlap.width * overlap.height;
		double buttonArea = (double) b.width * b.height;
		return buttonArea > 0 && overlapArea / buttonArea >= threshold;
	}

	private void drawClippedSprite(Graphics2D graphics, Rectangle bounds, int spriteId, Rectangle annotationBounds)
	{
		if (annotationBounds == null || !annotationBounds.intersects(bounds))
		{
			drawCenteredSprite(graphics, bounds, spriteId);
			return;
		}

		Area visible = new Area(bounds);
		visible.subtract(new Area(annotationBounds));
		if (visible.isEmpty())
		{
			return;
		}

		Shape originalClip = graphics.getClip();
		graphics.setClip(visible);
		drawCenteredSprite(graphics, bounds, spriteId);
		graphics.setClip(originalClip);
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

	private void renderCollectOnBar(Graphics2D graphics, Widget slotWidget, Rectangle annotationBounds)
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

		boolean clipped = false;
		Shape originalClip = graphics.getClip();
		if (annotationBounds != null && annotationBounds.intersects(pb))
		{
			Area visible = new Area(pb);
			visible.subtract(new Area(annotationBounds));
			if (visible.isEmpty())
			{
				return;
			}
			graphics.setClip(visible);
			clipped = true;
		}

		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		boolean hover = mouse != null && pb.contains(mouse.getX(), mouse.getY());

		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(hover ? new Color(0xFF9040) : Color.WHITE);
		graphics.drawString(text, x, y);

		if (clipped)
		{
			graphics.setClip(originalClip);
		}
	}

	private void trackProgressBarBounds(Widget slotWidget, int slot)
	{
		Widget progressBar = slotWidget.getChild(SLOT_PROGRESS_BAR_CHILD);
		if (progressBar != null && !progressBar.isHidden())
		{
			progressBarBounds.put(slot, progressBar.getBounds());
		}
	}

	private static final int PLEASE_WAIT_INTERFACE = 916;
	private static final int PLEASE_WAIT_CHILD = 7;

	private boolean isPleaseWaitVisible()
	{
		Widget w = client.getWidget(PLEASE_WAIT_INTERFACE, PLEASE_WAIT_CHILD);
		return w != null && !w.isHidden();
	}

	private static final int[] GE_SLOT_INVENTORIES = {518, 519, 520, 521, 522, 523, 539, 540};

	private boolean hasItemsToCollect(int slot)
	{
		if (slot < 0 || slot >= GE_SLOT_INVENTORIES.length)
		{
			return false;
		}
		ItemContainer container = client.getItemContainer(GE_SLOT_INVENTORIES[slot]);
		if (container == null)
		{
			return false;
		}
		Item[] items = container.getItems();
		if (items == null)
		{
			return false;
		}
		for (Item item : items)
		{
			if (item.getId() != -1 && item.getQuantity() > 0)
			{
				return true;
			}
		}
		return false;
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
