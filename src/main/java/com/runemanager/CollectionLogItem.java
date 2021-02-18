package com.runemanager;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.widgets.Widget;

@Getter
@Setter
class CollectionLogItem
{
	private int id;
	private String name;
	private boolean obtained;
	private int quantity;

	CollectionLogItem(Widget item)
	{
		id = item.getItemId();
		name = item.getName().split(">")[1].split("<")[0].replace(" ", "_").replaceAll("[+.^:,']", "").toLowerCase();
		obtained = item.getOpacity() == 0;
		quantity = obtained ? item.getItemQuantity() : 0;
	}
}