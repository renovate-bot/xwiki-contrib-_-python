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
package org.xwiki.contrib.python.repository.pypi.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.xwiki.contrib.python.PythonPackages;
import org.xwiki.contrib.python.engine.internal.DefaultPython3Engine;
import org.xwiki.contrib.python.packaging.internal.DefaultPythonPackaging;
import org.xwiki.contrib.python.repository.pypi.internal.searching.PypiPackageIndexManager;
import org.xwiki.contrib.python.repository.pypi.internal.utils.PypiHttpClient;
import org.xwiki.environment.Environment;
import org.xwiki.extension.DefaultExtensionDependency;
import org.xwiki.extension.Extension;
import org.xwiki.extension.ExtensionDependency;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.ExtensionLicenseManager;
import org.xwiki.extension.ExtensionManagerConfiguration;
import org.xwiki.extension.ExtensionNotFoundException;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.repository.result.IterableResult;
import org.xwiki.extension.version.Version;
import org.xwiki.extension.version.internal.DefaultVersion;
import org.xwiki.extension.version.internal.DefaultVersionConstraint;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Validate {@link PypiExtensionRepository}.
 * 
 * @version $Id$
 */
public class PypiExtensionRepositoryTest
{
    private static final String DECORATOR_SIMPLE = "{\"meta\":{\"api-version\":\"1.4\"},\"name\":\"decorator\","
        + "\"versions\":[\"4.0.11\",\"4.4.2\",\"5.3.1\"],\"files\":[]}";

    private static final String FILES_URL = "https://files.pythonhosted.org/packages/";

    private static final String POPULAR_PACKAGES_URL = "https://example.com/top-pypi-packages.json";

    private static final int EMBEDDED_INDEX_SIZE = 901095;

    @Rule
    public MockitoComponentMockingRule<PypiExtensionRepository> mocker =
        new MockitoComponentMockingRule<>(PypiExtensionRepository.class);

    /**
     * The body returned for each requested URI, all other URIs answer with a 404.
     */
    private final Map<String, byte[]> responses = new HashMap<>();

    /**
     * The {@code Accept} header sent for the requested URIs.
     */
    private final Map<String, String> accepts = new HashMap<>();

    /**
     * The number of requests of each URI.
     */
    private final Map<String, Integer> requests = new HashMap<>();

    /**
     * The versions listed by the project page of each package.
     */
    private final Map<String, Set<String>> projectVersions = new HashMap<>();

    /**
     * The files listed by the project page of each package.
     */
    private final Map<String, List<Map<String, Object>>> projectFiles = new HashMap<>();

    private File testDirectory;

    /**
     * Describe a release published in the simulated index.
     */
    private static final class Release
    {
        private String requiresPython;

        private List<String> requiresDist = Collections.emptyList();

        private String filenameSuffix = "-py3-none-any.whl";

        private boolean coreMetadata = true;

        private boolean metadataRequiresPython;

        private boolean yanked;

        private String metadataHash;

        private String wheelHash;

        private boolean hasWheelHash = true;

        Release requiresPython(String value)
        {
            this.requiresPython = value;
            return this;
        }

        Release requiresDist(String... values)
        {
            this.requiresDist = Arrays.asList(values);
            return this;
        }

        Release sdist()
        {
            this.filenameSuffix = ".tar.gz";
            return this;
        }

        Release nativeWheel()
        {
            this.filenameSuffix = "-cp312-cp312-manylinux_2_17_x86_64.whl";
            return this;
        }

        Release noCoreMetadata()
        {
            this.coreMetadata = false;
            return this;
        }

        Release requiresPythonOnlyInMetadata(String value)
        {
            this.requiresPython = value;
            this.metadataRequiresPython = true;
            return this;
        }

        Release yanked()
        {
            this.yanked = true;
            return this;
        }

        Release metadataHash(String value)
        {
            this.metadataHash = value;
            return this;
        }

        Release wheelHash(String value)
        {
            this.wheelHash = value;
            return this;
        }

        Release noWheelHash()
        {
            this.hasWheelHash = false;
            return this;
        }
    }

    private void putResponse(String uri, String body)
    {
        this.responses.put(uri, body.getBytes(StandardCharsets.UTF_8));
    }

