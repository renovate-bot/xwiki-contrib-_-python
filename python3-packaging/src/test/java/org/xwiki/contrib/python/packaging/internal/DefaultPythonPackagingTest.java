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
package org.xwiki.contrib.python.packaging.internal;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.python.PythonPackages;
import org.xwiki.contrib.python.engine.Python3Engine;
import org.xwiki.contrib.python.engine.internal.DefaultPython3Engine;
import org.xwiki.contrib.python.packaging.PythonMetadata;
import org.xwiki.contrib.python.packaging.PythonPackagingException;
import org.xwiki.contrib.python.packaging.PythonRequirement;
import org.xwiki.environment.Environment;
import org.xwiki.extension.ExtensionDependency;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.XWikiTempDir;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Validate {@link DefaultPythonPackaging} (with the actual GraalPy runtime).
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(DefaultPython3Engine.class)
class DefaultPythonPackagingTest
{
    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @InjectMockComponents
    private DefaultPythonPackaging packaging;

    @MockComponent
    private Environment environment;

    @XWikiTempDir
    private File temporaryDirectory;

    @BeforeEach
    void beforeEach()
    {
        when(this.environment.getTemporaryDirectory()).thenReturn(this.temporaryDirectory);
    }

    @AfterEach
    void afterEach()
    {
        this.packaging.dispose();
    }

    private String toRange(String requirement) throws PythonPackagingException
    {
        return this.packaging.getRequirements(List.of("pkg" + requirement)).get(0).getVersionRange();
    }

    @Test
    void getPythonVersion() throws PythonPackagingException
    {
        assertTrue(this.packaging.getPythonVersion().matches("3\\.\\d+\\.\\d+"));
    }

    @Test
    void isPythonSupported() throws PythonPackagingException
    {
        assertTrue(this.packaging.isPythonSupported(">=3.8"));
        assertTrue(this.packaging.isPythonSupported(">=2.7, !=3.0.*, !=3.1.*"));
        assertTrue(this.packaging.isPythonSupported(""));
        assertFalse(this.packaging.isPythonSupported("<3"));
        assertFalse(this.packaging.isPythonSupported(">=4"));
        assertThrows(PythonPackagingException.class, () -> this.packaging.isPythonSupported("invalid"));
    }

    @Test
    void isCompatibleWheel() throws PythonPackagingException
    {
        assertTrue(this.packaging.isCompatibleWheel("pkg-1.0-py3-none-any.whl"));
        assertTrue(this.packaging.isCompatibleWheel("pkg-1.0-py2.py3-none-any.whl"));
        assertTrue(this.packaging.isCompatibleWheel("pkg-1.0-1-py3-none-any.whl"));
        assertFalse(this.packaging.isCompatibleWheel("pkg-1.0.tar.gz"));
        assertFalse(this.packaging.isCompatibleWheel("pkg-none-any.whl"));
        assertFalse(this.packaging.isCompatibleWheel("pkg-1.0-py2-none-any.whl"));
        assertFalse(this.packaging.isCompatibleWheel("pkg-1.0-py3-abi3-any.whl"));
        assertFalse(this.packaging.isCompatibleWheel("pkg-1.0-py3-none-win_amd64.whl"));
        assertFalse(this.packaging.isCompatibleWheel("pkg-1.0-cp312-cp312-manylinux_2_17_x86_64.whl"));
        assertFalse(this.packaging.isCompatibleWheel("pkg-1.0-py399-none-any.whl"));
    }

    @Test
    void getRequirements() throws PythonPackagingException
    {
        List<PythonRequirement> requirements = this.packaging.getRequirements(Arrays.asList(
            "charset_normalizer<4,>=2", "aniso8601 (==1.2.1)", "Typing.Extensions",
            "requests[socks] >=2.8.1 ; python_version < \"2.7\"", "PySocks!=1.5.7,>=1.5.6; extra == \"socks\"",
            "importlib-metadata; python_version < \"3.8\"", "foo; python_version >= \"3.8\"",
            "pip @ https://example.com/pip-1.0.whl"));

        assertEquals(Arrays.asList("charset_normalizer<4,>=2", "aniso8601==1.2.1", "Typing.Extensions",
            "foo", "pip"),
            requirements.stream().map(PythonRequirement::toString).collect(Collectors.toList()));

        ExtensionDependency dependency = requirements.get(0).toExtensionDependency();
        assertEquals("charset-normalizer", dependency.getId());
        assertEquals("[2,4)", dependency.getVersionConstraint().getValue());
        assertEquals("<4,>=2", dependency.getProperty(PythonPackages.DEPENDENCY_SPECIFIERS));

        dependency = requirements.get(2).toExtensionDependency();
        assertEquals("typing-extensions", dependency.getId());
        assertEquals("(,)", dependency.getVersionConstraint().getValue());
        assertNull(dependency.getProperty(PythonPackages.DEPENDENCY_SPECIFIERS));

        assertThrows(PythonPackagingException.class, () -> this.packaging.getRequirements(List.of(">=2")));
    }

    @Test
    void getRequirementsVersionRange() throws PythonPackagingException
    {
        assertEquals("[2.0,)", toRange(">=2.0"));
        assertEquals("(2.0,)", toRange(">2.0"));
        assertEquals("(1.0,)", toRange(">1.0,>=1.0"));
        assertEquals("(,4]", toRange("<=4"));
        assertEquals("(,4)", toRange("<=4,<4"));
        assertEquals("[2,4)", toRange("<4,>=2"));
        assertEquals("[2.1,3)", toRange(">=2,>=2.1,<3,<4"));
        assertEquals("[1.4.2]", toRange("==1.4.2"));
        assertEquals("[1.4,1.5)", toRange("==1.4.*"));
        assertEquals("[1!1.4,1!1.5)", toRange("==1!1.4.*"));
        assertEquals("[1.4.2,1.5)", toRange("~=1.4.2"));
        assertEquals("[2.2,3)", toRange("~=2.2"));
        assertEquals("[2,)", toRange(">=2,!=2.5"));
        assertEquals("(,)", toRange("!=2.5"));
        assertEquals("(,)", toRange(">=3,<2"));
        assertEquals("(,)", toRange(">=2,<2"));
        assertEquals("[1.0]", toRange("===1.0"));
        assertEquals("(,)", toRange("===latest"));
    }

