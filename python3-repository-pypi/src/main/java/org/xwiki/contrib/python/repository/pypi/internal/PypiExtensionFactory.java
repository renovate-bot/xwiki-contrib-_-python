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
import java.util.Properties;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.contrib.python.PythonPackages;
import org.xwiki.contrib.python.packaging.PythonMetadata;
import org.xwiki.contrib.python.packaging.PythonPackaging;
import org.xwiki.contrib.python.packaging.PythonPackagingException;
import org.xwiki.contrib.python.packaging.PythonRequirement;
import org.xwiki.contrib.python.repository.pypi.internal.dto.json.PypiJsonProjectDto;
import org.xwiki.contrib.python.repository.pypi.internal.dto.simple.PypiSimpleFileDto;
import org.xwiki.contrib.python.repository.pypi.internal.dto.simple.PypiSimpleProjectDto;
import org.xwiki.contrib.python.repository.pypi.internal.utils.PypiHttpClient;
import org.xwiki.extension.DefaultExtensionDependency;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.ExtensionLicenseManager;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.repository.ExtensionRepository;
import org.xwiki.extension.version.internal.DefaultVersionConstraint;

/**
 * Create the extensions corresponding to the wheels of the Python packages.
 *
 * @version $Id$
 */
@Component(roles = PypiExtensionFactory.class)
@Singleton
public class PypiExtensionFactory implements Initializable
{
    /**
     * The resource holding the version of this module.
     */
    private static final String VERSION_RESOURCE = "/META-INF/python3-repository-pypi.properties";

    @Inject
    private ExtensionLicenseManager licenseManager;

    @Inject
    private PythonPackaging pythonPackaging;

    @Inject
    private PypiHttpClient httpClient;

    /**
     * The versions of the Python 3 API compatible with this repository: the ones starting with the version of this
     * module, since they are released together.
     */
    private String pythonAPIVersionConstraint;

    @Override
    public void initialize() throws InitializationException
    {
        Properties properties = new Properties();
        try (InputStream stream = getClass().getResourceAsStream(VERSION_RESOURCE)) {
            properties.load(stream);
        } catch (IOException e) {
            throw new InitializationException("Failed to read the version of the PyPI repository", e);
        }

        this.pythonAPIVersionConstraint = "[" + properties.getProperty("version") + ",)";
    }

    /**
     * @return the versions of the Python 3 API the Python packages depend on
     */
    public String getPythonAPIVersionConstraint()
    {
        return this.pythonAPIVersionConstraint;
    }

    private static String getProjectPage(String name)
    {
        return PypiParameters.PROJECT_PAGE.replace(PypiParameters.PACKAGE_NAME_VARIABLE, name);
    }

    /**
     * Create the extension of a package found by a search, from the description of its latest release. It's enough to
     * list the package: the extension is fully resolved (file and dependencies) when it's installed.
     *
     * @param repository the repository providing the extension
     * @param project the JSON API page of the package
     * @return the extension
     */
    public PypiExtension createSearchExtension(ExtensionRepository repository, PypiJsonProjectDto project)
    {
        PythonMetadata metadata = project.getInfo().toMetadata();
        String name = PythonPackages.normalizeName(metadata.getName());

        PypiExtension extension = new PypiExtension(repository, new ExtensionId(name, metadata.getVersion()));
        extension.setMetadata(metadata, getProjectPage(name), this.licenseManager);
        extension.addRepository(repository.getDescriptor());

        return extension;
    }

    /**
     * Create the extension of a package which cannot be installed (no wheel compatible with the Python runtime) so
     * that it can still be listed (the extension index will indicate it's not compatible).
     *
     * @param repository the repository providing the extension
     * @param project the project page of the package
     * @param version the version of the package
     * @return the extension
     */
    public PypiExtension createIncompatibleExtension(ExtensionRepository repository, PypiSimpleProjectDto project,
        String version)
    {
        String name = PythonPackages.normalizeName(project.getName());

        PypiExtension extension = new PypiExtension(repository, new ExtensionId(name, version));
        extension.setName(project.getName());
        extension.setWebsite(getProjectPage(name));
        extension.addRepository(repository.getDescriptor());

        return extension;
    }

    /**
     * @param repository the repository providing the extension
     * @param project the project page of the package
     * @param version the version of the package
     * @param file the selected wheel
     * @param metadata the core metadata of the wheel
     * @return the extension
     * @throws ResolveException when failing to create the extension
     */
    public PypiExtension createExtension(ExtensionRepository repository, PypiSimpleProjectDto project, String version,
        PypiSimpleFileDto file, PythonMetadata metadata) throws ResolveException
    {
        String name = PythonPackages.normalizeName(project.getName());

        PypiExtension extension = new PypiExtension(repository, new ExtensionId(name, version));
        extension.setMetadata(metadata, getProjectPage(name), this.licenseManager);
        extension.addRepository(repository.getDescriptor());

        try {
            extension.setFile(new PypiExtensionFile(new URI(file.getUrl()),
                file.getSize() != null ? file.getSize() : -1, file.getSha256(), this.httpClient));
        } catch (URISyntaxException e) {
            throw new ResolveException("Invalid URL [" + file.getUrl() + "] for package [" + name + "]", e);
        }

        // The Python packages are useless (and cannot even be initialized) without the extension handling them
        extension.addDependency(new DefaultExtensionDependency(PypiParameters.PYTHON_API_ID,
            new DefaultVersionConstraint(this.pythonAPIVersionConstraint)));
        try {
            for (PythonRequirement requirement : this.pythonPackaging.getRequirements(metadata.getRequiresDist())) {
                extension.addDependency(requirement.toExtensionDependency());
            }
        } catch (PythonPackagingException e) {
            throw new ResolveException(String.format("Invalid dependencies in package [%s]", name), e);
        }

        return extension;
    }
}
