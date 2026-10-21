#!/usr/bin/env python3
"""Validate the actual distribution contents, not just reactor classes."""
from pathlib import Path
import argparse
import xml.etree.ElementTree as ET
from zipfile import ZipFile

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--release', action='store_true', help='Also check attached source and Javadoc JARs.')
arguments = parser.parse_args()
namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
project = ET.parse(root / 'pom.xml').getroot()
version = project.find('m:version', namespace).text
group = project.find('m:groupId', namespace).text
modules = ['precognition-core', 'precognition-spring-webmvc',
           'precognition-spring-boot3-autoconfigure', 'precognition-spring-boot3-starter',
           'precognition-spring-boot4-autoconfigure', 'precognition-spring-boot4-starter']
for module in modules:
    artifact = root / module / 'target' / f'{module}-{version}.jar'
    with ZipFile(artifact) as archive:
        names = archive.namelist()
        descriptor = f'META-INF/maven/{group}/{module}/pom.properties'
        properties = dict(line.split('=', 1) for line in archive.read(descriptor).decode().splitlines()
                          if line and not line.startswith('#') and '=' in line)
        assert properties['groupId'] == group, artifact
        assert properties['artifactId'] == module, artifact
        assert properties['version'] == version, artifact
        assert archive.read('META-INF/LICENSE') == (root / 'LICENSE').read_bytes(), artifact
        assert not any(name.startswith(('dev/precognition/example/', 'dev/precognition/prototype/',
                                        'dev/precognition/verification/', 'org/junit/')) for name in names), artifact
        if module.endswith('starter'):
            assert not any(name.endswith('.class') for name in names), artifact
        if module.endswith('autoconfigure'):
            platform = 'boot3' if 'boot3' in module else 'boot4'
            location = 'META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports'
            expected = f'dev.precognition.spring.{platform}.PrecognitionAutoConfiguration'
            assert archive.read(location).decode().strip() == expected, artifact
            assert 'META-INF/spring-configuration-metadata.json' in names, artifact
    print(f'OK {artifact.relative_to(root)}')
    if arguments.release:
        for classifier in ('sources', 'javadoc'):
            attached = artifact.with_name(f'{module}-{version}-{classifier}.jar')
            with ZipFile(attached) as archive:
                names = archive.namelist()
                assert not any(name.endswith('.class') for name in names), attached
                assert not any(name.startswith(('dev/precognition/example/', 'dev/precognition/verification/'))
                               for name in names), attached
                if classifier == 'sources':
                    assert archive.read('META-INF/LICENSE') == (root / 'LICENSE').read_bytes(), attached
                    for source in (root / module / 'src/main/java').rglob('*.java'):
                        assert source.relative_to(root / module / 'src/main/java').as_posix() in names, attached
                elif module.endswith('starter'):
                    assert archive.read('README.md').strip(), attached
                else:
                    assert any(name.startswith('dev/precognition/spring/') and name.endswith('.html')
                               for name in names), attached
            print(f'OK {attached.relative_to(root)}')
