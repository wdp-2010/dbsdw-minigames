package be.dbsdw.minigames.paintball.rules;

/**
 * What players earn with the shop on and in the bomb mode. Cash is per match and never saved.
 *
 * @param start       cash at the start (and, in the bomb mode, after the sides swap)
 * @param cap         nobody holds more than this
 * @param elimination per enemy knocked out
 * @param capture     per flag captured (capture the flag with shop)
 * @param flagReturn  per own flag brought back
 * @param roundWin    bomb mode: every player of the team that won the round
 * @param roundLoss   bomb mode: every player of the team that lost it
 * @param objective   bomb mode: planting or defusing the bomb
 */
public record ShopCash(int start, int cap, int elimination, int capture, int flagReturn, int roundWin, int roundLoss,
                       int objective) {
    public ShopCash {
        Settings.require(start >= 0 && elimination >= 0 && capture >= 0 && flagReturn >= 0 && roundWin >= 0
                && roundLoss >= 0 && objective >= 0, "shop.cash values must be >= 0");
        Settings.require(cap >= start, "shop.cash.max must be >= shop.cash.start");
    }

    public static ShopCash defaults() {
        return new ShopCash(800, 9000, 300, 600, 200, 3000, 1900, 300);
    }
}
