package com.deepdimen.matrix.zenflow.media;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ZenflowMediaRangeTest {
    @Test void fullOpenEndedSuffixAndClampedEndHaveExactLengths(){
        assertEquals(new ZenflowMediaRange(0,99,100,false),ZenflowMediaRange.parse(null,100));
        assertEquals(new ZenflowMediaRange(10,99,100,true),ZenflowMediaRange.parse("bytes=10-",100));
        assertEquals(new ZenflowMediaRange(90,99,100,true),ZenflowMediaRange.parse("bytes=-10",100));
        assertEquals(new ZenflowMediaRange(10,99,100,true),ZenflowMediaRange.parse("bytes=10-999",100));
        var range=ZenflowMediaRange.parse("bytes=10-19",100);assertEquals(10,range.length());assertEquals("bytes 10-19/100",range.contentRange());
    }
    @Test void malformedMultipleOutOfBoundsAndOverflowRangesAreRejected(){
        for(String range:new String[]{"bytes=-","bytes=-0","bytes=100-","bytes=20-10","bytes=0-1,4-5","items=1-2","bytes=99999999999999999999999-"})assertThrows(IllegalArgumentException.class,()->ZenflowMediaRange.parse(range,100));
        assertThrows(IllegalArgumentException.class,()->ZenflowMediaRange.parse(null,0));
    }
}
