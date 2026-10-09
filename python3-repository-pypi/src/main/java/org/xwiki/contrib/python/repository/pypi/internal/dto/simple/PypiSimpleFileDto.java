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
package org.xwiki.contrib.python.repository.pypi.internal.dto.simple;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A distribution file listed in the JSON form of a Simple API project page (PEP 691, with the additions of PEP 700
 * and PEP 714).
 *
 * @version $Id$
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PypiSimpleFileDto
{
    private static final String SHA256 = "sha256";

    private String filename;

    private String url;

    private Map<String, String> hashes;

    @JsonProperty("requires-python")
    private String requiresPython;

    private Long size;

    /**
     * Either a boolean or the reason why the file was yanked (PEP 592).
     */
    private Object yanked;

    /**
     * Either a boolean or the hashes of the metadata file (PEP 658, PEP 714).
     */
    @JsonProperty("core-metadata")
    private Object coreMetadata;

    /**
     * The name of the core metadata field before PEP 714.
     */
    @JsonProperty("data-dist-info-metadata")
    private Object dataDistInfoMetadata;

    /**
     * @return the name of the file
     */
    public String getFilename()
    {
        return this.filename;
    }

    /**
     * @param filename the name of the file
     */
    public void setFilename(String filename)
    {
        this.filename = filename;
    }

    /**
     * @return the URL to download the file
     */
    public String getUrl()
    {
        return this.url;
    }

    /**
     * @param url the URL to download the file
     */
    public void setUrl(String url)
    {
        this.url = url;
    }

    /**
     * @return the hashes of the file, indexed by algorithm
     */
    public Map<String, String> getHashes()
    {
        return this.hashes;
    }

    /**
     * @param hashes the hashes of the file, indexed by algorithm
     */
    public void setHashes(Map<String, String> hashes)
    {
        this.hashes = hashes;
    }

    /**
     * @return the SHA-256 hash of the file, {@code null} if unknown
     */
    public String getSha256()
    {
        return this.hashes != null ? this.hashes.get(SHA256) : null;
    }

    /**
     * @return the Python versions supported by the distribution, {@code null} if unknown
     */
    public String getRequiresPython()
    {
        return this.requiresPython;
    }

    /**
     * @param requiresPython the Python versions supported by the distribution
     */
    public void setRequiresPython(String requiresPython)
    {
        this.requiresPython = requiresPython;
    }

    /**
     * @return the size of the file, {@code null} if unknown
     */
    public Long getSize()
    {
        return this.size;
    }

    /**
     * @param size the size of the file
     */
    public void setSize(Long size)
    {
        this.size = size;
    }

    /**
     * @param yanked either a boolean or the reason why the file was yanked
     */
    public void setYanked(Object yanked)
    {
        this.yanked = yanked;
    }

    /**
     * @return true if the file was yanked (it should only be used when explicitly requested)
     */
    public boolean isYanked()
    {
        return this.yanked != null && !Boolean.FALSE.equals(this.yanked);
    }

    /**
     * @param coreMetadata either a boolean or the hashes of the metadata file
     */
    public void setCoreMetadata(Object coreMetadata)
    {
        this.coreMetadata = coreMetadata;
    }

    /**
     * @param dataDistInfoMetadata either a boolean or the hashes of the metadata file
     */
    public void setDataDistInfoMetadata(Object dataDistInfoMetadata)
    {
        this.dataDistInfoMetadata = dataDistInfoMetadata;
    }

    private Object getCoreMetadataValue()
    {
        return this.coreMetadata != null ? this.coreMetadata : this.dataDistInfoMetadata;
    }

    /**
     * @return true if the core metadata of the distribution can be downloaded separately (from the URL of the file
     *         followed by {@code .metadata})
     */
    public boolean hasCoreMetadata()
    {
        Object value = getCoreMetadataValue();

        return value != null && !Boolean.FALSE.equals(value);
    }

    /**
     * @return the SHA-256 hash of the core metadata file, {@code null} if unknown
     */
    public String getCoreMetadataSha256()
    {
        Object value = getCoreMetadataValue();

        return value instanceof Map<?, ?> metadataHashes && metadataHashes.get(SHA256) instanceof String hash ? hash
            : null;
    }

    /**
     * @return the URL of the core metadata file
     */
    public String getCoreMetadataUrl()
    {
        return this.url + ".metadata";
    }
}
