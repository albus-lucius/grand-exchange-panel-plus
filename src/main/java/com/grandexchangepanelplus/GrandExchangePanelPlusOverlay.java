package com.grandexchangepanelplus;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
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

		// Hide buttons when not on the overview screen
		boolean pleaseWait = isPleaseWaitVisible();
		boolean inSubView = client.getVarbitValue(4439) != 0 || pleaseWait || plugin.suppressButtons;
		if (!pleaseWait && client.getVarbitValue(4439) == 0)
		{
			plugin.suppressButtons = false;
		}

		// Annotation tooltip that appears on progress bar hover
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

			GrandExchangeOffer offer = offers[slot];

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
		}

		return null;
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
}