    @Test
    void filterVersions() throws PythonPackagingException
    {
        List<String> versions = Arrays.asList("1.0", "1.0.dev1", "1.0a1", "latest", "1.0c2", "1.0.post1", "1!0.5",
            "2.0rc1");

        // PEP 440 ordering, invalid versions ignored, pre-releases excluded when final releases match
        assertEquals(Arrays.asList("1!0.5", "1.0.post1", "1.0"), this.packaging.filterVersions(versions, "", null));
        assertEquals(Arrays.asList("1!0.5", "2.0rc1", "1.0.post1", "1.0", "1.0c2", "1.0a1", "1.0.dev1"),
            this.packaging.filterVersions(versions, null, true));
        // Pre-releases are included when no final release matches
        assertEquals(Arrays.asList("1.0a1", "1.0.dev1"),
            this.packaging.filterVersions(versions, "<1.0b5", null));
        // <V does not match the pre-releases of V
        assertEquals(Collections.emptyList(), this.packaging.filterVersions(versions, "<1.0", null));
        assertEquals(Arrays.asList("1.0.post1", "1.0"), this.packaging.filterVersions(versions, "<2", false));

        assertThrows(PythonPackagingException.class, () -> this.packaging.filterVersions(versions, "invalid", null));
    }

    @Test
    void selectWheels() throws PythonPackagingException
    {
        // The tag of the runtime Python version (like py313)
        String versionTag = "py" + this.packaging.getPythonVersion().replaceAll("^(\\d+)\\.(\\d+).*$", "$1$2");

        Map<String, String> wheels = new LinkedHashMap<>();
        wheels.put("pkg-2.0-py3-none-any.whl", ">=4");
        wheels.put("pkg-1.5-cp312-cp312-manylinux_2_17_x86_64.whl", null);
        wheels.put("pkg-1.1-py2.py3-none-any.whl", null);
        wheels.put("pkg-1.1-" + versionTag + "-none-any.whl", null);
        wheels.put("pkg-1.0-py3-none-any.whl", "invalid");
        wheels.put("pkg-1.0.0-1-py3-none-any.whl", ">=3");
        wheels.put("invalid.whl", null);

        Map<String, String> expected = new LinkedHashMap<>();
        // The most specific tag wins
        expected.put("1.1", "pkg-1.1-" + versionTag + "-none-any.whl");
        // Versions are matched as versions, and an invalid Requires-Python does not exclude a wheel
        expected.put("1.0.0", "pkg-1.0-py3-none-any.whl");

        assertEquals(expected,
            this.packaging.selectWheels(Arrays.asList("2.0", "1.5", "1.1", "invalid", "1.0.0"), wheels));
    }

    @Test
    void parseMetadata() throws PythonPackagingException
    {
        PythonMetadata metadata = this.packaging.parseMetadata(String.join("\n", "Metadata-Version: 2.4",
            "Name: Typing_Extensions", "Version: 4.12.0", "Summary: Backported types",
            "Home-page: https://example.com/home", "License-Expression: PSF-2.0",
            "Project-URL: Documentation, https://example.com/doc", "Project-URL: Source, https://example.com/src",
            "Requires-Python: >=3.8", "Requires-Dist: charset_normalizer<4,>=2",
            "Requires-Dist: PySocks; extra == \"socks\"", "", "The long description.", ""));

        assertEquals("Typing_Extensions", metadata.getName());
        assertEquals("4.12.0", metadata.getVersion());
        assertEquals("Backported types", metadata.getSummary());
        assertEquals("The long description.\n", metadata.getDescription());
        assertNull(metadata.getLicense());
        assertEquals("PSF-2.0", metadata.getLicenseExpression());
        assertEquals("https://example.com/home", metadata.getHomePage());
        assertEquals(Map.of("Documentation", "https://example.com/doc", "Source", "https://example.com/src"),
            metadata.getProjectUrls());
        assertEquals(">=3.8", metadata.getRequiresPython());
        assertEquals(Arrays.asList("charset_normalizer<4,>=2", "PySocks; extra == \"socks\""),
            metadata.getRequiresDist());

        metadata = this.packaging.parseMetadata("Name: pkg\nVersion: 1.0\n");
        assertNull(metadata.getSummary());
        assertEquals(Map.of(), metadata.getProjectUrls());
        assertEquals(List.of(), metadata.getRequiresDist());
    }

    @Test
    void sharedEngine() throws Exception
    {
        // A context created by another user of the shared engine, like the Python 3 macro
        Python3Engine engine = this.componentManager.getInstance(Python3Engine.class);
        try (Context context = engine.createContextBuilder().option("python.PythonPath", "/other").build()) {
            assertEquals(2, context.eval("python", "1 + 1").asInt());

            assertTrue(this.packaging.isPythonSupported(">=3"));
        }
    }

    @Test
    void reinitializeAfterDispose() throws PythonPackagingException
    {
        assertTrue(this.packaging.isPythonSupported(">=3"));

        this.packaging.dispose();

        assertTrue(this.packaging.isPythonSupported(">=3"));
    }
}
