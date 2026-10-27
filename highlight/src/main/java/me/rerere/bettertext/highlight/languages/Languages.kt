package me.rerere.bettertext.highlight.languages

import me.rerere.bettertext.highlight.core.Language
import me.rerere.bettertext.highlight.languages.bash.bash
import me.rerere.bettertext.highlight.languages.c.c
import me.rerere.bettertext.highlight.languages.cmake.cmake
import me.rerere.bettertext.highlight.languages.cpp.cpp
import me.rerere.bettertext.highlight.languages.csharp.csharp
import me.rerere.bettertext.highlight.languages.css.css
import me.rerere.bettertext.highlight.languages.dart.dart
import me.rerere.bettertext.highlight.languages.diff.diff
import me.rerere.bettertext.highlight.languages.dockerfile.dockerfile
import me.rerere.bettertext.highlight.languages.go.go
import me.rerere.bettertext.highlight.languages.glsl.glsl
import me.rerere.bettertext.highlight.languages.ini.ini
import me.rerere.bettertext.highlight.languages.java.java
import me.rerere.bettertext.highlight.languages.javascript.javascript
import me.rerere.bettertext.highlight.languages.json.json
import me.rerere.bettertext.highlight.languages.kotlin.kotlin
import me.rerere.bettertext.highlight.languages.latex.latex
import me.rerere.bettertext.highlight.languages.lua.lua
import me.rerere.bettertext.highlight.languages.markdown.markdown
import me.rerere.bettertext.highlight.languages.php.php
import me.rerere.bettertext.highlight.languages.powershell.powershell
import me.rerere.bettertext.highlight.languages.properties.properties
import me.rerere.bettertext.highlight.languages.python.python
import me.rerere.bettertext.highlight.languages.rust.rust
import me.rerere.bettertext.highlight.languages.ruby.ruby
import me.rerere.bettertext.highlight.languages.sql.sql
import me.rerere.bettertext.highlight.languages.swift.swift
import me.rerere.bettertext.highlight.languages.typescript.typescript
import me.rerere.bettertext.highlight.languages.xml.xml
import me.rerere.bettertext.highlight.languages.yaml.yaml

/**
 * Every grammar bundled with the highlighter.
 *
 * Each entry builds a fresh mode tree: compilation mutates modes in place, mirroring `highlight.js`.
 */
internal fun builtinLanguages(): List<Language> = listOf(
    json(),
    ini(),
    cmake(),
    go(),
    glsl(),
    yaml(),
    bash(),
    dockerfile(),
    javascript(),
    typescript(),
    xml(),
    css(),
    dart(),
    java(),
    kotlin(),
    latex(),
    lua(),
    powershell(),
    properties(),
    python(),
    c(),
    cpp(),
    csharp(),
    sql(),
    diff(),
    markdown(),
    rust(),
    ruby(),
    php(),
    swift(),
)
