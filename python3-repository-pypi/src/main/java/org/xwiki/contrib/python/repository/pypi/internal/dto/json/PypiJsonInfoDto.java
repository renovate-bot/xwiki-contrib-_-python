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
package org.xwiki.contrib.python.repository.pypi.internal.dto.json;

import java.util.Map;

import org.xwiki.contrib.python.packaging.PythonMetadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The metadata of the latest release, as provided by the JSON API page of a project.
 *
 * @version $Id$
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PypiJsonInfoDto
{
    private String name;

    private String version;

    private String summary;

    private String description;

    private String license;

    @JsonProperty("license_expression")
    private String licenseExpression;

    @JsonProperty("home_page")
    private String homePage;

    @JsonProperty("project_urls")
    private Map<String, String> projectUrls;

    @JsonProperty("requires_python")
    private String requiresPython;

    /**
     * @return the name of the package
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * @param name the name of the package
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * @return the latest version of the package
     */
    public String getVersion()
    {
        return this.version;
    }

    /**
     * @param version the latest version of the package
     */
    public void setVersion(String version)
    {
        this.version = version;
    }

    /**
     * @return the short description of the package
     */
    public String getSummary()
    {
        return this.summary;
    }

    /**
     * @param summary the short description of the package
     */
    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    /**
     * @return the long description of the package
     */
    public String getDescription()
    {
        return this.description;
    }

    /**
     * @param description the long description of the package
     */
    public void setDescription(String description)
    {
        this.description = description;
    }

    /**
     * @return the free form license
     */
    public String getLicense()
    {
        return this.license;
    }

    /**
     * @param license the free form license
     */
    public void setLicense(String license)
    {
        this.license = license;
    }

    /**
     * @return the SPDX license expression (PEP 639)
     */
    public String getLicenseExpression()
    {
        return this.licenseExpression;
    }

    /**
     * @param licenseExpression the SPDX license expression (PEP 639)
     */
    public void setLicenseExpression(String licenseExpression)
    {
        this.licenseExpression = licenseExpression;
    }

    /**
     * @return the home page of the package
     */
    public String getHomePage()
    {
        return this.homePage;
    }

    /**
     * @param homePage the home page of the package
     */
    public void setHomePage(String homePage)
    {
        this.homePage = homePage;
    }

    /**
     * @return the URLs of the project, indexed by label
     */
    public Map<String, String> getProjectUrls()
    {
        return this.projectUrls;
    }

    /**
     * @param projectUrls the URLs of the project, indexed by label
     */
    public void setProjectUrls(Map<String, String> projectUrls)
    {
        this.projectUrls = projectUrls;
    }

    /**
     * @return the Python versions supported by the latest release
     */
    public String getRequiresPython()
    {
        return this.requiresPython;
    }

    /**
     * @param requiresPython the Python versions supported by the latest release
     */
    public void setRequiresPython(String requiresPython)
    {
        this.requiresPython = requiresPython;
    }

    /**
     * @return the metadata of the latest release, in the same form as the core metadata of a distribution
     */
    public PythonMetadata toMetadata()
    {
        PythonMetadata metadata = new PythonMetadata();
        metadata.setName(this.name);
        metadata.setVersion(this.version);
        metadata.setSummary(this.summary);
        metadata.setDescription(this.description);
        metadata.setLicense(this.license);
        metadata.setLicenseExpression(this.licenseExpression);
        metadata.setHomePage(this.homePage);
        if (this.projectUrls != null) {
            metadata.setProjectUrls(this.projectUrls);
        }
        metadata.setRequiresPython(this.requiresPython);

        return metadata;
    }
}
