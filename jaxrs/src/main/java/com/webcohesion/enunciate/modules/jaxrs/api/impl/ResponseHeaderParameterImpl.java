package com.webcohesion.enunciate.modules.jaxrs.api.impl;

import com.webcohesion.enunciate.api.resources.Parameter;
import com.webcohesion.enunciate.javac.javadoc.DefaultJavaDocTagHandler;
import com.webcohesion.enunciate.javac.javadoc.JavaDoc;

import javax.lang.model.element.AnnotationMirror;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Ryan Heaton
 */
public class ResponseHeaderParameterImpl implements Parameter {

  private final String header;
  private final String description;

  public ResponseHeaderParameterImpl(String header, String description) {
    this.header = header;
    this.description = description;
  }

  @Override
  public String getName() {
    return this.header;
  }

  @Override
  public String getDescription() {
    return this.description;
  }

  @Override
  public String getTypeLabel() {
    return "header";
  }

  @Override
  public String getDefaultValue() {
    return null;
  }

  @Override
  public String getConstraints() {
    return null;
  }

  @Override
  public Set<String> getConstraintValues() {
    return null;
  }

  @Override
  public Map<String, AnnotationMirror> getAnnotations() {
    return Collections.emptyMap();
  }

  @Override
  public JavaDoc getJavaDoc() {
    return new JavaDoc(null, new DefaultJavaDocTagHandler());
  }

  @Override
  public boolean isMultivalued() {
    return false;
  }
}