    private void putRelease(String name, String version, Release release) throws Exception
    {
        String normalizedName = PythonPackages.normalizeName(name);
        // The distribution name is escaped in the wheel file names (PEP 427)
        String filename = name.replace('-', '_') + "-" + version + release.filenameSuffix;
        String url = FILES_URL + filename;

        StringBuilder metadata = new StringBuilder();
        metadata.append("Metadata-Version: 2.1\nName: ").append(name).append("\nVersion: ").append(version)
            .append("\nSummary: Summary of ").append(name).append('\n');
        if (release.requiresPython != null) {
            metadata.append("Requires-Python: ").append(release.requiresPython).append('\n');
        }
        release.requiresDist.forEach(dependency -> metadata.append("Requires-Dist: ").append(dependency).append('\n'));
        metadata.append("\nThe description of ").append(name).append('\n');
        byte[] metadataBytes = metadata.toString().getBytes(StandardCharsets.UTF_8);

        Map<String, Object> file = new LinkedHashMap<>();
        file.put("filename", filename);
        file.put("url", url);
        file.put("size", 42);
        file.put("yanked", release.yanked ? "Broken release" : Boolean.FALSE);
        if (release.requiresPython != null && !release.metadataRequiresPython) {
            file.put("requires-python", release.requiresPython);
        }
        if (release.coreMetadata) {
            String hash = release.metadataHash != null ? release.metadataHash
                : HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(metadataBytes));
            file.put("core-metadata", Map.of("sha256", hash));
            this.responses.put(url + ".metadata", metadataBytes);
        } else {
            file.put("core-metadata", Boolean.FALSE);
        }
        byte[] wheel = createWheel(name, version, metadataBytes);
        this.responses.put(url, wheel);
        if (release.hasWheelHash) {
            file.put("hashes", Map.of("sha256", release.wheelHash != null ? release.wheelHash
                : HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(wheel))));
        } else {
            file.put("hashes", Map.of());
        }

        this.projectVersions.computeIfAbsent(normalizedName, key -> new LinkedHashSet<>()).add(version);
        this.projectFiles.computeIfAbsent(normalizedName, key -> new ArrayList<>()).add(file);
        updateProjectPage(normalizedName);
    }

    private void putPackage(String name, String version, String requiresPython, List<String> requiresDist,
        boolean wheel) throws Exception
    {
        Release release = new Release().requiresPython(requiresPython)
            .requiresDist(requiresDist != null ? requiresDist.toArray(new String[0]) : new String[0]);
        putRelease(name, version, wheel ? release : release.sdist());
    }

    private void putVersions(String name, String... versions) throws Exception
    {
        this.projectVersions.computeIfAbsent(name, key -> new LinkedHashSet<>()).addAll(Arrays.asList(versions));
        updateProjectPage(name);
    }

    private void updateProjectPage(String name) throws Exception
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("meta", Map.of("api-version", "1.4"));
        data.put("name", name);
        data.put("versions", new ArrayList<>(this.projectVersions.getOrDefault(name, Set.of())));
        data.put("files", this.projectFiles.getOrDefault(name, List.of()));

        putResponse("https://pypi.org/simple/" + name + "/", new ObjectMapper().writeValueAsString(data));
    }

    private static byte[] createWheel(String name, String version, byte[] metadata) throws Exception
    {
        ByteArrayOutputStream wheel = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(wheel)) {
            zip.putNextEntry(new ZipEntry(name.toLowerCase() + "/__init__.py"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(name + "-" + version + ".dist-info/METADATA"));
            zip.write(metadata);
            zip.closeEntry();
        }

        return wheel.toByteArray();
    }

    private ExtensionDependency dependency(String name, String constraint)
    {
        return new DefaultExtensionDependency(name, new DefaultVersionConstraint(constraint));
    }

    private String getPythonAPIVersionConstraint() throws Exception
    {
        return this.mocker.<PypiExtensionFactory>getInstance(PypiExtensionFactory.class).getPythonAPIVersionConstraint();
    }

    private static List<String> getDependencies(Extension extension)
    {
        return extension.getDependencies().stream()
            .map(dependency -> dependency.getId() + "-" + dependency.getVersionConstraint())
            .collect(Collectors.toList());
    }

    @Before
    public void before() throws Exception
    {
        this.mocker.registerMockComponent(ExtensionManagerConfiguration.class);
        // Use the actual Python packaging standards implementation (running GraalPy)
        this.mocker.registerComponent(DefaultPython3Engine.class);
        this.mocker.registerComponent(DefaultPythonPackaging.class);
        this.mocker.registerComponent(PypiSimpleApiClient.class);
        this.mocker.registerComponent(PypiExtensionFactory.class);
        this.mocker.registerComponent(PypiVersionSelector.class);
        this.mocker.registerComponent(PypiPackageIndexManager.class);

        Environment environment = this.mocker.registerMockComponent(Environment.class);

        this.testDirectory = new File("target/test-" + System.nanoTime()).getAbsoluteFile();
        File permdir = new File(this.testDirectory, "perm");
        permdir.mkdirs();
        File tempDir = new File(this.testDirectory, "temp");
        tempDir.mkdirs();

        when(environment.getPermanentDirectory()).thenReturn(permdir);
        when(environment.getTemporaryDirectory()).thenReturn(tempDir);

        this.mocker.registerMockComponent(ExtensionLicenseManager.class);
        PypiConfiguration configuration = this.mocker.registerMockComponent(PypiConfiguration.class);
        when(configuration.getPopularPackagesCount()).thenReturn(3);
        when(configuration.getPopularPackagesURL()).thenReturn(POPULAR_PACKAGES_URL);
        putResponse(POPULAR_PACKAGES_URL, "{\"last_update\":\"2026-10-01\",\"rows\":[{\"download_count\":3,"
            + "\"project\":\"Pkg_A\"},{\"project\":\"pkg-b\"},{\"project\":\"pkg-c\"},{\"project\":\"other\"}]}");
        PypiHttpClient httpClient = this.mocker.registerMockComponent(PypiHttpClient.class);
        when(httpClient.openStream(any(), any()))
            .then(invocation -> response(invocation.getArgument(0), invocation.getArgument(1)));
    }

    private synchronized InputStream response(URI uri, String accept)
    {
        String uriString = uri.toString();
        this.accepts.put(uriString, accept);
        this.requests.merge(uriString, 1, Integer::sum);

        byte[] body = this.responses.get(uriString);

        return body != null ? new ByteArrayInputStream(body) : null;
    }

    /**
     * @return the project pages which were requested (the global index of PyPI is excluded since it's requested in
     *         the background)
     */
    private synchronized List<String> getRequestedProjectPages()
    {
        return this.accepts.keySet().stream()
            .filter(uri -> uri.startsWith(PypiParameters.PACKAGE_LIST_SIMPLE_API)
                && !uri.equals(PypiParameters.PACKAGE_LIST_SIMPLE_API))
            .sorted().collect(Collectors.toList());
    }

    private synchronized int getRequestCount(String uri)
    {
        return this.requests.getOrDefault(uri, 0);
    }

    @Test
    public void resolveVersions() throws Exception
    {
        putResponse("https://pypi.org/simple/decorator/", DECORATOR_SIMPLE);

        IterableResult<Version> versions = this.mocker.getComponentUnderTest().resolveVersions("decorator", 1, 5);

        assertEquals(3, versions.getTotalHits());
        assertEquals(1, versions.getOffset());
        List<String> values = new ArrayList<>();
        versions.forEach(version -> values.add(version.getValue()));
        assertEquals(Arrays.asList("4.4.2", "5.3.1"), values);
        assertEquals(PypiParameters.SIMPLE_API_JSON_MEDIA_TYPE, this.accepts.get("https://pypi.org/simple/decorator/"));
    }

    @Test(expected = ExtensionNotFoundException.class)
    public void resolveVersionsWhenPackageDoesNotExist() throws Exception
    {
        this.mocker.getComponentUnderTest().resolveVersions("doesnotexist", 0, -1);
    }

    @Test(expected = ExtensionNotFoundException.class)
    public void resolveVersionsWhenNotPythonPackage() throws Exception
    {
        this.mocker.getComponentUnderTest().resolveVersions("org.xwiki.commons:xwiki-commons-extension-api", 0, -1);
    }

    @Test(expected = ExtensionNotFoundException.class)
    public void resolveVersionsWhenNoVersion() throws Exception
    {
        putResponse("https://pypi.org/simple/empty/", "{\"name\":\"empty\",\"versions\":[]}");

        this.mocker.getComponentUnderTest().resolveVersions("empty", 0, -1);
    }

    @Test
    public void getProjectUsesNormalizedNameAndCache() throws Exception
    {
        putResponse("https://pypi.org/simple/decorator/", DECORATOR_SIMPLE);

        PypiSimpleApiClient client = this.mocker.getInstance(PypiSimpleApiClient.class);
        assertEquals("decorator", client.getProject("Decorator").getName());
        assertEquals("decorator", client.getProject("decorator").getName());

        assertEquals(1, getRequestCount("https://pypi.org/simple/decorator/"));
    }

    private PypiPackageIndexManager getIndexManager() throws Exception
    {
        // Make sure the repository is initialized
        this.mocker.getComponentUnderTest();

        return this.mocker.getInstance(PypiPackageIndexManager.class);
    }

    private List<String> exportIndex(PypiPackageIndexManager indexManager, File exported) throws Exception
    {
        indexManager.exportIndex(exported);

        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(exported))) {
            zip.getNextEntry();
            return IOUtils.readLines(zip, StandardCharsets.UTF_8);
        }
    }

    @Test
    public void importIndexWithVersions() throws Exception
    {
        PypiPackageIndexManager indexManager = getIndexManager();

        // Indexes exported by older versions contain the version of each package after a tab
        ByteArrayOutputStream index = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(index)) {
            zip.putNextEntry(new ZipEntry("index.txt"));
            zip.write("decorator\t4.4.1\n\nnetworkx\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        indexManager.importIndex(new ByteArrayInputStream(index.toByteArray()));

        assertEquals(Arrays.asList("decorator", "networkx"),
            exportIndex(indexManager, new File(this.testDirectory, "index.zip")));
    }

    @Test
    public void exportAndImportIndex() throws Exception
    {
        PypiExtensionRepository repository = this.mocker.getComponentUnderTest();
        PypiPackageIndexManager indexManager = getIndexManager();

        File exported = new File(this.testDirectory, "index.zip");
        List<String> lines = exportIndex(indexManager, exported);
        assertEquals(EMBEDDED_INDEX_SIZE, lines.size());
        assertTrue(lines.contains("decorator"));

        try (InputStream stream = new FileInputStream(exported)) {
            indexManager.importIndex(stream);
        }

        // The package is found first in the index (with the others containing its name), and listed even if it has no
        // compatible distribution
        putRelease("decorator", "5.3.1", new Release().nativeWheel());
        IterableResult<Extension> result = repository.search("decorator", 0, 1);
        assertEquals(1, result.getSize());
        assertNull(result.iterator().next().getFile());
        assertTrue(result.getTotalHits() > 1);
        assertEquals(Arrays.asList("https://pypi.org/simple/decorator/"), getRequestedProjectPages());
    }

    @Test
    public void search() throws Exception
    {
        PypiExtensionRepository repository = this.mocker.getComponentUnderTest();

        ByteArrayOutputStream index = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(index)) {
            zip.putNextEntry(new ZipEntry("index.txt"));
            zip.write("pkg-a\npkg-b\npkg-c\nother\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        getIndexManager().importIndex(new ByteArrayInputStream(index.toByteArray()));

        putRelease("pkg-a", "1.0", new Release());
        putRelease("pkg-b", "2.0", new Release().nativeWheel());
        putRelease("pkg-c", "3.0", new Release());

        IterableResult<Extension> result = repository.search("pkg", 0, 10);

        assertEquals(3, result.getTotalHits());
        List<Extension> extensions = new ArrayList<>();
        result.forEach(extensions::add);
        assertEquals(
            Arrays.asList(new ExtensionId("pkg-a", "1.0"), new ExtensionId("pkg-b", "2.0"),
                new ExtensionId("pkg-c", "3.0")),
            extensions.stream().map(Extension::getId).collect(Collectors.toList()));
        // The package without compatible distribution is listed, but can't be installed
        assertNull(extensions.get(1).getFile());
        assertEquals("https://pypi.org/project/pkg-b/", extensions.get(1).getWebSite());
    }

    @Test
    public void searchWithoutQuery() throws Exception
    {
        PypiExtensionRepository repository = this.mocker.getComponentUnderTest();
        PypiPackageIndexManager indexManager = getIndexManager();

        // The list of the most downloaded packages is updated in the background when the repository is initialized
        long timeout = System.currentTimeMillis() + 30000;
        while (!indexManager.search("", 0, -1).iterator().next().equals("pkg-a")) {
            assertTrue("The list of the most downloaded packages was not updated", System.currentTimeMillis() < timeout);
            Thread.sleep(100);
        }

        putRelease("pkg-a", "1.0", new Release());
        putRelease("pkg-b", "2.0", new Release());
        putRelease("pkg-c", "3.0", new Release());
        putRelease("other", "4.0", new Release());

        // Only the configured number of most downloaded packages are listed, from the most downloaded one
        IterableResult<Extension> result = repository.search("", 0, 10);
        assertEquals(3, result.getTotalHits());
        List<ExtensionId> ids = new ArrayList<>();
        result.forEach(extension -> ids.add(extension.getId()));
        assertEquals(Arrays.asList(new ExtensionId("pkg-a", "1.0"), new ExtensionId("pkg-b", "2.0"),
            new ExtensionId("pkg-c", "3.0")), ids);

        result = repository.search(" ", 1, 1);
        assertEquals(3, result.getTotalHits());
        assertEquals(new ExtensionId("pkg-b", "2.0"), result.iterator().next().getId());
    }

    @Test
    public void resolveExtensionId() throws Exception
    {
        putRelease("Typing_Extensions", "4.12.0", new Release().requiresPython(">=3.8").requiresDist(
            "charset_normalizer<4,>=2", "idna (>=2.5)", "PySocks!=1.5.7,>=1.5.6; extra == \"socks\"",
            "importlib-metadata; python_version < \"3.8\""));

        Extension extension =
            this.mocker.getComponentUnderTest().resolve(new ExtensionId("Typing_Extensions", "4.12.0"));

        assertEquals(new ExtensionId("typing-extensions", "4.12.0"), extension.getId());
        assertEquals("Typing_Extensions", extension.getName());
        assertEquals("Summary of Typing_Extensions", extension.getSummary());
        assertEquals("The description of Typing_Extensions\n", extension.getDescription());
        assertEquals("https://pypi.org/project/typing-extensions/", extension.getWebSite());
        assertEquals(PythonPackages.TYPE_WHEEL, extension.getType());
        assertEquals(42, extension.getFile().getLength());
        assertEquals(
            Arrays.asList(PypiParameters.PYTHON_API_ID + "-" + getPythonAPIVersionConstraint(),
                "charset-normalizer-[2,4)", "idna-[2.5,)"),
            getDependencies(extension));

        // The wheel can be downloaded
        try (InputStream stream = extension.getFile().openStream()) {
            assertEquals(this.responses.get(FILES_URL + "Typing_Extensions-4.12.0-py3-none-any.whl").length,
                stream.readAllBytes().length);
        }
    }

    @Test
    public void resolveExtensionIdWithoutVersion() throws Exception
    {
        putRelease("pkg", "1.0", new Release());
        putRelease("pkg", "2.0", new Release());
        putRelease("pkg", "3.0rc1", new Release());
        putRelease("pkg", "4.0", new Release().nativeWheel());

        assertEquals(new ExtensionId("pkg", "2.0"),
            this.mocker.getComponentUnderTest().resolve(new ExtensionId("pkg")).getId());
    }

    @Test
    public void resolveExtensionIdWithMetadataInWheel() throws Exception
    {
        putRelease("pkg", "1.0", new Release().noCoreMetadata().requiresDist("idna"));

        assertEquals(Arrays.asList(PypiParameters.PYTHON_API_ID + "-" + getPythonAPIVersionConstraint(),
            "idna-(,)"), getDependencies(this.mocker.getComponentUnderTest().resolve(new ExtensionId("pkg", "1.0"))));
        assertEquals(0, getRequestCount(FILES_URL + "pkg-1.0-py3-none-any.whl.metadata"));
    }

    @Test
    public void resolveExtensionIdCachesMetadata() throws Exception
    {
        putRelease("pkg", "1.0", new Release());

        PypiExtensionRepository repository = this.mocker.getComponentUnderTest();
        repository.resolve(new ExtensionId("pkg", "1.0"));
        repository.resolve(new ExtensionId("pkg", "1.0"));

        assertEquals(1, getRequestCount("https://pypi.org/simple/pkg/"));
        assertEquals(1, getRequestCount(FILES_URL + "pkg-1.0-py3-none-any.whl.metadata"));
    }

    @Test(expected = ResolveException.class)
    public void resolveExtensionIdWithInvalidMetadataHash() throws Exception
    {
        putRelease("pkg", "1.0", new Release().metadataHash("0000"));

        this.mocker.getComponentUnderTest().resolve(new ExtensionId("pkg", "1.0"));
    }

    @Test
    public void downloadWheelWithInvalidHash() throws Exception
    {
        putRelease("pkg", "1.0", new Release().wheelHash("0000"));

        Extension extension = this.mocker.getComponentUnderTest().resolve(new ExtensionId("pkg", "1.0"));

        try (InputStream stream = extension.getFile().openStream()) {
            IOException exception = assertThrows(IOException.class, stream::readAllBytes);
            assertTrue(exception.getMessage().startsWith("Unexpected SHA-256 hash"));
        }
    }

    @Test
    public void downloadWheelWithoutHash() throws Exception
    {
        putRelease("pkg", "1.0", new Release().noWheelHash());

        Extension extension = this.mocker.getComponentUnderTest().resolve(new ExtensionId("pkg", "1.0"));

        try (InputStream stream = extension.getFile().openStream()) {
            assertEquals(this.responses.get(FILES_URL + "pkg-1.0-py3-none-any.whl").length,
                stream.readAllBytes().length);
        }
    }

    @Test
    public void resolveYankedVersion() throws Exception
    {
        putRelease("pkg", "1.0", new Release());
        putRelease("pkg", "2.0", new Release().yanked());

        PypiExtensionRepository repository = this.mocker.getComponentUnderTest();
        // Explicitly requested
        assertEquals(new ExtensionId("pkg", "2.0"), repository.resolve(new ExtensionId("pkg", "2.0")).getId());
        // Not explicitly requested
        assertEquals(new ExtensionId("pkg", "1.0"), repository.resolve(dependency("pkg", "(,)")).getId());
    }

    @Test(expected = ExtensionNotFoundException.class)
    public void resolveExtensionIdWhenPythonVersionNotSupported() throws Exception
    {
        putRelease("future", "1.0", new Release().requiresPython(">=4"));

        this.mocker.getComponentUnderTest().resolve(new ExtensionId("future", "1.0"));
    }

    @Test(expected = ResolveException.class)
    public void resolveExtensionIdWhenPythonVersionOnlyInMetadataNotSupported() throws Exception
    {
        putRelease("future", "1.0", new Release().requiresPythonOnlyInMetadata(">=4"));

        this.mocker.getComponentUnderTest().resolve(new ExtensionId("future", "1.0"));
    }

    @Test(expected = ResolveException.class)
    public void resolveExtensionIdWithInvalidDependency() throws Exception
    {
        putRelease("invalid", "1.0", new Release().requiresDist(">=2"));

        this.mocker.getComponentUnderTest().resolve(new ExtensionId("invalid", "1.0"));
    }

    @Test
    public void resolveDependency() throws Exception
    {
        putVersions("pkg", "1.0", "2.0", "2.5", "3.0rc1", "3.1");
        putPackage("pkg", "2.0", null, null, true);
        // No compatible distribution for the best matching version
        putPackage("pkg", "2.5", null, null, false);

        Extension extension = this.mocker.getComponentUnderTest().resolve(dependency("pkg", "[2,3)"));

        assertEquals(new ExtensionId("pkg", "2.0"), extension.getId());
        // Only the metadata of the selected wheel is needed
        assertEquals(1, getRequestCount("https://pypi.org/simple/pkg/"));
        assertEquals(1, getRequestCount(FILES_URL + "pkg-2.0-py3-none-any.whl.metadata"));
    }

    @Test
    public void resolveDependencyWhenPythonVersionOnlyInMetadataNotSupported() throws Exception
    {
        putRelease("pkg", "1.0", new Release());
        putRelease("pkg", "2.0", new Release().requiresPythonOnlyInMetadata(">=4"));

        assertEquals(new ExtensionId("pkg", "1.0"),
            this.mocker.getComponentUnderTest().resolve(dependency("pkg", "(,)")).getId());
    }

    @Test
    public void resolveDependencyIgnoresPreReleases() throws Exception
    {
        putPackage("pkg", "2.0", null, null, true);
        putPackage("pkg", "3.0rc1", null, null, true);

        assertEquals(new DefaultVersion("2.0"),
            this.mocker.getComponentUnderTest().resolve(dependency("pkg", "(,)")).getId().getVersion());
    }

    @Test
    public void resolveDependencyWithOnlyPreReleases() throws Exception
    {
        putPackage("pkg", "3.0rc1", null, null, true);

        assertEquals(new DefaultVersion("3.0rc1"),
            this.mocker.getComponentUnderTest().resolve(dependency("pkg", "(,)")).getId().getVersion());
    }

    @Test
    public void resolveDependencyWithSpecifiers() throws Exception
    {
        // PEP 440 orders development releases before pre-releases, and ignores invalid versions
        putVersions("pkg", "latest", "1.0");
        putPackage("pkg", "1.0a1", null, null, true);
        putPackage("pkg", "1.0.dev1", null, null, true);

        DefaultExtensionDependency dependency =
            new DefaultExtensionDependency("pkg", new DefaultVersionConstraint("(,1.0b1)"));
        dependency.putProperty(PythonPackages.DEPENDENCY_SPECIFIERS, "<1.0b1");

        assertEquals(new DefaultVersion("1.0a1"),
            this.mocker.getComponentUnderTest().resolve(dependency).getId().getVersion());
    }

    @Test
    public void resolveDependencyWithEpoch() throws Exception
    {
        putPackage("pkg", "2.0", null, null, true);
        putPackage("pkg", "1!0.5", null, null, true);

        assertEquals(new DefaultVersion("1!0.5"),
            this.mocker.getComponentUnderTest().resolve(dependency("pkg", "(,)")).getId().getVersion());
    }

    @Test
    public void resolveDependencyWithInvalidSpecifiers() throws Exception
    {
        putVersions("pkg", "2.0");
        putPackage("pkg", "1.0", null, null, true);

        DefaultExtensionDependency dependency =
            new DefaultExtensionDependency("pkg", new DefaultVersionConstraint("[1.0]"));
        dependency.putProperty(PythonPackages.DEPENDENCY_SPECIFIERS, "invalid");

        assertEquals(new DefaultVersion("1.0"),
            this.mocker.getComponentUnderTest().resolve(dependency).getId().getVersion());
    }

    @Test
    public void resolveDependencyWithRecommendedVersion() throws Exception
    {
        putVersions("pkg", "1.0");
        putPackage("pkg", "2.0", null, null, true);
        putPackage("pkg", "3.1", null, null, true);

        assertEquals(new DefaultVersion("2.0"),
            this.mocker.getComponentUnderTest().resolve(dependency("pkg", "2.0")).getId().getVersion());
    }

    @Test(expected = ExtensionNotFoundException.class)
    public void resolveDependencyWhenNoVersionMatches() throws Exception
    {
        putPackage("pkg", "1.0", null, null, true);

        this.mocker.getComponentUnderTest().resolve(dependency("pkg", "[2,)"));
    }

    @Test(expected = ExtensionNotFoundException.class)
    public void resolveDependencyWhenNoCompatibleDistribution() throws Exception
    {
        putPackage("pkg", "1.0", null, null, false);

        this.mocker.getComponentUnderTest().resolve(dependency("pkg", "(,)"));
    }

    @Test
    public void resolveDependencyWhenPackageDoesNotExist() throws Exception
    {
        try {
            this.mocker.getComponentUnderTest().resolve(dependency("doesnotexist", "(,)"));
        } catch (ExtensionNotFoundException e) {
            assertNull(e.getCause());
            return;
        }

        throw new AssertionError("An ExtensionNotFoundException was expected");
    }
}
