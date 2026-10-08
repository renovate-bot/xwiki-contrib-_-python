# See the NOTICE file distributed with this work for additional
# information regarding copyright ownership.
#
# This is free software; you can redistribute it and/or modify it
# under the terms of the GNU Lesser General Public License as
# published by the Free Software Foundation; either version 2.1 of
# the License, or (at your option) any later version.
#
# This software is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this software; if not, write to the Free
# Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
# 02110-1301 USA, or see the FSF site: http://www.fsf.org.

"""Python packaging helpers backing the PythonPackaging Java component.

The standard behaviors (versions ordering, version specifiers, environment markers, wheel tags) are provided by the
packaging library, the reference implementation used by pip.
"""

from packaging import markers, metadata, requirements, specifiers, tags, utils, version

_ENVIRONMENT = markers.default_environment()

# Only the wheels without native code can be imported from the Python paths (the native code cannot be loaded from a
# zip), so only the platform independent tags are supported. The tags are listed from the most to the least specific.
_TAG_PRIORITIES = {}
for _tag in tags.compatible_tags(interpreter=tags.interpreter_name() + tags.interpreter_version(), platforms=["any"]):
    _TAG_PRIORITIES.setdefault(_tag, len(_TAG_PRIORITIES))

_SUPPORTED_TAGS = frozenset(_TAG_PRIORITIES)


def get_python_version():
    return _ENVIRONMENT["python_full_version"]


def is_python_supported(requires_python):
    return specifiers.SpecifierSet(requires_python).contains(_ENVIRONMENT["python_full_version"], prereleases=True)


def is_compatible_wheel(filename):
    try:
        wheel_tags = utils.parse_wheel_filename(filename)[3]
    except (utils.InvalidWheelFilename, version.InvalidVersion):
        return False

    return not _SUPPORTED_TAGS.isdisjoint(wheel_tags)


def select_wheels(versions, filenames, requires_pythons):
    """Return [version, wheel file name] for each version having a compatible wheel, in the order of the versions.

    When several wheels of a version are compatible, the one with the most specific tag is selected.
    """
    best = {}
    for filename, requires_python in zip(filenames, requires_pythons):
        try:
            wheel_version, wheel_tags = utils.parse_wheel_filename(filename)[1::2]
        except (utils.InvalidWheelFilename, version.InvalidVersion):
            continue

        priorities = [_TAG_PRIORITIES[tag] for tag in wheel_tags if tag in _TAG_PRIORITIES]
        if not priorities or (requires_python and not _is_python_supported_lenient(requires_python)):
            continue

        priority = min(priorities)
        if wheel_version not in best or priority < best[wheel_version][0]:
            best[wheel_version] = (priority, filename)

    result = []
    for value in versions:
        try:
            parsed = version.Version(value)
        except version.InvalidVersion:
            continue
        if parsed in best:
            result.append([value, best[parsed][1]])

    return result


def _is_python_supported_lenient(requires_python):
    try:
        return is_python_supported(requires_python)
    except specifiers.InvalidSpecifier:
        # Don't exclude a distribution because of an invalid metadata
        return True


def parse_metadata(text):
    """Parse the core metadata of a distribution (the METADATA file of a wheel)."""
    raw = metadata.parse_email(text)[0]

    return {
        "name": raw.get("name"),
        "version": raw.get("version"),
        "summary": raw.get("summary"),
        "description": raw.get("description"),
        "license": raw.get("license"),
        "license_expression": raw.get("license_expression"),
        "home_page": raw.get("home_page"),
        "project_urls": raw.get("project_urls") or {},
        "requires_python": raw.get("requires_python"),
        "requires_dist": raw.get("requires_dist") or [],
    }


def get_requirements(requires_dist):
    """Return [name, specifiers, version range] for each requirement applying to the current environment."""
    result = []
    # No extra is ever requested when installing a package
    environment = dict(_ENVIRONMENT, extra="")
    for value in requires_dist:
        requirement = requirements.Requirement(value)
        if requirement.marker is None or requirement.marker.evaluate(environment):
            result.append([requirement.name, str(requirement.specifier), _to_range(requirement.specifier)])

    return result


def filter_versions(versions, specifier, prereleases):
    """Return the valid versions matching the specifier, from the best to the worst."""
    valid_versions = []
    for value in versions:
        try:
            version.Version(value)
            valid_versions.append(value)
        except version.InvalidVersion:
            # Ignore the versions which are not valid PEP 440 versions (like pip does)
            pass

    matching = specifiers.SpecifierSet(specifier).filter(valid_versions, prereleases=prereleases)

    return sorted(matching, key=version.Version, reverse=True)


def _next_prefix(release, epoch):
    """Return the first version following all the versions starting with the release prefix."""
    prefix = ".".join(str(segment) for segment in release[:-1] + (release[-1] + 1,))
    return "%d!%s" % (epoch, prefix) if epoch else prefix


def _to_range(specifier_set):
    """Convert the specifiers to an XWiki version range (exclusions cannot be expressed so they are ignored)."""
    bounds = {"lower": None, "upper": None}

    def restrict(bound, raw, inclusive):
        current = bounds[bound]
        candidate = version.Version(raw)
        if current is not None:
            comparison = (candidate > current[0]) - (candidate < current[0])
            if bound == "upper":
                comparison = -comparison
            if comparison < 0 or (comparison == 0 and (inclusive or not current[2])):
                return
        bounds[bound] = (candidate, raw, inclusive)

    for spec in specifier_set:
        operator, raw = spec.operator, spec.version
        if operator in (">=", ">"):
            restrict("lower", raw, operator == ">=")
        elif operator in ("<=", "<"):
            restrict("upper", raw, operator == "<=")
        elif operator == "~=":
            parsed = version.Version(raw)
            restrict("lower", raw, True)
            restrict("upper", _next_prefix(parsed.release[:-1], parsed.epoch), False)
        elif operator == "==" and raw.endswith(".*"):
            parsed = version.Version(raw[:-2])
            restrict("lower", raw[:-2], True)
            restrict("upper", _next_prefix(parsed.release, parsed.epoch), False)
        elif operator in ("==", "==="):
            try:
                version.Version(raw)
            except version.InvalidVersion:
                # An arbitrary equality can target a version which is not a valid PEP 440 version
                continue
            restrict("lower", raw, True)
            restrict("upper", raw, True)

    lower, upper = bounds["lower"], bounds["upper"]
    if lower is not None and upper is not None:
        if lower[0] > upper[0] or (lower[0] == upper[0] and not (lower[2] and upper[2])):
            # The specifiers contradict each other: let the resolution pick the best available version
            return "(,)"
        if lower[0] == upper[0]:
            return "[%s]" % lower[1]

    return "%s%s,%s%s" % ("[" if lower is not None and lower[2] else "(", lower[1] if lower is not None else "",
        upper[1] if upper is not None else "", "]" if upper is not None and upper[2] else ")")
