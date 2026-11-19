package com.crysound.opscenter.integration;

import java.util.List;

/** Generic parameter schema shared by connectors and validation. */
public record ParameterDefinition(String name, String label, String type, boolean required,
                                  List<String> options, Integer maxLength, Long min, Long max) { }
