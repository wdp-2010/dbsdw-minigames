package be.dbsdw.minigames.core.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class VoiceRuleTest {
    @Test
    void parsesConfigSpellings() {
        assertEquals(Optional.of(VoiceRule.TEAM), VoiceRule.parse("team"));
        assertEquals(Optional.of(VoiceRule.ALL), VoiceRule.parse(" ALL "));
        assertEquals(Optional.of(VoiceRule.NEARBY), VoiceRule.parse("Nearby"));
        assertEquals(Optional.empty(), VoiceRule.parse("teams"));
        assertEquals(Optional.empty(), VoiceRule.parse(null));
    }

    @Test
    void cyclesThroughEveryRule() {
        assertEquals(VoiceRule.ALL, VoiceRule.TEAM.next());
        assertEquals(VoiceRule.NEARBY, VoiceRule.ALL.next());
        assertEquals(VoiceRule.TEAM, VoiceRule.NEARBY.next());
    }

    @Test
    void teamRuleGivesEachTeamItsOwnGroup() {
        assertEquals(Optional.of("m7:0"), VoiceRule.TEAM.groupKey("m7", 0));
        assertEquals(Optional.of("m7:1"), VoiceRule.TEAM.groupKey("m7", 1));
    }

    @Test
    void allRuleSharesOneGroupAndNearbyHasNone() {
        assertEquals(VoiceRule.ALL.groupKey("m7", 0), VoiceRule.ALL.groupKey("m7", 1));
        assertEquals(Optional.empty(), VoiceRule.NEARBY.groupKey("m7", 0));
    }

    @Test
    void groupNamesAreShortAndNeverBlank() {
        assertEquals("Rood", VoiceRule.groupName("  Rood "));
        assertEquals("Team", VoiceRule.groupName("   "));
        assertEquals("Team", VoiceRule.groupName(null));
        assertEquals("Verstoppers van", VoiceRule.groupName("Verstoppers van de Houtkamp"));
        assertEquals("Zoekers-en-vinde", VoiceRule.groupName("Zoekers-en-vinders"));
    }
}
