package be.dbsdw.minigames.core.solo;

import be.dbsdw.minigames.core.api.ModeSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The modes {@code /solo} offers, grouped by game in the order the games were registered. */
final class SoloChoices {
    private SoloChoices() {
    }

    static Map<String, List<ModeSpec>> byGame(List<ModeSpec> modes) {
        Map<String, List<ModeSpec>> out = new LinkedHashMap<>();
        for (ModeSpec mode : modes) {
            out.computeIfAbsent(mode.gameId(), g -> new ArrayList<>()).add(mode);
        }
        out.replaceAll((g, list) -> List.copyOf(list));
        return out;
    }
}
