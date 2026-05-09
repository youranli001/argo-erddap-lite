package com.youranli.argo.service;

import com.youranli.argo.model.Constraint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConstraintParserTest {

    @Test
    void nullCyclesMeansAll() {
        Constraint c = ConstraintParser.parse(null, null);
        assertTrue(c.keepsAllCycles());
        assertNull(c.cycleFilter());
    }

    @Test
    void allKeywordMeansAll() {
        Constraint c = ConstraintParser.parse("all", null);
        assertTrue(c.keepsAllCycles());
    }

    @Test
    void rangeIsInclusiveOnBothEnds() {
        List<Integer> got = ConstraintParser.parseCycles("1-5");
        assertEquals(List.of(1, 2, 3, 4, 5), got);
    }

    @Test
    void explicitListIsParsed() {
        List<Integer> got = ConstraintParser.parseCycles("1,3,5");
        assertEquals(List.of(1, 3, 5), got);
    }

    @Test
    void rangeAndListCombineAndDedupe() {
        List<Integer> got = ConstraintParser.parseCycles("1-3,3,5,7-9");
        assertEquals(List.of(1, 2, 3, 5, 7, 8, 9), got);
    }

    @Test
    void whitespaceIsTolerated() {
        List<Integer> got = ConstraintParser.parseCycles("  1 , 3 - 5 ");
        assertEquals(List.of(1, 3, 4, 5), got);
    }

    @Test
    void invalidIntegerRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ConstraintParser.parseCycles("abc"));
    }

    @Test
    void inverseRangeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ConstraintParser.parseCycles("10-5"));
    }

    @Test
    void negativeCycleRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ConstraintParser.parseCycles("-1"));
    }

    @Test
    void parametersDefaultsWhenMissing() {
        List<String> got = ConstraintParser.parseParameters(null);
        assertEquals(List.of("TEMP", "PSAL", "PRES"), got);
    }

    @Test
    void parametersAreUppercased() {
        List<String> got = ConstraintParser.parseParameters("temp,psal,doxy");
        assertEquals(List.of("TEMP", "PSAL", "DOXY"), got);
    }

    @Test
    void parametersDedupePreservesOrder() {
        List<String> got = ConstraintParser.parseParameters("TEMP,PSAL,TEMP,DOXY");
        assertEquals(List.of("TEMP", "PSAL", "DOXY"), got);
    }
}
