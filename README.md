# Bazillion
[![Build Status](https://travis-ci.org/SerCeMan/bazillion.svg?branch=master)](https://travis-ci.org/SerCeMan/bazillion)

Bazillion is an opinionated alternative Bazel plugin for IntelliJ IDEA.

## Why

To allow for opening of any kind of project, the [official bazel plugin](https://github.com/bazelbuild/intellij) needs to call bazel to get the required information which means that the information is always correct, but unfortunately these calls are often costly on large projects. 

Bazillion chooses the other side of the tradeoff and builds the IntelliJ project structure by parsing the BUILD files directly. Bazillion also provides a first-class support for [external dependencies](https://github.com/bazelbuild/rules_jvm_external). 

## Requirements

* The files in each module should be places according to the [standard directory layout](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html).
* [external dependencies](https://github.com/bazelbuild/rules_jvm_external) should be used as the 3rd party dependency provider.  

## Current status

This plugin is in alpha stage.

## Contribute

Contributions are always welcome!
