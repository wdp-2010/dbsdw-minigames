package be.dbsdw.minigames.core.arena;

import be.dbsdw.minigames.core.api.ArenaFeature;
import be.dbsdw.minigames.core.api.ModeSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/** Checks an arena before it is saved. Errors block saving; warnings are only shown. */
public final class ArenaValidator {
    public record Validation(List<String> errors, List<String> warnings) {
        public Validation {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
    }

    private ArenaValidator() {}

    public static Validation validate(ArenaDefinition a, Function<String, Optional<ModeSpec>> modes) {
        return validate(a, modes, ArenaCopies.DEFAULT_SPACING);
    }

    public static Validation validate(ArenaDefinition a, Function<String, Optional<ModeSpec>> modes, int copySpacing) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String n = "arena " + a.name() + ": ";
        if (!a.hasBounds()) {
            errors.add(n + "no bounds — set both corners with /mg arena setbounds 1 and 2");
        }
        if (a.modes().isEmpty()) {
            errors.add(n + "no modes — set them with /mg arena modes");
        }
        if (a.hasBounds()) {
            new TreeMap<>(a.spawns()).forEach((team, list) -> list.forEach(p -> {
                if (!a.contains(p)) {
                    errors.add(n + "spawn for team " + team + " at " + p + " is outside the bounds");
                }
            }));
            for (Map.Entry<Integer, Pos> flag : new TreeMap<>(a.flags()).entrySet()) {
                if (!a.contains(flag.getValue())) {
                    errors.add(n + "flag for team " + flag.getKey() + " at " + flag.getValue() + " is outside the bounds");
                }
            }
            for (Pos p : a.powerups()) {
                if (!a.contains(p)) {
                    errors.add(n + "power-up point at " + p + " is outside the bounds");
                }
            }
        }
        if (a.isTemplate() && a.hasBounds()) {
            int step = ArenaCopies.step(a, copySpacing);
            int x = Math.max(a.corner1().blockX(), a.corner2().blockX());
            warnings.add(n + a.copies() + " copies, one per match, built east of the arena every " + step
                    + " blocks (up to x = " + (x + a.copies() * step) + "): keep that space empty");
        }
        for (String key : a.modes().stream().sorted().toList()) {
            Optional<ModeSpec> mode = modes.apply(key);
            if (mode.isEmpty()) {
                warnings.add(n + "mode " + key + " is not registered (yet) — check the spelling");
            } else if (a.hasBounds() && !a.fits(mode.get())) {
                errors.add(n + "mode " + key + " needs a spawn for teams 0.." + (mode.get().teams() - 1)
                        + (mode.get().features().contains(ArenaFeature.FLAGS) ? " and a flag for every team" : ""));
            }
        }
        return new Validation(errors, warnings);
    }
}
