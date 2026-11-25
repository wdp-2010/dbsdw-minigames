package be.dbsdw.minigames.paintball.rules;

/**
 * What the shop sells, in menu order. Bought items last until the player is out (or, in the bomb
 * mode, until the sides swap).
 */
public enum ShopItem {
    /** Absorbs the next hit (the shield power-up). */
    VEST(400, 1, Kind.WORN),
    /** Removes one hit already taken, at once. */
    REPAIR(250, 1, Kind.INSTANT),
    /** Speed I until out. */
    BOOTS(350, 1, Kind.WORN),
    /** Hotbar item: right-click for a burst of rapid fire. */
    RAPID_CARTRIDGE(500, 1, Kind.USED),
    /** Hotbar item: right-click for a burst of triple shots. */
    TRIPLE_CARTRIDGE(600, 1, Kind.USED),
    /** Thrown: one hit on every enemy near where it lands. */
    PAINT_GRENADE(300, 2, Kind.USED),
    /** Thrown: a thick cloud nobody sees through for a while. */
    SMOKE_GRENADE(300, 1, Kind.USED),
    /** Bomb mode, defenders: defuse faster. */
    DEFUSE_KIT(400, 1, Kind.WORN);

    /** WORN: kept and shown while alive; INSTANT: used up when bought; USED: a hotbar item to use. */
    public enum Kind { WORN, INSTANT, USED }

    private final int price;
    private final int max;
    private final Kind kind;

    ShopItem(int price, int max, Kind kind) {
        this.price = price;
        this.max = max;
        this.kind = kind;
    }

    public int price() {
        return price;
    }

    /** How many one player may hold at once. */
    public int max() {
        return max;
    }

    public Kind kind() {
        return kind;
    }

    /** Config and message name, e.g. "paint_grenade". */
    public String key() {
        return name().toLowerCase();
    }

    /** Only sold in the bomb mode, to the defending team. */
    public boolean defendersOnly() {
        return this == DEFUSE_KIT;
    }
}
