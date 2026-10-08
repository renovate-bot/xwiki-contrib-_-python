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
package org.xwiki.contrib.python.packaging;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The core metadata of a Python distribution (the {@code METADATA} file of a wheel), as specified by the Python
 * packaging core metadata specification.
 *
 * @version $Id$
 */
public class PythonMetadata
{
    private String name;

    private String version;

    private String summary;

    private String description;

    private String license;

    private String licenseExpression;

    private String homePage;

    private Map<String, String> projectUrls = Collections.emptyMap();

    private String requiresPython;

    private List<String> requiresDist = Collections.emptyList();

    /**
     * @return the name of the distribution
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * @param name the name of the distribution
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * @return the version of the distribution
     */
    public String getVersion()
    {
        return this.version;
    }

    /**
     * @param version the version of the distribution
     */
    public void setVersion(String version)
    {
        this.version = version;
    }

    /**
     * @return the one-line summary of the distribution
     */
    public String getSummary()
    {
        return this.summary;
    }

    /**
     * @param summary the one-line summary of the distribution
     */
    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    /**
     * @return the long description of the distribution
     */
    public String getDescription()
    {
        return this.description;
    }

    /**
     * @param description the long description of the distribution
     */
    public void setDescription(String description)
    {
        this.description = description;
    }

    /**
     * @return the free form license of the distribution
     */
    public String getLicense()
    {
        return this.license;
    }

    /**
     * @param license the free form license of the distribution
     */
    public void setLicense(String license)
    {
        this.license = license;
    }

    /**
     * @return the SPDX license expression of the distribution
     */
    public String getLicenseExpression()
    {
        return this.licenseExpression;
    }

    /**
     * @param licenseExpression the SPDX license expression of the distribution
     */
    public void setLicenseExpression(String licenseExpression)
    {
        this.licenseExpression = licenseExpression;
    }

    /**
     * @return the home page of the distribution (deprecated in favor of the project URLs)
     */
    public String getHomePage()
    {
        return this.homePage;
    }

    /**
     * @param homePage the home page of the distribution
     */
    public void setHomePage(String homePage)
    {
        this.homePage = homePage;
    }

    /**
     * @return the URLs associated with the project, indexed by label
     */
    public Map<String, String> getProjectUrls()
    {
        return this.projectUrls;
    }

    /**
     * @param projectUrls the URLs associated with the project, indexed by label
     */
    public void setProjectUrls(Map<String, String> projectUrls)
    {
        this.projectUrls = projectUrls;
    }

    /**
     * @return the Python versions supported by the distribution
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
     * @return the dependencies of the distribution
     */
    public List<String> getRequiresDist()
    {
        return this.requiresDist;
    }

    /**
     * @param requiresDist the dependencies of the distribution
     */
    public void setRequiresDist(List<String> requiresDist)
    {
        this.requiresDist = requiresDist;
    }
}
