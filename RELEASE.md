# Release Process

This document describes how to create a new release of the Java Web Server.

## Overview

Releases are managed using **semantic versioning** (SemVer) and **Git tags**. The release process is automated via GitHub Actions, which builds the JAR artifact and creates a GitHub Release with an auto-generated changelog.

## Versioning Strategy

### Semantic Versioning (SemVer)

Version format: `MAJOR.MINOR.PATCH[-prerelease]`

- **MAJOR**: Increment for incompatible API changes
- **MINOR**: Increment for backward-compatible new features
- **PATCH**: Increment for backward-compatible bug fixes
- **Pre-release** (optional): `-alpha`, `-beta`, `-rc1`, etc.

Examples:

- `1.0.0` - First stable release
- `1.1.0` - New feature added
- `1.1.1` - Bug fix
- `2.0.0-beta.1` - Major version pre-release

### Version Source of Truth

- **Default version**: `gradle.properties` contains `version=0.1.0-SNAPSHOT`
- **Release version**: Overridden via `-Pversion=X.Y.Z` during release builds
- **Git tags**: Version tags are created in the format `vX.Y.Z` (e.g., `v1.0.0`)

## Release Checklist

Before creating a release, ensure:

1. ✅ All changes for the release are merged to `main`
2. ✅ All tests pass: `./gradlew test`
3. ✅ Build succeeds: `./gradlew build`
4. ✅ Version number follows SemVer convention
5. ✅ `gradle.properties` still contains `-SNAPSHOT` suffix (never commit without it)
6. ✅ No uncommitted changes in your working directory

## Creating a Release

### Option 1: Manual Trigger (Recommended)

Use the GitHub Actions workflow dispatch to create a release:

1. Navigate to **Actions** → **Release** workflow
2. Click **Run workflow**
3. Enter the release version (e.g., `1.0.0`) **without** the `v` prefix
4. Click **Run workflow**

The workflow will:

- Validate the version format (SemVer)
- Check that the tag doesn't already exist
- Run all tests
- Build the release JAR with the specified version
- Create and push the Git tag (`vX.Y.Z`)
- Generate a changelog from git commits
- Create a GitHub Release with the JAR artifact attached

### Option 2: Push Tag Manually

Alternatively, create and push a tag manually:

```bash
# Ensure you're on main and up to date
git checkout main
git pull

# Create an annotated tag
git tag -a v1.0.0 -m "Release 1.0.0: Description of changes"

# Push the tag to trigger the release workflow
git push origin v1.0.0
```

The GitHub Actions workflow will automatically detect the tag and create the release.

## Testing Release Builds Locally

To test a release build locally before publishing:

```bash
# Build with a specific version
./gradlew clean build -Pversion=1.0.0-rc1

# Verify the JAR was created with the correct name
ls -lh app/build/libs/java-web-server-1.0.0-rc1.jar

# Test the JAR
java -jar app/build/libs/java-web-server-1.0.0-rc1.jar
```

## Release Artifacts

### JAR Naming Convention

Release JARs follow this naming pattern:

- `java-web-server-{version}.jar`

Examples:

- `java-web-server-1.0.0.jar`
- `java-web-server-1.2.3.jar`
- `java-web-server-2.0.0-beta.1.jar`

### Manifest Attributes

The JAR manifest includes:

- `Main-Class`: Entry point for the application
- `Implementation-Version`: The release version

Verify manifest contents:

```bash
jar xf app/build/libs/java-web-server-1.0.0.jar META-INF/MANIFEST.MF
cat META-INF/MANIFEST.MF
```

## GitHub Actions Workflows

### `build.yml` - Continuous Integration

**Triggers**: Push to `main` or Pull Requests

**Actions**:

- Checkout code
- Set up JDK 21
- Build and test with Gradle
- Upload snapshot JAR artifact (main branch only)

### `release.yml` - Release Automation

**Triggers**:

- Manual workflow dispatch with `release_version` input
- Push of tags matching `v*.*.*`

**Actions**:

1. Validate SemVer format
2. Check tag doesn't exist (workflow_dispatch only)
3. Run tests (`./gradlew clean test`)
4. Build release JAR (`./gradlew :app:build -x test -Pversion=X.Y.Z`)
5. Create and push Git tag (workflow_dispatch only)
6. Generate changelog from git shortlog
7. Create GitHub Release with JAR artifact

**Fail-Fast Validation**:

- Version must match regex: `^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?$`
- Tag must not already exist
- Tests must pass

## Changelog Generation

Changelogs are automatically generated using `git log` between the previous tag and the new tag:

```bash
# Example changelog format
## Changes since v0.9.0

- Add multi-threaded request handling (a1b2c3d)
- Fix connection leak in keep-alive mode (d4e5f6g)
- Update dependencies to latest versions (h7i8j9k)
```

## Post-Release Tasks

After a successful release:

1. ✅ Verify the GitHub Release was created: `https://github.com/{owner}/{repo}/releases`
2. ✅ Download and test the release JAR artifact
3. ✅ Update `gradle.properties` with the next snapshot version if needed:

   ```properties
   version=1.1.0-SNAPSHOT
   ```

4. ✅ Communicate the release to users/stakeholders

## Troubleshooting

### Version Validation Failed

**Error**: `Version 'X.Y.Z' does not match SemVer pattern`

**Solution**: Ensure version follows format `MAJOR.MINOR.PATCH` (e.g., `1.0.0`, not `1.0` or `v1.0.0`)

### Tag Already Exists

**Error**: `Tag vX.Y.Z already exists`

**Solution**: Choose a different version number or delete the existing tag if it was created in error:

```bash
git tag -d v1.0.0           # Delete locally
git push origin :refs/tags/v1.0.0  # Delete remotely
```

### Tests Failed

**Error**: Build fails during test execution

**Solution**: Fix failing tests before attempting to release. Run locally:

```bash
./gradlew clean test
```

### JAR Not Found

**Error**: `Expected JAR file not found: app/build/libs/java-web-server-X.Y.Z.jar`

**Solution**: Check that the version override is working correctly. Verify build.gradle.kts has proper JAR naming configuration.

## Security & Best Practices

### Protected Branches

Configure branch protection rules for `main`:

- Require pull request reviews
- Require status checks to pass
- Restrict who can push tags

### Secrets Management

Release credentials (if publishing to artifact repositories in the future) should be stored as GitHub Actions secrets:

- Navigate to **Settings** → **Secrets and variables** → **Actions**
- Add required secrets (e.g., `MAVEN_TOKEN`, `DOCKER_PASSWORD`)

### Version Immutability

- Never delete or overwrite published release tags
- If a release has critical issues, publish a new patch version
- Mark problematic releases as pre-release or draft in GitHub

## Future Enhancements

Potential improvements to the release process:

- [ ] Automated changelog generation from PR titles/labels
- [ ] Docker image building and pushing to registry
- [ ] Publishing to Maven Central or other artifact repositories
- [ ] Automated dependency updates via Dependabot
- [ ] Release notes template customization
- [ ] Signed releases with GPG keys

## References

- [Semantic Versioning 2.0.0](https://semver.org/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Gradle Version Management](https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties)
- [Creating GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)
