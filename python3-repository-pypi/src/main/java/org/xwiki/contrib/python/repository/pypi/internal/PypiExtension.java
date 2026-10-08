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

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.python.PythonPackages;
import org.xwiki.contrib.python.packaging.PythonMetadata;
import org.xwiki.extension.AbstractRemoteExtension;
import org.xwiki.extension.ExtensionFile;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.ExtensionLicense;
import org.xwiki.extension.ExtensionLicenseManager;
import org.xwiki.extension.repository.ExtensionRepository;

/**
 * A Python package (wheel) provided by PyPI.
 *
 * @version $Id$
 */
public class PypiExtension extends AbstractRemoteExtension implements Serializable
{
    private static final long serialVersionUID = 1L;

    private static final int SUMMARY_MAX_LENGTH = 200;

    /**
     * @param repository the repository providing the extension
     * @param id the identifier of the extension
     */
    public PypiExtension(ExtensionRepository repository, ExtensionId id)
    {
        super(repository, id, PythonPackages.TYPE_WHEEL);

        setRecommended(false);
    }

    /**
     * @param metadata the core metadata of the wheel
     * @param projectPage the page of the project on PyPI, used as website when the package does not indicate any
     * @param licenseManager used to find the known licenses
     */
    public void setMetadata(PythonMetadata metadata, String projectPage, ExtensionLicenseManager licenseManager)
    {
        setName(metadata.getName());
        setDescription(metadata.getDescription());
        setSummary(StringUtils.isNotEmpty(metadata.getSummary()) ? metadata.getSummary()
            : StringUtils.substring(metadata.getDescription(), 0, SUMMARY_MAX_LENGTH));
        // Recent packages declare their license as an SPDX expression (PEP 639) instead of the free form license
        addLicense(StringUtils.defaultIfEmpty(metadata.getLicenseExpression(), metadata.getLicense()),
            licenseManager);
        setWebsite(getWebsite(metadata, projectPage));
    }

    @Override
    public void setFile(ExtensionFile file)
    {
        super.setFile(file);
    }

    private static String getWebsite(PythonMetadata metadata, String projectPage)
    {
        // The Home-page field is deprecated in favor of the project URLs (and is empty for most recent packages)
        if (StringUtils.isNotEmpty(metadata.getHomePage())) {
            return metadata.getHomePage();
        }

        for (Map.Entry<String, String> entry : metadata.getProjectUrls().entrySet()) {
            if (StringUtils.equalsAnyIgnoreCase(entry.getKey(), "homepage", "home page", "home")) {
                return entry.getValue();
            }
        }

        return projectPage;
    }

    private void addLicense(String licenseName, ExtensionLicenseManager licenseManager)
    {
        if (StringUtils.isNotEmpty(licenseName)) {
            ExtensionLicense extensionLicense = licenseManager.getLicense(licenseName);
            if (extensionLicense != null) {
                addLicense(extensionLicense);
            } else {
                addLicense(new ExtensionLicense(licenseName, (List<String>) null));
            }
        }
    }
}
