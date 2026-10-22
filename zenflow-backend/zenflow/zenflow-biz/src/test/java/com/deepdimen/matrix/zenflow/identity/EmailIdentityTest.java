package com.deepdimen.matrix.zenflow.identity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EmailIdentityTest {
    @Test void normalizesCaseWithoutMergingAliases() {
        assertEquals("a.b+tag@example.com", EmailIdentity.normalize(" A.B+tag@Example.COM "));
        assertNotEquals(EmailIdentity.normalize("a.b@example.com"), EmailIdentity.normalize("ab@example.com"));
        assertNotEquals(EmailIdentity.normalize("a+one@example.com"), EmailIdentity.normalize("a+two@example.com"));
    }
    @Test void rejectsInvalidAddressesAndBcryptOverlength() {
        assertThrows(IllegalArgumentException.class, () -> EmailIdentity.normalize("a@@example.com"));
        assertThrows(IllegalArgumentException.class, () -> EmailIdentity.normalize("a@example"));
        assertThrows(IllegalArgumentException.class, () -> EmailIdentity.checkPassword("short"));
        assertThrows(IllegalArgumentException.class, () -> EmailIdentity.checkPassword("密码".repeat(25)));
    }
}
