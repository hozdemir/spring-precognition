#!/usr/bin/env python3
"""Validate the actual distribution contents, not just reactor classes."""
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile

root = Path(__file__).resolve().parent.parent
namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
version = ET.parse(root / 'pom.xml').getroot().find('m:version', namespace).text
modules = ['precognition-core', 'precognition-spring-webmvc',
           'precognition-spring-boot3-autoconfigure', 'precognition-spring-boot3-starter',
           'precognition-spring-boot4-autoconfigure', 'precognition-spring-boot4-starter']
for module in modules:
    artifact = root / module / 'target' / f'{module}-{version}.jar'
    with ZipFile(artifact) as archive:
        names = archive.namelist()
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
