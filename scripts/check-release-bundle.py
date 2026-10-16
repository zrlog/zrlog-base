#!/usr/bin/env python3
"""Build the real release reactor in isolation and inspect its unuploaded bundle."""

import argparse
import io
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
VERSION = "4.0.0-bundle-check"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--maven-repo", type=Path, help="Optional Maven dependency cache")
    args = parser.parse_args()

    model = ET.parse(ROOT / "pom.xml").getroot()
    publisher = next(plugin for plugin in model.findall("m:build/m:plugins/m:plugin", NS)
                     if plugin.findtext("m:artifactId", namespaces=NS) == "central-publishing-maven-plugin")
    # skipUpload was added in 0.12.0. Fail before invoking Maven if it is unavailable.
    if publisher.findtext("m:version", namespaces=NS) != "0.12.0":
        raise AssertionError("Bundle check requires Central Publishing 0.12.0 and its skipUpload support")

    artifacts = {model.findtext("m:artifactId", namespaces=NS): "pom"}
    for module in model.findall("m:modules/m:module", NS):
        child = ET.parse(ROOT / module.text / "pom.xml").getroot()
        artifacts[child.findtext("m:artifactId", namespaces=NS)] = "jar"

    with tempfile.TemporaryDirectory(prefix="zrlog-release-bundle-") as temporary:
        directory = Path(temporary)
        project = directory / "project"
        project.mkdir()
        tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
        for filename in filter(None, tracked):
            source = ROOT / filename
            if source.is_file():
                destination = project / filename
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, destination)

        # No publishing credentials are read from the user's settings.
        settings = directory / "settings.xml"
        settings.write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"/>', encoding="utf-8")
        command = [str(project / "mvnw"), "-B", "-ntp", "-s", str(settings)]
        if args.maven_repo:
            command.append(f"-Dmaven.repo.local={args.maven_repo.resolve()}")

        subprocess.run(command + ["org.codehaus.mojo:versions-maven-plugin:2.21.0:set",
                                 f"-DnewVersion={VERSION}", "-DprocessAllModules=true",
                                 "-DgenerateBackupPoms=false"], cwd=project, check=True)
        # Exercise deploy's Javadoc, staging and bundling without signing or uploading.
        # Tests have already run in the preceding CI step.
        subprocess.run(command + ["deploy", "-DskipTests", "-Dgpg.skip=true",
                                 "-DskipUpload=true", "-DignorePublishedComponents=false"],
                       cwd=project, check=True)

        bundle = project / "target/central-publishing/central-bundle.zip"
        with zipfile.ZipFile(bundle) as archive:
            files = {name for name in archive.namelist() if not name.endswith("/")}
            components = set()
            for name in files:
                path = PurePosixPath(name)
                if len(path.parts) != 5 or path.parts[:2] != ("com", "hibegin"):
                    raise AssertionError(f"Unexpected bundle path: {name}")
                artifact, version, filename = path.parts[2:]
                if artifact not in artifacts or version != VERSION:
                    raise AssertionError(f"Unexpected component: {name}")
                if not filename.startswith(f"{artifact}-{VERSION}.") and not filename.startswith(f"{artifact}-{VERSION}-"):
                    raise AssertionError(f"Local repository metadata leaked into bundle: {name}")
                components.add(artifact)
            if components != set(artifacts):
                raise AssertionError(f"Missing components: {set(artifacts) - components}")
            for artifact, packaging in artifacts.items():
                prefix = f"com/hibegin/{artifact}/{VERSION}/{artifact}-{VERSION}"
                required = [f"{prefix}.pom"]
                if packaging == "jar":
                    required.extend(f"{prefix}{suffix}.jar" for suffix in ("", "-sources", "-javadoc"))
                for name in required:
                    for suffix in ("", ".md5", ".sha1"):
                        if name + suffix not in files:
                            raise AssertionError(f"Missing release attachment: {name + suffix}")
                if packaging == "jar":
                    with zipfile.ZipFile(io.BytesIO(archive.read(f"{prefix}-javadoc.jar"))) as javadoc:
                        if "index.html" not in javadoc.namelist():
                            raise AssertionError(f"Empty Javadoc attachment: {artifact}")
        print(f"PASS: {len(artifacts)} components contain POMs, module JARs, sources, Javadocs and checksums")
        print("PASS: no local repository metadata; skipUpload kept the bundle off Central")


if __name__ == "__main__":
    main()
