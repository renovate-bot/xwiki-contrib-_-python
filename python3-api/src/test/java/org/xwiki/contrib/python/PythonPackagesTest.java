/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.python;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validate {@link PythonPackages}.
 *
 * @version $Id$
 */
class PythonPackagesTest
{
    @Test
    void normalizeName()
    {
        assertEquals("typing-extensions", PythonPackages.normalizeName("Typing_Extensions"));
        assertEquals("zope-interface", PythonPackages.normalizeName("zope.interface"));
        assertEquals("a-b", PythonPackages.normalizeName("A-_.b"));
        assertEquals("requests", PythonPackages.normalizeName("requests"));
    }

    @Test
    void isValidName()
    {
        assertTrue(PythonPackages.isValidName("requests"));
        assertTrue(PythonPackages.isValidName("Typing_Extensions"));
        assertTrue(PythonPackages.isValidName("a"));
        assertFalse(PythonPackages.isValidName("org.xwiki.commons:xwiki-commons-extension-api"));
        assertFalse(PythonPackages.isValidName("-requests"));
        assertFalse(PythonPackages.isValidName(""));
    }
}
