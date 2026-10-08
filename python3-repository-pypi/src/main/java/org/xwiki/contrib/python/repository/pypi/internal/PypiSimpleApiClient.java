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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.python.PythonPackages;
import org.xwiki.contrib.python.packaging.PythonMetadata;
import org.xwiki.contrib.python.packaging.PythonPackaging;
import org.xwiki.contrib.python.packaging.PythonPackagingException;
import org.xwiki.contrib.python.repository.pypi.internal.dto.simple.PypiSimpleFileDto;
import org.xwiki.contrib.python.repository.pypi.internal.dto.simple.PypiSimpleProjectDto;
import org.xwiki.contrib.python.repository.pypi.internal.utils.PypiHttpClient;
import org.xwiki.extension.ExtensionNotFoundException;
import org.xwiki.extension.ResolveException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Get the information published by PyPI through the standard Simple API (PEP 503, PEP 691, PEP 700): the project
 * pages listing the distribution files of a package, and the core metadata of the wheels (PEP 658, PEP 714).
 *
 * @version $Id$
 */
@Component(roles = PypiSimpleApiClient.class)
@Singleton
public class PypiSimpleApiClient
{
    private static final String DOES_NOT_EXIST = "] does not exist";

    /**
     * The metadata file of a wheel (in the top level {@code .dist-info} directory).
     */
    private static final Pattern WHEEL_METADATA = Pattern.compile("^[^/]+\\.dist-info/METADATA$");

    /**
     * The duration during which a project page is reused, same as the cache duration indicated by PyPI.
     */
    private static final long PROJECT_CACHE_DURATION = 10L * 60L * 1000L;

    private static final int PROJECT_CACHE_SIZE = 200;

    private static final int METADATA_CACHE_SIZE = 1000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject
    private PythonPackaging pythonPackaging;

    @Inject
    private PypiHttpClient httpClient;

    private final Map<String, CachedProject> projectCache =
        Collections.synchronizedMap(new LruMap<>(PROJECT_CACHE_SIZE));

    /**
     * The metadata of the wheels, indexed by URL (the files published on PyPI never change).
     */
    private final Map<String, PythonMetadata> metadataCache =
        Collections.synchronizedMap(new LruMap<>(METADATA_CACHE_SIZE));

    private record CachedProject(PypiSimpleProjectDto project, long date)
    {
    }

    /**
     * A map keeping the most recently used entries.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     */
    private static final class LruMap<K, V> extends LinkedHashMap<K, V>
    {
        private static final long serialVersionUID = 1L;

        private final int maxSize;

        LruMap(int maxSize)
        {
            super(16, 0.75F, true);

            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest)
        {
            return size() > this.maxSize;
        }
    }

    /**
     * @param packageName the name of the package
     * @return the JSON form of the Simple API page of the package (PEP 691), reused for a few minutes
     * @throws ResolveException when failing to get the project page
     */
    public PypiSimpleProjectDto getProject(String packageName) throws ResolveException
    {
        // Use the normalized name to avoid a redirect
        String name = PythonPackages.normalizeName(packageName);

        CachedProject cached = this.projectCache.get(name);
        if (cached != null && System.currentTimeMillis() - cached.date() < PROJECT_CACHE_DURATION) {
            return cached.project();
        }

        PypiSimpleProjectDto project;
        try (InputStream stream = this.httpClient.openStream(
            new URI(PypiParameters.PACKAGE_SIMPLE_API.replace(PypiParameters.PACKAGE_NAME_VARIABLE, name)),
            PypiParameters.SIMPLE_API_JSON_MEDIA_TYPE)) {
            if (stream == null) {
                throw new ExtensionNotFoundException("Cannot find package [" + packageName + "] on PyPI");
            }

            project = OBJECT_MAPPER.readValue(stream, PypiSimpleProjectDto.class);
        } catch (IOException | URISyntaxException e) {
            throw new ResolveException("Failed to get the project page of package [" + packageName + "]", e);
        }

        this.projectCache.put(name, new CachedProject(project, System.currentTimeMillis()));

        return project;
    }
    /**
     * @param file the wheel file
     * @return the core metadata of the wheel
     * @throws ResolveException when failing to get the metadata
     */
    public PythonMetadata getMetadata(PypiSimpleFileDto file) throws ResolveException
    {
        PythonMetadata metadata = this.metadataCache.get(file.getUrl());

        if (metadata == null) {
            String content;
            try {
                content = file.hasCoreMetadata() ? downloadCoreMetadata(file) : extractCoreMetadata(file);
            } catch (IOException | URISyntaxException e) {
                throw new ResolveException("Failed to get the metadata of [" + file.getFilename() + "]", e);
            }

            try {
                metadata = this.pythonPackaging.parseMetadata(content);
            } catch (PythonPackagingException e) {
                throw new ResolveException("Failed to parse the metadata of [" + file.getFilename() + "]", e);
            }

            this.metadataCache.put(file.getUrl(), metadata);
        }

        return metadata;
    }
    private String downloadCoreMetadata(PypiSimpleFileDto file) throws IOException, URISyntaxException
    {
        byte[] content;
        try (InputStream stream = this.httpClient.openStream(new URI(file.getCoreMetadataUrl()), null)) {
            if (stream == null) {
                throw new IOException("The metadata file [" + file.getCoreMetadataUrl() + DOES_NOT_EXIST);
            }

            content = stream.readAllBytes();
        }

        String expectedHash = file.getCoreMetadataSha256();
        if (expectedHash != null && !expectedHash.equalsIgnoreCase(sha256(content))) {
            throw new IOException("Unexpected SHA-256 hash for the metadata file [" + file.getCoreMetadataUrl() + "]");
        }

        return new String(content, StandardCharsets.UTF_8);
    }
    private static String sha256(byte[] content) throws IOException
    {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is not supported", e);
        }
    }
    /**
     * Extract the metadata from the wheel itself, for the files whose metadata is not provided separately.
     */
    private String extractCoreMetadata(PypiSimpleFileDto file) throws IOException, URISyntaxException
    {
        try (InputStream stream = this.httpClient.openStream(new URI(file.getUrl()), null)) {
            if (stream == null) {
                throw new IOException("The file [" + file.getUrl() + DOES_NOT_EXIST);
            }

            ZipInputStream zip = new ZipInputStream(stream);
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (WHEEL_METADATA.matcher(entry.getName()).matches()) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }

        throw new IOException("No metadata file in the wheel [" + file.getUrl() + "]");
    }
}
