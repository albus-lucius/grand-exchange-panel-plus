package com.grandexchangepanelplus;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(name = "01 Grand Exchange Panel Plus")
public class GrandExchangePanelPlusPlugin extends Plugin implements MouseListener
{
	private static final int GE_OFFERS_INTERFACE = 465;
	private static final int GE_SLOT_FIRST_CHILD = 7;
	private static final int GE_BACK_BUTTON = 4;
	private static final int GE_COLLECT_AREA = 24;

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private GrandExchangePanelPlusOverlay overlay;

	@Inject
	private GrandExchangePanelPlusConfig config;

	private int pendingCollectSlot = -1;
	private int collectTicksWaited;

	@Provides
	GrandExchangePanelPlusConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GrandExchangePanelPlusConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(overlay);
		mouseManager.registerMouseListener(this);
		log.info("Grand Exchange Panel Plus started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		mouseManager.unregisterMouseListener(this);
		pendingCollectSlot = -1;
		log.info("Grand Exchange Panel Plus stopped");
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		if (!config.showSlotStatus())
		{
			return e;
		}

		for (int slot = 0; slot < 8; slot++)
		{
			Rectangle bounds = overlay.getProgressBarBound(slot);
			if (bounds != null && bounds.contains(e.getPoint()))
			{
				log.info("HIT slot {}, starting collect", slot);
				pendingCollectSlot = slot;
				collectTicksWaited = -1; // -1 = need to open slot first
				e.consume();
				return e;
			}
		}
		return e;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (pendingCollectSlot < 0)
		{
			return;
		}

		int slot = pendingCollectSlot;

		// First tick: dump slot widget info and open the slot detail view
		if (collectTicksWaited == -1)
		{
			Widget slotWidget = client.getWidget(GE_OFFERS_INTERFACE, GE_SLOT_FIRST_CHILD + slot);
			if (slotWidget == null)
			{
				log.info("Collect: slot widget null, aborting");
				pendingCollectSlot = -1;
				return;
			}

			// "View offer" is on a dynamic child of the slot widget
			Widget[] dynChildren = slotWidget.getDynamicChildren();
			Widget viewOfferChild = null;
			if (dynChildren != null)
			{
				for (Widget c : dynChildren)
				{
					if (c == null) continue;
					String[] ca = c.getActions();
					if (ca != null)
					{
						for (int i = 0; i < ca.length; i++)
						{
							if (ca[i] != null && ca[i].contains("View offer"))
							{
								viewOfferChild = c;
								log.info("Collect: found 'View offer' on dyn child index={} id={}", c.getIndex(), c.getId());
								break;
							}
						}
					}
					if (viewOfferChild != null) break;
				}
			}

			if (viewOfferChild == null)
			{
				log.info("Collect: no 'View offer' child found, aborting");
				pendingCollectSlot = -1;
				return;
			}

			log.info("Collect: opening slot {} detail view", slot);
			client.menuAction(
				viewOfferChild.getIndex(),
				viewOfferChild.getId(),
				MenuAction.CC_OP,
				1,
				-1,
				"View offer",
				""
			);
			collectTicksWaited = 0;
			return;
		}

		collectTicksWaited++;
		log.info("Collect: tick {}, checking detail view", collectTicksWaited);

		// Wait for detail view collect area children to become visible
		Widget collectArea = client.getWidget(GE_OFFERS_INTERFACE, GE_COLLECT_AREA);
		if (collectArea == null)
		{
			if (collectTicksWaited >= 10)
			{
				log.info("Collect: gave up waiting for collect area");
				pendingCollectSlot = -1;
			}
			return;
		}

		Widget item2 = collectArea.getChild(2);
		Widget item3 = collectArea.getChild(3);
		boolean anyVisible = (item2 != null && !item2.isSelfHidden())
			|| (item3 != null && !item3.isSelfHidden());

		if (!anyVisible)
		{
			if (collectTicksWaited >= 10)
			{
				log.info("Collect: gave up, children never became visible");
				goBack();
				pendingCollectSlot = -1;
			}
			return;
		}

		// Collect items
		log.info("Collect: detail view ready at tick {}", collectTicksWaited);
		for (int child = 2; child <= 3; child++)
		{
			Widget item = collectArea.getChild(child);
			if (item == null || item.isSelfHidden())
			{
				continue;
			}
			String[] actions = item.getActions();
			if (actions == null)
			{
				continue;
			}
			log.info("Collect: child {} actions: {}", child, java.util.Arrays.toString(actions));
			for (int i = 0; i < actions.length; i++)
			{
				if (actions[i] != null && (actions[i].contains("Collect") || actions[i].equals("Bank")))
				{
					log.info("Collect: invoking child {} [{}]: {}", child, i + 1, actions[i]);
					client.menuAction(
						child,
						item.getId(),
						MenuAction.CC_OP,
						i + 1,
						item.getItemId(),
						actions[i],
						""
					);
					break;
				}
			}
		}

		goBack();
		pendingCollectSlot = -1;
	}

	private void goBack()
	{
		Widget backButton = client.getWidget(GE_OFFERS_INTERFACE, GE_BACK_BUTTON);
		if (backButton != null)
		{
			log.info("Collect: going back to overview");
			client.menuAction(
				-1,
				backButton.getId(),
				MenuAction.CC_OP,
				1,
				-1,
				"Back",
				""
			);
		}
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		return e;
	}
}
