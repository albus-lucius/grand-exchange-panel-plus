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
@PluginDescriptor(name = "Grand Exchange Buttons")
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
	volatile boolean suppressButtons = false;

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
		log.info("Grand Exchange Buttons started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		mouseManager.unregisterMouseListener(this);
		pendingCollectSlot = -1;
		log.info("Grand Exchange Buttons stopped");
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		for (int slot = 0; slot < 8; slot++)
		{
			if (config.collectEnable())
			{
				Rectangle bounds = overlay.getProgressBarBound(slot);
				if (bounds != null && bounds.contains(e.getPoint()))
				{
					suppressButtons = true;
					openSlotForCollect(slot);
					e.consume();
					return e;
				}
			}

			if (config.modifyEnable())
			{
				Rectangle modBounds = overlay.getModifyBound(slot);
				if (modBounds != null && modBounds.contains(e.getPoint()))
				{
					suppressButtons = true;
					invokeSlotAction(slot, "Modify");
					e.consume();
					return e;
				}
			}

			if (config.abortEnable())
			{
				Rectangle abortBounds = overlay.getAbortBound(slot);
				if (abortBounds != null && abortBounds.contains(e.getPoint()))
				{
					suppressButtons = true;
					invokeSlotAction(slot, "Abort");
					e.consume();
					return e;
				}
			}
		}
		return e;
	}

	private void openSlotForCollect(int slot)
	{
		clientThread.invokeLater(() ->
		{
			Widget slotWidget = client.getWidget(GE_OFFERS_INTERFACE, GE_SLOT_FIRST_CHILD + slot);
			if (slotWidget == null)
			{
				return;
			}

			Widget[] dynChildren = slotWidget.getDynamicChildren();
			if (dynChildren == null)
			{
				return;
			}

			for (Widget c : dynChildren)
			{
				if (c == null) continue;
				String[] ca = c.getActions();
				if (ca == null) continue;
				for (String action : ca)
				{
					if (action != null && action.contains("View offer"))
					{
						client.menuAction(
							c.getIndex(),
							c.getId(),
							MenuAction.CC_OP,
							1,
							-1,
							"View offer",
							""
						);
						pendingCollectSlot = slot;
						collectTicksWaited = 0;
						return;
					}
				}
			}
		});
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (pendingCollectSlot < 0)
		{
			return;
		}

		collectTicksWaited++;

		// Wait for detail view collect area children to become visible
		Widget collectArea = client.getWidget(GE_OFFERS_INTERFACE, GE_COLLECT_AREA);
		if (collectArea == null)
		{
			if (collectTicksWaited >= 5)
			{
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
			if (collectTicksWaited >= 5)
			{
				goBack();
				pendingCollectSlot = -1;
			}
			return;
		}

		// Collect both item slots and go back immediately
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
			for (int i = 0; i < actions.length; i++)
			{
				if (actions[i] != null && (actions[i].contains("Collect") || actions[i].equals("Bank")))
				{
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

	private void invokeSlotAction(int slot, String actionName)
	{
		clientThread.invokeLater(() ->
		{
			Widget slotWidget = client.getWidget(GE_OFFERS_INTERFACE, GE_SLOT_FIRST_CHILD + slot);
			if (slotWidget == null)
			{
				return;
			}

			Widget[] dynChildren = slotWidget.getDynamicChildren();
			if (dynChildren == null)
			{
				return;
			}

			for (Widget child : dynChildren)
			{
				if (child == null) continue;
				String[] actions = child.getActions();
				if (actions == null) continue;
				for (int i = 0; i < actions.length; i++)
				{
					if (actions[i] != null && actions[i].contains(actionName))
					{
						log.debug("{}: slot {} invoking [{}]: {}", actionName, slot, i + 1, actions[i]);
						client.menuAction(
							child.getIndex(),
							child.getId(),
							MenuAction.CC_OP,
							i + 1,
							-1,
							actions[i],
							""
						);
						return;
					}
				}
			}
			log.debug("{}: no matching action found on slot {}", actionName, slot);
		});

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
