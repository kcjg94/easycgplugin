package com.kcjg94.easycgplugin;

/**
 * Which weapon the player intends to build first, for highlighting the
 * matching option in the Singing Bowl's crafting menu. Not detected
 * automatically - the plugin has no way to know which weapon a player
 * wants before they've committed to one.
 */
public enum GauntletWeaponType
{
	NONE("Don't highlight a weapon"),
	HALBERD("Halberd"),
	BOW("Bow"),
	STAFF("Staff");

	private final String label;

	GauntletWeaponType(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
