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
package org.xwiki.contrib.python.repository.pypi.internal.utils;

import java.util.Optional;

import org.junit.Test;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.ExtensionNotFoundException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Validate {@link PypiUtils}.
 *
 * @version $Id$
 */
public class PypiUtilsTest
{
    @Test
    public void getPackageName() throws Exception
    {
        assertEquals("requests", PypiUtils.getPackageName("requests"));
        assertEquals("Typing_Extensions", PypiUtils.getPackageName(new ExtensionId("Typing_Extensions", "1.0")));
    }

    @Test(expected = ExtensionNotFoundException.class)
    public void getPackageNameWhenNotPythonPackage() throws Exception
    {
        PypiUtils.getPackageName("org.xwiki.commons:xwiki-commons-extension-api");
    }

    @Test
    public void getVersion()
    {
        assertEquals(Optional.of("1.0"), PypiUtils.getVersion(new ExtensionId("pkg", "1.0")));
        assertEquals(Optional.empty(), PypiUtils.getVersion(new ExtensionId("pkg", "")));
        assertEquals(Optional.empty(), PypiUtils.getVersion(new ExtensionId("pkg")));
    }

    @Test
    public void isPureWheel()
    {
        assertTrue(PypiUtils.isPureWheel("pygments-2.21.0-py3-none-any.whl"));
        assertTrue(PypiUtils.isPureWheel("six-1.17.0-py2.py3-none-any.whl"));
        assertTrue(PypiUtils.isPureWheel("pkg-1.0-1-py312-none-any.whl"));
        assertFalse(PypiUtils.isPureWheel("pkg-1.0-py2-none-any.whl"));
        assertFalse(PypiUtils.isPureWheel("pkg-1.0-cp312-cp312-manylinux_2_17_x86_64.whl"));
        assertFalse(PypiUtils.isPureWheel("pkg-1.0-py3-abi3-any.whl"));
        assertFalse(PypiUtils.isPureWheel("pkg-1.0.tar.gz"));
        assertFalse(PypiUtils.isPureWheel("pkg-py3-none-any.whl"));
        assertFalse(PypiUtils.isPureWheel(null));
    }

    @Test
    public void toVersion()
    {
        assertEquals("2.0rc1", PypiUtils.toVersion("2.0rc1").getValue());
        assertTrue(PypiUtils.toVersion("1.12").compareTo(PypiUtils.toVersion("1.2.3")) > 0);
    }
}
